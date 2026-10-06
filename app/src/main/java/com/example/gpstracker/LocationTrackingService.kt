package com.example.gpstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

class LocationTrackingService : Service(), SensorEventListener {

    companion object {
        // Η "χρυσή" λίστα που δεν χάνεται όσο τρέχει το Service
        val masterPathPoints = mutableListOf<org.osmdroid.util.GeoPoint>()
        var serviceTotalDistance = 0f
        var serviceTotalSteps = 0
        var serviceTotalCalories = 0.0
        var isServicePaused = false
    }

    private lateinit var locationManager: LocationManager
    private var previousLocation: Location? = null
    private var totalDistance: Float = 0f
    private lateinit var sensorManager: SensorManager

    private var gravityGrade: Double = 0.0 // Η κλίση από το επιταχυνσιόμετρο
    private var currentGrade: Double = 0.0
    private val altitudeBuffer = mutableListOf<Triple<Float, Double, Location>>()

    private var currentSteps: Int = 0
    private var lastStepsForFilter: Int = 0
    private var lastFilterTime: Long = System.currentTimeMillis()
    private var lastStepTime: Long = 0

    private var startTime: Long = 0L
    private val statsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var statsRunnable: Runnable
    private var currentSpeedKmH: Float = 0f
    private var initialSteps: Int = -1

    private var smoothedLat = 0.0
    private var smoothedLng = 0.0
    private var hasSmoothedPoint = false

    private var previousSmoothedLocation: Location? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "GPSTracker::WakeLock"
        )
        wakeLock?.acquire()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        lastStepTime = 0L

// --- RUNNABLE: Εκτελείται σταθερά κάθε 1 δευτερόλεπτο ---
        statsRunnable = object : Runnable {

            // Το βάρος διαβάζεται μόνο μία φορά
            private var cachedWeight: Float? = null

            override fun run() {

                if (startTime != 0L && !isServicePaused) {

                    val elapsedTimeSeconds =
                        (System.currentTimeMillis() - startTime) / 1000

                    // 1. Διαβάζουμε το βάρος μόνο την πρώτη φορά
                    if (cachedWeight == null) {

                        val sharedPrefs =
                            getSharedPreferences(
                                "gps_stats",
                                Context.MODE_PRIVATE
                            )

                        cachedWeight =
                            sharedPrefs.getFloat(
                                "user_weight",
                                115.0f
                            )
                    }

                    val weight = cachedWeight!!

                    // 2. Τρέχουσα ταχύτητα
                    val speed = currentSpeedKmH.coerceAtLeast(0.0f)

                    // 3. Υπολογισμός MET
                    //
                    // Για περπάτημα χρησιμοποιούμε τη σχέση
                    // ACSM για οριζόντιο περπάτημα.
                    //
                    // Για τρέξιμο χρησιμοποιούμε τη σχέση
                    // ACSM για οριζόντιο τρέξιμο.
                    //
                    // Το αποτέλεσμα είναι συνεχές και όχι
                    // απότομα διαφορετικό όταν αλλάζει λίγο
                    // η ταχύτητα.

                    val met = when {

                        // Στάση / σχεδόν ακίνητος
                        speed < 1.0f -> {
                            1.3f
                        }

                        // Περπάτημα
                        speed < 8.0f -> {

                            val speedMetersPerMinute =
                                speed * 1000.0f / 60.0f

                            // VO2 = 0.1 × ταχύτητα + 3.5
                            val vo2 =
                                (0.1f * speedMetersPerMinute) + 3.5f

                            // Μετατροπή VO2 σε MET
                            vo2 / 3.5f
                        }

                        // Τρέξιμο
                        else -> {

                            val speedMetersPerMinute =
                                speed * 1000.0f / 60.0f

                            // ACSM running equation:
                            //
                            // VO2 = 0.2 × speed + 0.9 × speed × grade + 3.5
                            //
                            // grade = 0 (επίπεδη διαδρομή)
                            //
                            // Άρα:
                            // VO2 = 0.2 × speed + 3.5

                            val vo2 =
                                (0.2f * speedMetersPerMinute) + 3.5f

                            // Μετατροπή VO2 σε MET
                            vo2 / 3.5f
                        }
                    }

                    // 4. Συνολικές (GROSS) θερμίδες
                    //
                    // kcal/min =
                    // MET × 3.5 × βάρος / 200
                    //
                    // Το MET περιλαμβάνει ήδη την ενέργεια ηρεμίας.
                    // Επομένως ΔΕΝ προσθέτουμε ξεχωριστά BMR θερμίδες.

                    val caloriesPerSecond =
                        ((met * 3.5f * weight) / 200.0f) / 60.0f

                    serviceTotalCalories += caloriesPerSecond

                    // 5. Ενημέρωση Notification
                    updateNotification(
                        totalDistance,
                        elapsedTimeSeconds
                    )
                }

                statsHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isServicePaused) return
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()

            if (initialSteps == -1) {
                initialSteps = totalStepsSinceBoot
            }

            currentSteps = totalStepsSinceBoot - initialSteps
            lastStepTime = System.currentTimeMillis()
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val az = event.values[2]
            val ay = event.values[1]

            val angleRad = Math.atan2(ay.toDouble(), az.toDouble())
            val newGravityGrade = Math.tan(angleRad) * 100
            gravityGrade = (gravityGrade * 0.8) + (newGravityGrade * 0.2)

            if (Math.abs(gravityGrade) > 100) gravityGrade = 100.0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Μηδενισμός δεδομένων για τη νέα διαδρομή
        totalDistance = 0f
        currentGrade = 0.0
        altitudeBuffer.clear()
        previousLocation = null
        lastStepTime = 0L
        currentSteps = 0
        initialSteps = -1
        serviceTotalCalories = 0.0 // <-- Μηδενισμός θερμίδων στην έναρξη

        startTime = System.currentTimeMillis()
        statsHandler.post(statsRunnable)

        startForegroundService()

        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0.5f,
                locationListener
            )
        }

        return START_STICKY
    }

    private fun updateNotification(distanceMeters: Float, timeSeconds: Long) {
        val channelId = "GPS_Tracking_Service_Channel"

        val h = timeSeconds / 3600
        val m = (timeSeconds % 3600) / 60
        val s = timeSeconds % 60
        val timeStr = String.format("%02d:%02d:%02d", h, m, s)
        val distStr = String.format("%.2f km", distanceMeters / 1000f)
        val speedStr = String.format("%.1f km/h", currentSpeedKmH)

        val line1 = "📍 $distStr  |  ⏱️ $timeStr"
        val line2 = "⚡ $speedStr  |  👣 $currentSteps steps"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Καταγραφή Διαδρομής")
            .setContentText("$line1  |  $line2")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line1\n$line2"))
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun startForegroundService() {
        val channelId = "GPS_Tracking_Service_Channel"
        val channelName = "GPS Tracking Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Καταγραφή Διαδρομής")
            .setContentText("📍 0.00 km  |  ⏱️ 00:00:00")
            .setStyle(NotificationCompat.BigTextStyle().bigText("📍 0.00 km  |  ⏱️ 00:00:00\n⚡ 0.0 km/h  |  👣 0 steps"))
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private val locationListener = LocationListener { location ->
        if (isServicePaused) return@LocationListener
        val currentTime = System.currentTimeMillis()

        if (location.accuracy > 35f) return@LocationListener

        var isPointValid = false
        currentSpeedKmH = location.speed * 3.6f

        // --- GPS SMOOTHING ---
        if (!hasSmoothedPoint) {
            smoothedLat = location.latitude
            smoothedLng = location.longitude
            hasSmoothedPoint = true
        } else {
            smoothedLat = smoothedLat * 0.8 + location.latitude * 0.2
            smoothedLng = smoothedLng * 0.8 + location.longitude * 0.2
        }

        val smoothedLocation = Location(location).apply {
            latitude = smoothedLat
            longitude = smoothedLng
        }

        // --- ΥΠΟΛΟΓΙΣΜΟΣ ΑΠΟΣΤΑΣΗΣ ---
        if (previousSmoothedLocation != null) {
            val gpsDistance = previousSmoothedLocation!!.distanceTo(smoothedLocation)

            if (gpsDistance > 50f) return@LocationListener

            val minMove = maxOf(1.5f, location.accuracy * 0.3f)
            val isProbablyStationary = currentSpeedKmH < 0.4f && gpsDistance < 1.5f

            if (gpsDistance >= minMove && !isProbablyStationary) {
                totalDistance += gpsDistance
                isPointValid = true
                previousSmoothedLocation = smoothedLocation
            }
        } else {
            previousSmoothedLocation = smoothedLocation
        }

        // --- ΥΠΟΛΟΓΙΣΜΟΣ ΚΛΙΣΗΣ ---
        altitudeBuffer.add(Triple(totalDistance, location.altitude, location))

        while (altitudeBuffer.isNotEmpty() && (totalDistance - altitudeBuffer.first().first) > 65f) {
            altitudeBuffer.removeAt(0)
        }

        val backPoint = altitudeBuffer.find { (totalDistance - it.first) in 35f..45f }

        val isAccurate =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters < 8f
            } else {
                location.accuracy < 8f
            }

        if (backPoint != null && isAccurate) {
            val horizontalDist = backPoint.third.distanceTo(location)

            if (horizontalDist > 15f) {
                val altDiff = location.altitude - backPoint.second

                if (Math.abs(altDiff) > 1.2) {
                    val calculatedGrade = (altDiff / horizontalDist) * 100
                    currentGrade = (currentGrade * 0.7) + (calculatedGrade * 0.3)
                }

                if (Math.abs(currentGrade) < 0.6) currentGrade = 0.0
                if (currentGrade > 25.0) currentGrade = 25.0
                if (currentGrade < -25.0) currentGrade = -25.0
            }
        }

        // --- ΑΠΟΣΤΟΛΗ ΔΕΔΟΜΕΝΩΝ ΣΤΟ UI ---
        val intent = Intent("LocationUpdate").apply {
            setPackage(packageName)
            putExtra("lat", smoothedLocation.latitude)
            putExtra("lng", smoothedLocation.longitude)
            putExtra("is_valid", isPointValid)
            putExtra("distance", totalDistance)
            putExtra("current_speed", currentSpeedKmH)
            putExtra("accuracy", location.accuracy)
            putExtra("bearing", location.bearing)
            putExtra("calories", serviceTotalCalories) // Στέλνει τις ενημερωμένες θερμίδες στο UI
            putExtra("steps", currentSteps)
        }
        sendBroadcast(intent)

        // --- ΑΝΑΚΤΗΣΗ ΔΕΔΟΜΕΝΩΝ ΣΤΟ SERVICE ---
        if (isPointValid) {
            val point = org.osmdroid.util.GeoPoint(smoothedLocation.latitude, smoothedLocation.longitude)
            masterPathPoints.add(point)
            serviceTotalDistance = totalDistance
            serviceTotalSteps = currentSteps
        }

        if (isPointValid || previousLocation == null) {
            previousLocation = location
        }
        lastFilterTime = currentTime
        lastStepsForFilter = currentSteps
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        statsHandler.removeCallbacks(statsRunnable)
        sensorManager.unregisterListener(this)
        totalDistance = 0f
        previousLocation = null
        wakeLock?.release()
    }
}
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
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import android.hardware.SensorEvent

class LocationTrackingService : Service(), SensorEventListener {

    private lateinit var locationManager: LocationManager
    private var previousLocation: Location? = null
    private var totalDistance: Float = 0f
    private lateinit var sensorManager: SensorManager // ΠΡΟΣΘΗΚΗ

    private var gravityGrade: Double = 0.0 // Η κλίση από το επιταχυνσιόμετρο

    private var currentGrade: Double = 0.0
    private val altitudeBuffer = mutableListOf<Triple<Float, Double, Location>>()

    private var currentSteps: Int = 0 // Η μεταβλητή που έλειπε
    private var lastStepsForFilter: Int = 0
    private var lastFilterTime: Long = System.currentTimeMillis()
    private var lastStepTime: Long = 0 // Για το παράθυρο των 60 δευτερολέπτων

    private var startTime: Long = 0L // Η προσθήκη που λείπει
    private val statsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var statsRunnable: Runnable
    private var currentSpeedKmH: Float = 0f
    private var initialSteps: Int = -1 // Η τιμή του αισθητήρα κατά την εκκίνηση

    private var smoothedLat = 0.0
    private var smoothedLng = 0.0
    private var hasSmoothedPoint = false

    override fun onCreate() {
        super.onCreate()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) // Μειωμένη συχνότητα
        }

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) // Μειωμένη συχνότητα
        }

        // Μηδενισμός lastStepTime στην αρχή
        lastStepTime = 0L

        statsRunnable = object : Runnable {
            override fun run() {
                if (startTime != 0L) {
                    val elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                    updateNotification(totalDistance, elapsedTimeSeconds)
                }
                statsHandler.postDelayed(this, 1000) // Επανάληψη κάθε 1 δευτερόλεπτο
            }
        }
    }

    // ΠΡΟΣΘΗΚΗ: Υλοποίηση της μεθόδου onSensorChanged
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()

            // Αν είναι η πρώτη φορά που παίρνουμε τιμή μετά το "Start"
            if (initialSteps == -1) {
                initialSteps = totalStepsSinceBoot
            }

            // Υπολογίζουμε τη διαφορά: Τωρινά βήματα - Βήματα που είχαμε στο ξεκίνημα
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

    // ΠΡΟΣΘΗΚΗ: Υλοποίηση της μεθόδου onAccuracyChanged (απαιτείται από το interface)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Μηδενισμός δεδομένων κάθε φορά που ξεκινάει η υπηρεσία
        totalDistance = 0f
        currentGrade = 0.0
        altitudeBuffer.clear()
        previousLocation = null
        lastStepTime = 0L
        currentSteps = 0     // Μηδενισμός εμφάνισης
        initialSteps = -1 // Επαναφορά για τη νέα διαδρομή

        // ΚΑΤΑΓΡΑΦΗ ΤΗΣ ΩΡΑΣ ΕΝΑΡΞΗΣ
        startTime = System.currentTimeMillis()
        statsHandler.post(statsRunnable)

        startForegroundService() // Εκκίνηση του Notification

        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1500L, 1.0f, locationListener
            )
        }

        return START_STICKY
    }

    private fun updateNotification(distanceMeters: Float, timeSeconds: Long) {
        val channelId = "GPS_Tracking_Service_Channel"

        // Μορφοποίηση δεδομένων
        val h = timeSeconds / 3600
        val m = (timeSeconds % 3600) / 60
        val s = timeSeconds % 60
        val timeStr = String.format("%02d:%02d:%02d", h, m, s)
        val distStr = String.format("%.2f km", distanceMeters / 1000f)
        val speedStr = String.format("%.1f km/h", currentSpeedKmH)

        // Η πρώτη σειρά (Κύρια στατιστικά)
        val line1 = "📍 $distStr  |  ⏱️ $timeStr"
        // Η δεύτερη σειρά (Επιπλέον στατιστικά)
        val line2 = "⚡ $speedStr  |  👣 $currentSteps steps"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Καταγραφή Διαδρομής")
            // Το ContentText φαίνεται όταν η ειδοποίηση είναι κλειστή
            .setContentText("$line1  |  $line2")
            // Το BigTextStyle επιτρέπει τις δύο σειρές όταν την κατεβάζεις
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

        startForeground(1, notification)
    }
    private val locationListener = LocationListener { location ->

        // --- 1. ΦΙΛΤΡΟ ΑΚΡΙΒΕΙΑΣ ---
        if (location.accuracy > 45f) return@LocationListener

        var isPointValid = false
        currentSpeedKmH = location.speed * 3.6f
        val currentTime = System.currentTimeMillis()

        // --- 2. GPS SMOOTHING (μειώνει το jitter του GPS) ---
        if (!hasSmoothedPoint) {
            smoothedLat = location.latitude
            smoothedLng = location.longitude
            hasSmoothedPoint = true
        } else {
            smoothedLat = smoothedLat * 0.7 + location.latitude * 0.3
            smoothedLng = smoothedLng * 0.7 + location.longitude * 0.3
        }

        val smoothedLocation = Location(location).apply {
            latitude = smoothedLat
            longitude = smoothedLng
        }

        // --- 3. ΥΠΟΛΟΓΙΣΜΟΣ ΑΠΟΣΤΑΣΗΣ ---
        if (previousLocation != null) {

            val gpsDistance = previousLocation!!.distanceTo(smoothedLocation)

            // ΦΙΛΤΡΟ TELEPORT
            if (gpsDistance > 80f) {
                return@LocationListener
            }

            // ελάχιστη κίνηση
            val minMove = maxOf(2.0f, location.accuracy * 0.12f)

            // ανίχνευση ακινησίας
            val isProbablyStationary = currentSpeedKmH < 0.4f && gpsDistance < 3f

            if (gpsDistance >= minMove && !isProbablyStationary) {

                totalDistance += gpsDistance
                isPointValid = true
            }
        }

        // --- 4. ΥΠΟΛΟΓΙΣΜΟΣ ΚΛΙΣΗΣ ---

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

        // --- 5. ΣΩΣΤΟ BEARING (όχι τρεμόπαιγμα όταν είσαι ακίνητος) ---
        val safeBearing =
            if (location.speed > 0.5f) location.bearing else -1f


        // --- ΑΠΟΣΤΟΛΗ ΔΕΔΟΜΕΝΩΝ ΣΤΟ UI ---
        val intent = Intent("LocationUpdate").apply {
            setPackage(packageName)
            putExtra("lat", location.latitude)
            putExtra("lng", location.longitude)
            putExtra("is_valid", isPointValid)
            putExtra("distance", totalDistance)
            putExtra("current_speed", currentSpeedKmH)
            putExtra("accuracy", location.accuracy)
            putExtra("bearing", location.bearing)
            putExtra("grade", currentGrade)
            putExtra("device_pitch", gravityGrade)
        }
        sendBroadcast(intent)

        // --- ΕΝΗΜΕΡΩΣΗ ΜΕΤΑΒΛΗΤΩΝ ---
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
        // Μηδενισμός για την επόμενη χρήση
        sensorManager.unregisterListener(this) // Αποδέσμευση αισθητήρα
        totalDistance = 0f
        previousLocation = null
    }
}
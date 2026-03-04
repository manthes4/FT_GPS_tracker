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
            lastStepTime = System.currentTimeMillis()
            currentSteps = event.values[0].toInt() // Αποθήκευση των βημάτων μόνο για reference
            // ΔΕΝ χρησιμοποιούνται πουθενά στον υπολογισμό απόστασης
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

        val h = timeSeconds / 3600
        val m = (timeSeconds % 3600) / 60
        val s = timeSeconds % 60
        val timeStr = String.format("%02d:%02d:%02d", h, m, s)
        val distStr = String.format("%.2f km", distanceMeters / 1000f)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Καταγραφή Διαδρομής")
            .setContentText("Απόσταση: $distStr | Χρόνος: $timeStr")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // .setContentIntent(pendingIntent) <-- ΑΦΑΙΡΕΘΗΚΕ
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
            ).apply {
                description = "Channel for GPS tracking service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        // Δημιουργία ειδοποίησης ΧΩΡΙΣ PendingIntent
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking")
            .setContentText("Απόσταση: 0.00 km | Χρόνος: 00:00:00")
            .setSmallIcon(R.drawable.ic_location)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            // .setContentIntent(pendingIntent) <-- ΑΦΑΙΡΕΘΗΚΕ
            .build()

        startForeground(1, notification)
    }

    private val locationListener = LocationListener { location ->
        // 1. Βασικό φίλτρο ακρίβειας
        if (location.accuracy > 35) return@LocationListener
        var isPointValid = false

        val currentSpeedKmH = location.speed * 3.6f
        val currentTime = System.currentTimeMillis()

        if (previousLocation != null) {
            val gpsDistance = previousLocation!!.distanceTo(location)

            // --- ΦΙΛΤΡΟ ΤΗΛΕΜΕΤΑΦΟΡΑΣ (Outlier Rejection) ---
            // Αν η απόσταση είναι πολύ μεγάλη (>50μ), μάλλον είναι λάθος GPS
            if (gpsDistance > 50f) {
                previousLocation = location
                lastFilterTime = currentTime
                return@LocationListener
            }

            // --- ΑΠΛΟ ΦΙΛΤΡΟ ΜΕ ΒΑΣΗ ΤΗΝ ΑΚΡΙΒΕΙΑ ---
            // Το ελάχιστο όριο κίνησης εξαρτάται από την ακρίβεια
            val minMove = maxOf(3.0f, location.accuracy * 0.3f)

            // --- ΑΝΙΧΝΕΥΣΗ ΑΚΙΝΗΣΙΑΣ (για φανάρια) ---
            // Αν η ταχύτητα είναι σχεδόν μηδέν και η απόσταση μικρή, μην προσθέτεις
            val isProbablyStationary = currentSpeedKmH < 0.5f && gpsDistance < 5f

            if (gpsDistance >= minMove && !isProbablyStationary) {
                totalDistance += gpsDistance
                // --- ΕΝΗΜΕΡΩΣΗ NOTIFICATION ΜΕ ΣΤΑΤΙΣΤΙΚΑ ---
                val elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                updateNotification(totalDistance, elapsedTimeSeconds)
                // Χρησιμοποιούμε ΜΟΝΟ την απόσταση GPS χωρίς καμία ανάμειξη βημάτων
                isPointValid = true
            }
        }

        // --- ΥΠΟΛΟΓΙΣΜΟΣ ΚΛΙΣΗΣ (τον κρατάμε ως έχει) ---
        altitudeBuffer.add(Triple(totalDistance, location.altitude, location))

        while (altitudeBuffer.isNotEmpty() && (totalDistance - altitudeBuffer.first().first) > 65f) {
            altitudeBuffer.removeAt(0)
        }

        val backPoint = altitudeBuffer.find { (totalDistance - it.first) in 35f..45f }

        val isAccurate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
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
        previousLocation = location
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
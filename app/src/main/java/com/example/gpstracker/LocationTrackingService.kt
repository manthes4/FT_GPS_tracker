package com.example.gpstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

    private var lastStepTime = 0L // Αποθηκεύει την ώρα που έγινε το τελευταίο βήμα

     private var currentGrade: Double = 0.0
    private val altitudeBuffer = mutableListOf<Triple<Float, Double, Location>>()

    override fun onCreate() {
        super.onCreate()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // --- ΕΔΩ ΜΠΑΙΝΕΙ Η ΛΟΓΙΚΗ ΑΙΣΘΗΤΗΡΑ ---
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // ΠΡΟΣΘΗΚΗ: Υλοποίηση της μεθόδου onSensorChanged
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            lastStepTime = System.currentTimeMillis()
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val az = event.values[2] // Ο άξονας Z (κάθετα στην οθόνη)
            val ay = event.values[1] // Ο άξονας Y (μήκος του κινητού)

            // Υπολογισμός γωνίας σε μοίρες και μετατροπή σε % κλίση
            // Χρησιμοποιούμε την εφαπτομένη της γωνίας y/z
            val angleRad = Math.atan2(ay.toDouble(), az.toDouble())
            val angleDeg = Math.toDegrees(angleRad)

            // Μετατροπή μοιρών σε ποσοστό (Grade % = tan(angle) * 100)
            val newGravityGrade = Math.tan(angleRad) * 100
            gravityGrade = (gravityGrade * 0.8) + (newGravityGrade * 0.2)

            // Φίλτρο για να μην "παίζει" πολύ η τιμή
            if (Math.abs(gravityGrade) > 100) gravityGrade = 100.0
        }
    }

    // ΠΡΟΣΘΗΚΗ: Υλοποίηση της μεθόδου onAccuracyChanged (απαιτείται από το interface)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Μηδενισμός δεδομένων κάθε φορά που πατάμε "Start" στην Activity
        totalDistance = 0f
        currentGrade = 0.0
        altitudeBuffer.clear()
        previousLocation = null
        lastStepTime = 0L // Μηδενισμός στην αρχή

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
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking")
            .setContentText("Καταγραφή διαδρομής σε εξέλιξη...")
            .setSmallIcon(R.drawable.ic_location)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private val locationListener = LocationListener { location ->
        // 1. Ακρίβεια: Στο βουνό/μονοπάτια το 20 είναι αυστηρό. Το 30 είναι πιο ρεαλιστικό.
        if (location.accuracy > 30) return@LocationListener

        val currentSpeedKmH = location.speed * 3.6f
        val currentTime = System.currentTimeMillis()

        // 2. Βήματα: Αυξάνουμε το παράθυρο στα 40 δευτερόλεπτα.
        // Στην πεζοπορία μπορεί να σταματήσεις να βγάλεις μια φωτό ή να ανέβεις μια απότομη κλίση.
        val isMovingBySteps = (currentTime - lastStepTime) < 60000

        if (previousLocation != null) {
            val distance = previousLocation!!.distanceTo(location)

            // 3. ΤΟ ΦΙΛΤΡΟ ΠΕΖΟΠΟΡΙΑΣ:
            // distance >= 1.0f: Ακόμα και μικρά βήματα μετράνε.
            // currentSpeedKmH > 0.8f: Ακόμα και αν σέρνεσαι σε ανηφόρα (0.8 χλμ/ώρα), κατέγραψε!
            if (distance >= 1.0f && (isMovingBySteps || currentSpeedKmH > 0.8f)) {
                totalDistance += distance
            }
        }

// 2. --- ΒΕΛΤΙΩΜΕΝΟΣ ΥΠΟΛΟΓΙΣΜΟΣ ΚΛΙΣΗΣ (Geometry Logic) ---
        // Αποθηκεύουμε: Συνολική Απόσταση, Υψόμετρο, και το ίδιο το Location αντικείμενο
        altitudeBuffer.add(Triple(totalDistance, location.altitude, location))

        while (altitudeBuffer.isNotEmpty() && (totalDistance - altitudeBuffer.first().first) > 65f) {
            altitudeBuffer.removeAt(0)
        }

        // Ψάχνουμε σημείο 35-45 μέτρα πίσω βάσει του "κοντέρ"
        val backPoint = altitudeBuffer.find { (totalDistance - it.first) in 35f..45f }

        // Έλεγχος ακρίβειας (Vertical Accuracy αν υπάρχει, αλλιώς απλό Accuracy)
        val isAccurate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters < 8f
        } else {
            location.accuracy < 8f
        }

        if (backPoint != null && isAccurate) {
            // Χρησιμοποιούμε την απευθείας απόσταση μεταξύ των δύο σημείων (Γεωμετρική)
            val horizontalDist = backPoint.third.distanceTo(location)

            // Υπολογίζουμε μόνο αν η γεωμετρική απόσταση είναι επαρκής (π.χ. > 15μ)
            if (horizontalDist > 15f) {
                val altDiff = location.altitude - backPoint.second
                val calculatedGrade = (altDiff / horizontalDist) * 100

                // Smoothing για να μην έχουμε απότομες αλλαγές
                currentGrade = (currentGrade * 0.7) + (calculatedGrade * 0.3)

                // Deadzone για την ευθεία
                if (Math.abs(currentGrade) < 0.6) currentGrade = 0.0

                // Όρια
                if (currentGrade > 25.0) currentGrade = 25.0
                if (currentGrade < -25.0) currentGrade = -25.0
            }
        }
        // --------------------------------------------------

        // 3. Αποστολή Intent (Όπως πριν)
        val intent = Intent("LocationUpdate").apply {
            setPackage(packageName)
            putExtra("lat", location.latitude)
            putExtra("lng", location.longitude)
            putExtra("distance", totalDistance)
            putExtra("current_speed", currentSpeedKmH)
            putExtra("accuracy", location.accuracy)
            putExtra("bearing", location.bearing)
            putExtra("grade", currentGrade)        // Κλίση δρόμου (GPS)
            putExtra("device_pitch", gravityGrade) // Κλίση συσκευής (Accelerometer)
        }
        sendBroadcast(intent)

        if (currentSpeedKmH > 1.0f) {
            previousLocation = location
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        // Μηδενισμός για την επόμενη χρήση
        sensorManager.unregisterListener(this) // Αποδέσμευση αισθητήρα
        totalDistance = 0f
        previousLocation = null
    }
}
package com.example.gpstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

class LocationTrackingService : Service() {

    private lateinit var locationManager: LocationManager
    private var previousLocation: Location? = null
    private var totalDistance: Float = 0f

     private var currentGrade: Double = 0.0
    private val altitudeBuffer = mutableListOf<Triple<Float, Double, Location>>()

    override fun onCreate() {
        super.onCreate()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Μηδενισμός δεδομένων κάθε φορά που πατάμε "Start" στην Activity
        totalDistance = 0f
        currentGrade = 0.0
        altitudeBuffer.clear()
        previousLocation = null

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
        if (location.accuracy > 20) return@LocationListener

        val currentSpeedKmH = location.speed * 3.6f

        // 1. ΕΝΗΜΕΡΩΣΗ ΑΠΟΣΤΑΣΗΣ (Πρέπει να γίνει πριν την κλίση)
        if (previousLocation != null) {
            val distance = previousLocation!!.distanceTo(location)
            if (distance >= 2.5f && currentSpeedKmH > 1.0f) {
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
            putExtra("grade", currentGrade)
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
        totalDistance = 0f
        previousLocation = null
    }
}
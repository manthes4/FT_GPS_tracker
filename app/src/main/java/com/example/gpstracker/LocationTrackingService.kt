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

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Αυξάνουμε λίγο το minDistance (π.χ. 1.0f) για να βοηθήσουμε το hardware να φιλτράρει μόνο του
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1500L, 1.0f, locationListener
            )
        }
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
        // 1. Έλεγχος ακρίβειας (όπως το είχες)
        if (location.accuracy > 20) return@LocationListener

        val currentSpeedKmH = location.speed * 3.6f

        if (previousLocation != null) {
            val distance = previousLocation!!.distanceTo(location)

            // 2. ΔΙΠΛΟΣ ΕΛΕΓΧΟΣ:
            // Πρέπει ΚΑΙ η απόσταση να είναι > 2.5m ΚΑΙ η ταχύτητα > 1.0km/h
            if (distance >= 2.5f && currentSpeedKmH > 1.0f) {
                totalDistance += distance
            }
        }

        // 3. Στέλνουμε ΠΑΝΤΑ το Intent για να βλέπουμε την ταχύτητα 0.0 στην οθόνη
        val intent = Intent("LocationUpdate").apply {
            setPackage(packageName)
            putExtra("lat", location.latitude)
            putExtra("lng", location.longitude)
            putExtra("distance", totalDistance)
            putExtra("current_speed", currentSpeedKmH)
            putExtra("accuracy", location.accuracy) // ΕΛΕΓΞΕ ΑΥΤΗ ΤΗ ΓΡΑΜΜΗ
        }
        sendBroadcast(intent)

        // 4. Ενημερώνουμε την προηγούμενη θέση μόνο αν η ταχύτητα ήταν επαρκής,
        // ώστε να μην "σέρνουμε" το σφάλμα του drift.
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
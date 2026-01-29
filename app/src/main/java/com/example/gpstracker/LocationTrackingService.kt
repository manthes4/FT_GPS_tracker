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
    private var route: Polyline? = null
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
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1500L, 0.1f, locationListener
            )
        } else {
            // Handle the case where permissions are not granted
            // Ideally, you should handle permission requests in your main activity
        }
    }

    private fun startForegroundService() {
        val channelId = "GPS_Tracking_Service_Channel"
        val channelName = "GPS Tracking Service"

        // Create the notification channel for Android O and above
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

        // Create the notification using NotificationCompat
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Tracking")
            .setContentText("Tracking your location in the background")
            .setSmallIcon(R.drawable.ic_location) // Ensure this drawable exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        // Start the service in the foreground with the notification
        startForeground(1, notification)
    }

    private val locationListener = LocationListener { location ->
        if (previousLocation != null) {
            val distance = previousLocation!!.distanceTo(location)
            totalDistance += distance
            route?.addPoint(GeoPoint(location.latitude, location.longitude))
        }
        previousLocation = location
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }
}

package com.example.gpstracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Marker
import android.graphics.Paint
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.widget.ImageButton
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.ListView
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay

private var currentSearchMarker: Marker? = null

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var locationManager: LocationManager
    private lateinit var startButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var viewStatsButton: ImageButton
    private lateinit var viewSatellitesButton: ImageButton
    private lateinit var statsDisplay: TextView
    private lateinit var fabLoadKml: FloatingActionButton
    private lateinit var fabSearch: FloatingActionButton
    private lateinit var fabTogglePOI: FloatingActionButton  // New FAB for POI toggle
    private var roadOverlay: Polyline? = null // Μεταβλητή για να διαχειριζόμαστε τη γραμμή

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null

    private var isTracking = false
    private var route: Polyline? = null
    private var borderRoute: Polyline? = null
    private var kmlRoute: Polyline? = null
    private var kmlBorderRoute: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var greenMarker: Marker? = null
    private var kmlGreenMarker: Marker? = null
    private var kmlPurpleMarker: Marker? = null
    private var lastLocation: Location? = null // Variable to store the last location
    private var initialLocationMarker: Marker? = null // Declare the variable
    private var previousLocation: Location? = null
    private var totalDistance = 0f
    private var startTime: Long = 0
    private val handler = Handler()
    private var locationMarker: Marker? = null // Declare locationMarker here
    private lateinit var updateStatsRunnable: Runnable

    private var availableSatellites = 0
    private var connectedSatellites = 0
    private var arePOIsVisible = false  // Flag to control POI visibility

    private val PICK_KML_REQUEST = 1
    private val CHANNEL_ID = "gps_tracker_channel"
    private val poiMarkers = mutableListOf<org.osmdroid.views.overlay.Marker>()

    private lateinit var routePlanner: RoutePlannerHelper
    private var isPlanningEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getPreferences(Context.MODE_PRIVATE))
        Configuration.getInstance().setCacheMapTileCount(23)
        Configuration.getInstance().setCacheMapTileOvershoot(18)
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        startButton = findViewById(R.id.button_start)
        stopButton = findViewById(R.id.button_stop)
        viewStatsButton = findViewById(R.id.button_view_stats)
        viewSatellitesButton = findViewById(R.id.button_view_satellites)
        statsDisplay = findViewById(R.id.stats_display)
        fabLoadKml = findViewById(R.id.fab_load_kml)
        fabSearch = findViewById(R.id.fab_search)
        fabTogglePOI = findViewById(R.id.fab_toggle_poi)  // Initialize new FAB

        map.setMultiTouchControls(true)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.isTilesScaledToDpi = false

        // Enable Rotation Gesture Overlay
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled = true
        map.overlays.add(rotationGestureOverlay)

        // Αρχικοποίηση του Helper
// 1. Αρχικοποίηση του Helper
        routePlanner = RoutePlannerHelper(this, map)

        // 2. Σύνδεση του κουμπιού Clear Map (Σωστά το έβαλες, απλά σιγουρέψου ότι η συνάρτηση είναι η "σαρωτική")
        val btnClearMap = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.button_clear_map)
        btnClearMap.setOnClickListener {
            clearMapRouting() // Αυτή που σβήνει με Iterator
        }

        // 3. ΕΝΑΣ και μοναδικός Listener για το Long Click
        val mEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false

                if (isPlanningEnabled) {
                    val totalDistance = routePlanner.addPoint(p)
                    showCustomToast("Total distance: ${String.format("%.3f", totalDistance)} km")
                } else {
                    // Αν δεν είναι planning, κάνει την απλή δρομολόγηση που είχες
                    handleLongClick(p)
                }
                return true
            }
        }

        // Προσθήκη του overlay στη θέση 0 (για να πιάνει πάντα τα κλικ)
        map.overlays.add(0, MapEventsOverlay(mEventsReceiver))

// Ένα κουμπί (π.χ. ImageButton) για ενεργοποίηση/απενεργοποίηση του Planning
        val btnPlan = findViewById<FloatingActionButton>(R.id.button_plan_mode)
        btnPlan.setOnClickListener {
            isPlanningEnabled = !isPlanningEnabled
            if (isPlanningEnabled) {
                showCustomToast("Planning Mode: ON (Long press on map)")
                btnPlan.setColorFilter(Color.GREEN)
            } else {
                showCustomToast("Planning Mode: OFF")
                btnPlan.setColorFilter(null)
            }
        }

        val btnUndo = findViewById<FloatingActionButton>(R.id.button_undo_plan)
        btnUndo.setOnClickListener {
            if (isPlanningEnabled) {
                val newDistance = routePlanner.undoLastPoint()
                showCustomToast(
                    "Τελευταίο σημείο αφαιρέθηκε. Νέα απόσταση: ${
                        String.format(
                            "%.3f",
                            newDistance
                        )
                    } km"
                )
            } else {
                showCustomToast("Ενεργοποιήστε το Planning Mode πρώτα")
            }
        }

// Κουμπί Clear για σβήσιμο της σχεδίασης
// Κουμπί Clear για σβήσιμο της σχεδίασης ΚΑΙ απενεργοποίηση του Mode
        val btnClearPlan =
            findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.button_clear_plan)
        btnClearPlan.setOnClickListener {
            // 1. Καθαρισμός γραμμών και markers από τον χάρτη
            routePlanner.clearAll()

            // 2. Απενεργοποίηση του Planning Mode
            isPlanningEnabled = false

            // 3. Επαναφορά του χρώματος στο κουμπί btnPlan (για να μη φαίνεται πράσινο)
            val btnPlan =
                findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
                    R.id.button_plan_mode
                )
            btnPlan.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)

            showCustomToast("Planning cleared & Mode OFF")
        }

        // Δημιουργία του MyLocationNewOverlay
        locationOverlay = MyLocationNewOverlay(map).apply {
            enableMyLocation()
            disableMyLocation()
        }
        map.overlays.add(locationOverlay)

        val compassOverlay = CompassOverlay(this, map).apply {
            enableCompass()
        }
        map.overlays.add(compassOverlay)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        checkPermissionsAndZoomToLocation()

        startButton.setOnClickListener {
            checkPermissionsAndStartTracking()
        }

        stopButton.setOnClickListener {
            stopTracking()
        }

        viewStatsButton.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
        }

        viewSatellitesButton.setOnClickListener {
            showCustomToast("Διαθέσιμοι δορυφόροι: $availableSatellites, Συνδεδεμένοι δορυφόροι: $connectedSatellites")
        }

        fabLoadKml.setOnClickListener {
            openFileChooser()
        }

        fabSearch.setOnClickListener {
            showSearchDialog()
        }

        // Initially hide POIs
        arePOIsVisible = false
        poiMarkers.forEach { map.overlays.remove(it) }

        fabTogglePOI.setOnClickListener {
            arePOIsVisible = !arePOIsVisible
            togglePOIsVisibility()
            // Optional: Show a toast to indicate state change
            showCustomToast(if (arePOIsVisible) "POIs shown" else "POIs hidden")
        }

        registerGnssCallback()
        createNotificationChannel()

        // Fetch POIs when the map is ready
        map.addOnFirstLayoutListener { _, _, _, _, _ ->
            fetchAndDisplayPOIs()
        }

        if (savedInstanceState != null) {
            isTracking = savedInstanceState.getBoolean("isTracking", false)
            totalDistance = savedInstanceState.getFloat("totalDistance", 0f)
            startTime = savedInstanceState.getLong("startTime", 0)
            if (isTracking) {
                startTracking()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isTracking", isTracking)
        outState.putFloat("totalDistance", totalDistance)
        outState.putLong("startTime", startTime)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GPS Tracker"
            val descriptionText = "Notifications for GPS tracking"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun togglePOIsVisibility() {
        if (arePOIsVisible) {
            fetchAndDisplayPOIs() // Fetch and display POIs for the current map center
        } else {
            poiMarkers.forEach { map.overlays.remove(it) }
            poiMarkers.clear() // Clear the list when hiding POIs
            map.invalidate()
        }
    }

    private fun fetchAndDisplayPOIs() {
        // Clear existing POIs
        poiMarkers.forEach { map.overlays.remove(it) }
        poiMarkers.clear()

        if (!arePOIsVisible) return // Exit if POIs shouldn’t be visible

        val mapCenter = map.mapCenter
        val south = mapCenter.latitude - 0.1
        val west = mapCenter.longitude - 0.1
        val north = mapCenter.latitude + 0.1
        val east = mapCenter.longitude + 0.1

        val overpassUrl =
            URL("https://overpass-api.de/api/interpreter?data=[out:json];node[amenity]($south,$west,$north,$east);out center;")

        val client = OkHttpClient()
        val request = Request.Builder().url(overpassUrl).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Overpass API", "Failed to fetch POIs", e)
                runOnUiThread { showCustomToast("Failed to fetch POIs") }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val jsonData = response.body?.string()
                if (jsonData != null) {
                    try {
                        val jsonObject = JSONObject(jsonData)
                        val elements = jsonObject.getJSONArray("elements")

                        for (i in 0 until elements.length()) {
                            val element = elements.getJSONObject(i)
                            val lat = element.getDouble("lat")
                            val lon = element.getDouble("lon")
                            val tags = element.getJSONObject("tags")
                            val amenity = tags.getString("amenity")
                            val name = tags.optString("name", "")

                            // Add POIs directly since we only call this when visible
                            runOnUiThread { addPoiToMap(lat, lon, amenity, name) }
                        }
                    } catch (e: Exception) {
                        Log.e("Overpass API", "Failed to parse POI data", e)
                        runOnUiThread { showCustomToast("Failed to parse POI data") }
                    }
                }
            }
        })
    }

    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        Toast(applicationContext).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 290)
            show()
        }
    }

    private fun checkPermissionsAndZoomToLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            zoomToLastKnownLocation()
        }
    }

    private fun zoomToLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val lastKnownLocation =
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            lastKnownLocation?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                map.controller.setCenter(geoPoint)
                map.controller.setZoom(17.0)

                // Add a marker for the initial location
                initialLocationMarker = Marker(map).apply {
                    position = geoPoint
                    icon = ContextCompat.getDrawable(
                        this@MainActivity,
                        R.drawable.baseline_run_circle_24
                    ) // Replace with your icon drawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Initial Location" // Optional title
                }
                map.overlays.add(initialLocationMarker)
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                4500L,
                4.1f,
                locationListener
            )
        }
    }

    private fun checkPermissionsAndStartTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                1
            )
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        // Αφαίρεση της διαδρομής και του περιγράμματος από το KML αν υπάρχουν
        kmlRoute?.let { map.overlays.remove(it) }
        kmlBorderRoute?.let { map.overlays.remove(it) }
        route?.let { map.overlays.remove(it) }
        borderRoute?.let { map.overlays.remove(it) }

        // Αφαίρεση όλων των markers
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        greenMarker?.let { map.overlays.remove(it) }

        // Αφαίρεση των KML markers
        kmlGreenMarker?.let { map.overlays.remove(it) }
        kmlPurpleMarker?.let { map.overlays.remove(it) }

        // Remove the initial location marker
        initialLocationMarker?.let { map.overlays.remove(it) }
        initialLocationMarker = null // Set to null to prevent further use

        // Reset markers to null after removal
        startMarker = null
        endMarker = null
        greenMarker = null
        kmlGreenMarker = null
        kmlPurpleMarker = null

        isTracking = true
        totalDistance = 0f
        startTime = System.currentTimeMillis()

        // Δημιουργία Polyline για το μαύρο περίγραμμα
        borderRoute = Polyline().apply {
            outlinePaint.apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK // Μαύρο περίγραμμα
                strokeWidth = 15.0f // Πιο παχύ από την κόκκινη γραμμή
                strokeJoin = Paint.Join.ROUND // Στρογγυλεμένες γωνίες
                strokeCap = Paint.Cap.ROUND // Στρογγυλεμένα άκρα
            }
        }

        // Δημιουργία Polyline για την κόκκινη γραμμή
        route = Polyline().apply {
            outlinePaint.apply {
                isAntiAlias = true
                color = android.graphics.Color.RED // Κόκκινη γραμμή
                strokeWidth = 10.0f // Πιο λεπτή γραμμή
                strokeJoin = Paint.Join.ROUND // Στρογγυλεμένες γωνίες
                strokeCap = Paint.Cap.ROUND // Στρογγυλεμένα άκρα
            }
        }

        // Προσθήκη των δύο Polylines στον χάρτη: πρώτα το περίγραμμα, μετά η κόκκινη γραμμή
        map.overlays.add(borderRoute)  // Μαύρο περίγραμμα
        map.overlays.add(route)        // Κόκκινη γραμμή

        val intent = Intent(this, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)

        showCustomToast("Tracking started")
        zoomToLastKnownLocation()

        updateNotification("Tracking started")

        updateStatsRunnable = object : Runnable {
            override fun run() {
                if (isTracking) {
                    val currentLocation =
                        getLastKnownLocation() // Assuming this method retrieves the current location
                    currentLocation?.let {
                        if (lastLocation == null || lastLocation!!.distanceTo(it) > 3.5) { // Check if the distance exceeds 3 meters
                            val geoPoint = GeoPoint(it.latitude, it.longitude)

                            if (route?.actualPoints?.isEmpty() == true) {
                                // Αν προσθέτουμε το πρώτο σημείο, βάζουμε και το πράσινο marker στην αρχή
                                startMarker = Marker(map).apply {
                                    position = geoPoint
                                    icon = ContextCompat.getDrawable(
                                        this@MainActivity,
                                        R.drawable.green_marker
                                    ) // πράσινο marker
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                map.overlays.add(startMarker)
                            }

                            // βελος πλοηγησης
                            if (locationMarker == null) {
                                locationMarker = Marker(map).apply {
                                    position = geoPoint
                                    icon = ContextCompat.getDrawable(
                                        this@MainActivity,
                                        R.drawable.baseline_run_circle_24
                                    ) // Replace with your icon drawable
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                map.overlays.add(locationMarker)
                            } else {
                                locationMarker?.position = geoPoint
                            }

                            route?.addPoint(geoPoint)
                            borderRoute?.addPoint(geoPoint)
                            lastLocation = it
                            map.invalidate()
                        }
                    }

                    // Update distance and time stats
                    val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                    val distanceInKm = totalDistance / 1000
                    val formattedTime = formatTime(elapsedTime)
                    val formattedDistance = String.format("%.3f χιλιόμετρα", distanceInKm)

                    statsDisplay.text = "$formattedDistance\n$formattedTime"
                    handler.postDelayed(this, 1000) // Update every second
                }
            }
        }

        handler.post(updateStatsRunnable)
    }

    private fun getLastKnownLocation(): Location? {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else {
            null
        }
    }

    private fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        locationManager.removeUpdates(locationListener)

        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        val distanceInKm = totalDistance / 1000

        val formattedTime = formatTime(elapsedTime)
        val formattedDistance = String.format("%.3f", distanceInKm)

        saveStats(formattedTime, formattedDistance)
        saveRouteData()

        showCustomToast("Απόσταση: $formattedDistance km, Χρόνος: $formattedTime")

        val intent = Intent(this, LocationTrackingService::class.java)
        stopService(intent)

        handler.removeCallbacks(updateStatsRunnable)
        statsDisplay.text = ""

        updateNotification("Tracking stopped")

        // Προσθήκη μοβ marker στο τέλος
        if (route?.actualPoints?.isNotEmpty() == true) {
            val lastPoint = route?.actualPoints?.last()
            endMarker = Marker(map).apply {
                position = lastPoint
                icon = ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.purple_marker
                ) // μοβ marker
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(endMarker)
        }

        // Clear the KML route overlay
        kmlRoute?.let {
            map.overlays.remove(it)
            kmlRoute = null
        }

// Clear the KML border overlay (περίγραμμα KML διαδρομής)
        kmlBorderRoute?.let {
            map.overlays.remove(it)
            kmlBorderRoute = null
        }

        map.invalidate() // Redraw the map
    }

    private fun addPoiToMap(lat: Double, lon: Double, amenity: String, name: String) {
        // Exclude telephone amenities
        if (amenity == "telephone" || amenity == "fountain") {
            return // Don't add a marker for telephones
        }
        val poiPoint = GeoPoint(lat, lon)
        val poiMarker = org.osmdroid.views.overlay.Marker(map)
        poiMarker.position = poiPoint

        // Customize marker icon based on amenity (example)
        val icon: Drawable? = when (amenity) {
            "restaurant" -> ContextCompat.getDrawable(this, R.drawable.baseline_restaurant_menu_24)
            "school" -> ContextCompat.getDrawable(this, R.drawable.baseline_school_24)
            "pharmacy" -> ContextCompat.getDrawable(this, R.drawable.baseline_local_pharmacy_24)
            "atm", "bank" -> ContextCompat.getDrawable(this, R.drawable.baseline_local_atm_24)
            // ... add more cases for other amenities
            else -> ContextCompat.getDrawable(this, R.drawable.baseline_push_pin_24)
        }
        poiMarker.icon = icon

        poiMarker.setAnchor(
            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
        )
        poiMarker.title = "$name ($amenity)"
        poiMarker.setOnMarkerClickListener { marker, mapView ->
            showCustomToast(marker.title)
            true
        }

        poiMarkers.add(poiMarker)
        map.overlays.add(poiMarker)
        map.invalidate()
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun saveStats(duration: String, distance: String) {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val stats = "$date   $duration    $distance χιλιόμετρα\n"

        editor.putString("stats", (sharedPreferences.getString("stats", "") ?: "") + stats)
        editor.apply()
    }

    private fun saveRouteData() {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val routeData =
            route?.points?.joinToString("\n") { "${it.latitude}, ${it.longitude}" } ?: ""
        editor.putString("route_data", routeData)
        editor.apply()
    }

    private fun registerGnssCallback() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationManager.registerGnssStatusCallback(object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                super.onSatelliteStatusChanged(status)
                availableSatellites = status.satelliteCount

                connectedSatellites = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.getConstellationType(i) == GnssStatus.CONSTELLATION_GPS &&
                        status.usedInFix(i)
                    ) {
                        connectedSatellites++
                    }
                }
            }
        })
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isTracking) {
                val currentGeoPoint = GeoPoint(location.latitude, location.longitude)
                route?.let {  // Safe call with let for route to avoid NPE
                    it.addPoint(currentGeoPoint)
                }

                previousLocation?.let {
                    totalDistance += location.distanceTo(it)
                }
                previousLocation = location

                map.controller.setCenter(currentGeoPoint)
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.google-earth.kml+xml"
        }
        startActivityForResult(intent, PICK_KML_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_KML_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Extract the filename from the URI
                    val filename = getFileName(uri)

                    // Parse the filename to get the distance in kilometers
                    val distance = extractDistanceFromFilename(filename)

                    // Display the distance (e.g., in a toast)
                    showCustomToast("Distance: $distance km")

                    // Parse and load the KML file
                    parseKmlFile(inputStream)
                }
            }
        }
    }

    // Helper function to get the filename from the URI
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) {
                    result = it.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown"
    }

    // Helper function to extract the distance from the filename
    private fun extractDistanceFromFilename(filename: String): String {
        // Regular expression to find the distance, assuming it's in the format 1,425χιλιόμετρα
        val regex = Regex("""(\d+,\d+)χιλιόμετρα""")
        val matchResult = regex.find(filename)
        return matchResult?.groups?.get(1)?.value ?: "Unknown distance"
    }

    private fun parseKmlFile(inputStream: InputStream) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var inPlacemark = false
            var inCoordinates = false
            val pathPoints = mutableListOf<GeoPoint>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("Placemark", true)) {
                            inPlacemark = true
                        } else if (inPlacemark && parser.name.equals("coordinates", true)) {
                            inCoordinates = true
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (inCoordinates) {
                            val coordinates = parser.text.trim()
                            val coordsArray = coordinates.split(" ")

                            for (coord in coordsArray) {
                                val coords = coord.split(",")
                                if (coords.size >= 2) {
                                    val lon = coords[0].toDouble()
                                    val lat = coords[1].toDouble()
                                    pathPoints.add(GeoPoint(lat, lon))
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("coordinates", true)) {
                            inCoordinates = false
                        } else if (parser.name.equals("Placemark", true)) {
                            inPlacemark = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (pathPoints.isNotEmpty()) {
                // Αφαίρεση παλιών διαδρομών από τον χάρτη αν υπάρχουν
                kmlRoute?.let { map.overlays.remove(it) }
                kmlBorderRoute?.let { map.overlays.remove(it) }
                // Remove the initial location marker
                initialLocationMarker?.let { map.overlays.remove(it) }
                initialLocationMarker = null // Set to null to prevent further use
                route?.let { map.overlays.remove(it) }
                borderRoute?.let { map.overlays.remove(it) }

                // Αφαίρεση όλων των markers
                startMarker?.let { map.overlays.remove(it) }
                endMarker?.let { map.overlays.remove(it) }
                greenMarker?.let { map.overlays.remove(it) }

                // Δημιουργία περιγράμματος Polyline για το KML route
                kmlBorderRoute = Polyline().apply {
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = android.graphics.Color.BLACK
                    outlinePaint.strokeWidth = 15.0f
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }

                // Δημιουργία κύριας Polyline για τη διαδρομή KML
                kmlRoute = Polyline().apply {
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = android.graphics.Color.YELLOW
                    outlinePaint.strokeWidth = 10.0f
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }

                // Προσθήκη των σημείων και στις δύο γραμμές
                kmlBorderRoute?.setPoints(pathPoints)
                kmlRoute?.setPoints(pathPoints)

                // Προσθήκη των Polylines στον χάρτη: πρώτα το περίγραμμα, μετά η κύρια διαδρομή
                kmlBorderRoute?.let { map.overlays.add(it) }
                kmlRoute?.let { map.overlays.add(it) }

                // Δημιουργία πράσινου marker στην αρχή
                if (pathPoints.isNotEmpty()) {
                    val startPoint = pathPoints.first()
                    kmlGreenMarker = Marker(map).apply {
                        position = startPoint
                        icon = ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.green_marker
                        ) // πράσινο marker
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(kmlGreenMarker)
                }

// Δημιουργία μοβ marker στο τέλος
                if (pathPoints.isNotEmpty()) {
                    val endPoint = pathPoints.last()
                    kmlPurpleMarker = Marker(map).apply {
                        position = endPoint
                        icon = ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.purple_marker
                        ) // μοβ marker
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(kmlPurpleMarker)
                }


                // Ανανέωση του χάρτη
                map.invalidate()

            } else {
                showCustomToast("No coordinates found in KML file")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            showCustomToast("Failed to parse KML file")
        }
    }

    private fun updateNotification(message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("GPS Tracker")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notificationBuilder.build())
    }

    data class SearchResult(
        val shortName: String,
        val lat: Double,
        val lon: Double,
        val distance: Float // <--- Πρέπει να είναι Float ή Double
    )

    private fun performNominatimSearch(query: String) {
        val myLoc = locationOverlay.myLocation

        // Προσθέτουμε lat/lon στο URL για να βοηθήσουμε το API να "καταλάβει" την περιοχή μας
        val proximity = if (myLoc != null) "&lat=${myLoc.latitude}&lon=${myLoc.longitude}" else ""
        val url =
            "https://nominatim.openstreetmap.org/search?q=$query&format=json&addressdetails=1&limit=20&accept-language=el$proximity"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).header("User-Agent", "GPSTrackerApp").build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { showCustomToast("Σφάλμα σύνδεσης") }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val jsonData = response.body?.string() ?: return
                Log.d("SEARCH_DEBUG", "JSON Received: $jsonData") // LOG 1: Τι φέρνει το API

                try {
                    val jsonArray = JSONArray(jsonData)
                    val resultsList = mutableListOf<SearchResult>()

                    val myLoc = locationOverlay.myLocation
                    val refLat = myLoc?.latitude ?: map.mapCenter.latitude
                    val refLon = myLoc?.longitude ?: map.mapCenter.longitude

                    Log.d(
                        "SEARCH_DEBUG",
                        "Reference Point: Lat $refLat, Lon $refLon"
                    ) // LOG 2: Πού νομίζει ότι είσαι

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val addr = obj.optJSONObject("address") ?: JSONObject()

                        val resLat = obj.getDouble("lat")
                        val resLon = obj.getDouble("lon")

                        // --- ΔΙΟΡΘΩΣΗ ΟΝΟΜΑΤΟΣ ---
// --- ΔΙΟΡΘΩΣΗ ΟΝΟΜΑΤΟΣ (V2) ---
                        val road = addr.optString("road", addr.optString("pedestrian", ""))
// Ψάχνουμε το όνομα του μέρους με σειρά προτεραιότητας
                        val placeName = addr.optString(
                            "village",
                            addr.optString(
                                "town",
                                addr.optString(
                                    "suburb",
                                    addr.optString("city", "")
                                )
                            )
                        )

                        val municipality = addr.optString("municipality", "")

                        val displayTitle = buildString {
                            if (road.isNotEmpty()) {
                                append(road)
                                if (placeName.isNotEmpty()) append(", $placeName")
                            } else if (placeName.isNotEmpty()) {
                                append(placeName)
                                if (municipality.isNotEmpty()) append(", $municipality")
                            } else {
                                // Αν δεν βρούμε τίποτα από τα παραπάνω, παίρνουμε το display_name από το OSM
                                append(obj.optString("display_name", "").split(",")[0])
                            }
                        }

                        // --- ΔΙΟΡΘΩΣΗ ΑΠΟΣΤΑΣΗΣ ---
                        val distResult = FloatArray(1)
                        android.location.Location.distanceBetween(
                            refLat,
                            refLon,
                            resLat,
                            resLon,
                            distResult
                        )
                        val dist = distResult[0]

                        Log.d(
                            "SEARCH_DEBUG",
                            "Result: $displayTitle | Dist: $dist meters | Lat: $resLat, Lon: $resLon"
                        )

                        resultsList.add(SearchResult(displayTitle, resLat, resLon, dist))
                    }

                    resultsList.sortBy { it.distance }

                    runOnUiThread {
                        if (resultsList.isEmpty()) showCustomToast("Δεν βρέθηκαν αποτελέσματα")
                        else showNominatimSelectionDialog(resultsList)
                    }
                } catch (e: Exception) {
                    Log.e("SEARCH_DEBUG", "Error: ${e.message}", e)
                    runOnUiThread { showCustomToast("Σφάλμα στην ανάλυση") }
                }
            }
        })
    }

    private fun showNominatimSelectionDialog(results: List<SearchResult>) {
        // 1. Δημιουργία της λίστας κειμένων με την απόσταση
        val displayNames = results.map { result ->
            if (result.distance != Float.MAX_VALUE) {
                val kms = result.distance / 1000 // Μετατροπή μέτρων σε χιλιόμετρα
                // Εμφάνιση π.χ.: "Ακροπόλεως, Θεσσαλονίκη (2.4 km)"
                "${result.shortName} (${String.format("%.1f", kms)} km)"
            } else {
                result.shortName // Αν δεν υπάρχει στίγμα GPS, δείξε μόνο το όνομα
            }
        }.toTypedArray()

        // 2. Δημιουργία του ListView (όπως το είχες)
        val listView = ListView(this).apply {
            adapter =
                ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, displayNames)
            divider = ColorDrawable(Color.RED)
            dividerHeight =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
                    .toInt()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Επιλέξτε τοποθεσία:")
            .setView(listView)
            .setNegativeButton("Άκυρο", null)
            .create()

        // 3. Click Listener
        listView.setOnItemClickListener { _, _, which, _ ->
            val selected = results[which]

            // 1. Πάρε το κέντρο του χάρτη ΤΩΡΑ (πριν το zoom) ως εναλλακτική
            val currentMapCenter = GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)

            // 2. Το σημείο προορισμού
            val endGeoPoint = GeoPoint(selected.lat, selected.lon)

            // 3. Η αφετηρία: Προσπάθησε για GPS, αλλιώς χρησιμοποίησε το τρέχον κέντρο
            val startGeoPoint = locationOverlay.myLocation ?: currentMapCenter

            Log.d("ROUTING_DEBUG", "Εκκίνηση διαδρομής από: ${startGeoPoint.latitude}, ${startGeoPoint.longitude}")
            Log.d("ROUTING_DEBUG", "Προς προορισμό: ${endGeoPoint.latitude}, ${endGeoPoint.longitude}")

            // 4. Κάλεσε τη διαδρομή
            calculateRoute(startGeoPoint, endGeoPoint)

            // 5. Τώρα κάνε το zoom στον προορισμό
            zoomToLocation(selected.lat, selected.lon, selected.shortName)

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun zoomToLocation(lat: Double, lon: Double, name: String) {
        val geoPoint = GeoPoint(lat, lon)
        map.controller.setCenter(geoPoint)
        map.controller.setZoom(18.0)

        // 1. Αφαίρεση του προηγούμενου marker αν υπάρχει
        currentSearchMarker?.let {
            map.overlays.remove(it)
        }

        // 2. Δημιουργία του νέου marker
        val marker = Marker(map).apply {
            position = geoPoint
            title = name
            // Χρησιμοποίησε ένα εικονίδιο που έχεις, π.χ. το baseline_push_pin_24
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.baseline_gps_fixed_24)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        // 3. Αποθήκευση στη μεταβλητή και προσθήκη στον χάρτη
        currentSearchMarker = marker
        map.overlays.add(marker)

        map.invalidate() // Απαραίτητο για να ανανεωθεί ο χάρτης οπτικά

        showCustomToast("Μετάβαση σε: $name")
    }

    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)

        AlertDialog.Builder(this)
            .setTitle("Αναζήτηση Τοποθεσίας")
            .setView(dialogView)
            .setPositiveButton("Αναζήτηση") { dialog, _ ->
                val searchQuery = searchEditText.text.toString()
                // ΕΔΩ ΚΑΛΕΙΣ ΤΟ ΝΕΟ ΣΥΣΤΗΜΑ
                performNominatimSearch(searchQuery)
                dialog.dismiss()
            }
            .setNegativeButton("Ακύρωση") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun calculateRoute(startPoint: GeoPoint, endPoint: GeoPoint) {
        Thread {
            try {
                val roadManager = OSRMRoadManager(this, packageName)
                val waypoints = arrayListOf(startPoint, endPoint)
                val road = roadManager.getRoad(waypoints)

                Log.d("ROUTING_DEBUG", "Status: ${road.mStatus} | Distance: ${road.mLength} km")

                runOnUiThread {
                    if (road.mStatus == Road.STATUS_OK) {
                        // 1. Καθαρισμός προηγούμενης γραμμής
                        roadOverlay?.let { map.overlays.remove(it) }

                        // 2. Δημιουργία και Στυλ γραμμής
                        roadOverlay = RoadManager.buildRoadOverlay(road)
                        roadOverlay?.outlinePaint?.apply {
                            color = Color.parseColor("#5E31F7")
                            strokeWidth = 15f
                            strokeCap = Paint.Cap.ROUND
                            isAntiAlias = true
                        }

                        // Προσθήκη στην κατάλληλη θέση (πίσω από markers)
                        map.overlays.add(1, roadOverlay)

                        // 3. ΥΠΟΛΟΓΙΣΜΟΣ ΓΙΑ ΠΕΖΟ (5 km/h)
                        val distanceKm = road.mLength
                        // Χρόνος σε λεπτά = (Απόσταση / 5) * 60
                        val walkingMinutes = (distanceKm / 5.0) * 60.0

                        val timeText = if (walkingMinutes >= 60) {
                            val hours = (walkingMinutes / 60).toInt()
                            val mins = (walkingMinutes % 60).toInt()
                            "${hours}ω και ${mins}λ"
                        } else {
                            "${walkingMinutes.toInt()} λεπτά"
                        }

                        // 4. Ενημέρωση UI
                        val info = "Περπάτημα: $timeText\nΑπόσταση: ${String.format("%.2f", distanceKm)} km"
                        showCustomToast(info)

                        // Ενημέρωση Marker αν υπάρχει
                        endMarker?.snippet = info
                        endMarker?.showInfoWindow()

                        // 5. Εστίαση και Ανανέωση
                        map.zoomToBoundingBox(road.mBoundingBox.increaseByScale(1.2f), true)
                        map.invalidate()

                    } else {
                        showCustomToast("Σφάλμα διαδρομής: ${road.mStatus}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ROUTING_DEBUG", "Error: ${e.message}")
                runOnUiThread { showCustomToast("Αποτυχία σύνδεσης στο δίκτυο") }
            }
        }.start()
    }

    private fun handleLongClick(point: GeoPoint) {
        if (startPoint == null || (startPoint != null && endPoint != null)) {
            // Καθαρισμός αν υπήρχε προηγούμενη διαδρομή και ορισμός νέας αφετηρίας
            clearRouting()
            startPoint = point
            startMarker = addMarker(point, "Αφετηρία", R.drawable.edit_location_alt_24px) // Βάλε ένα δικό σου εικονίδιο
            showCustomToast("Ορίστηκε Αφετηρία")
        } else {
            // Ορισμός προορισμού
            endPoint = point
            endMarker = addMarker(point, "Προορισμός", R.drawable.edit_location_alt_24px)

            // Κλήση της routing συνάρτησης που ήδη έχεις
            calculateRoute(startPoint!!, endPoint!!)
        }
    }

    // Βοηθητική συνάρτηση για προσθήκη Marker
    private fun addMarker(point: GeoPoint, title: String, iconRes: Int): Marker {
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        // marker.icon = resources.getDrawable(iconRes, null) // Προαιρετικά αν έχεις εικονίδιο
        map.overlays.add(marker)
        map.invalidate()
        return marker
    }

    private fun clearRouting() {
        startPoint = null
        endPoint = null
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        roadOverlay?.let { map.overlays.remove(it) }
        startMarker = null
        endMarker = null
        map.invalidate()
    }

    private fun clearMapRouting() {
        // 1. Μηδενισμός βασικών μεταβλητών
        startPoint = null
        endPoint = null
        routePlanner?.clearAll()

        // 2. ΚΛΕΙΣΙΜΟ ΤΩΝ INFOWINDOWS (Αυτό εξαφανίζει την καρτέλα)
        startMarker?.closeInfoWindow()
        endMarker?.closeInfoWindow()

        // 3. Δημιουργία λίστας για τα στοιχεία που θα διαγραφούν
        val toRemove = mutableListOf<Overlay>()

        // 4. Εντοπισμός των στοιχείων (Markers και Polylines)
        for (overlay in map.overlays) {
            if (overlay is Marker) {
                overlay.closeInfoWindow() // Σιγουριά: κλείσιμο όλων των παραθύρων
                toRemove.add(overlay)
            } else if (overlay is Polyline) {
                toRemove.add(overlay)
            }
        }

        // 5. Αφαίρεση από τον χάρτη
        map.overlays.removeAll(toRemove)

        // 6. Καθαρισμός αναφορών
        roadOverlay = null
        startMarker = null
        endMarker = null

        // 7. Ανανέωση χάρτη
        map.invalidate()
        showCustomToast("Ο χάρτης καθαρίστηκε")
    }
}
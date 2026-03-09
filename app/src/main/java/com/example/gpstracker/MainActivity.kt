package com.example.gpstracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Marker
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.widget.ImageButton
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.provider.DocumentsContract
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import java.io.File
import java.net.URLEncoder
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.widget.LinearLayout
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

private var currentSearchMarker: Marker? = null

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var currentSpeed: Float = 0f
    private var currentLocationMarker: Marker? = null
    private lateinit var statsContainer: LinearLayout // Δήλωση στην κορυφή
    private var hasZoomedToTracking = false

    // Μια λίστα που θα κρατάει όλα τα σημεία της διαδρομής
    private val pathPoints = mutableListOf<org.osmdroid.util.GeoPoint>()

    private lateinit var mapEventsOverlay: MapEventsOverlay

    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvAccuracy: TextView // Πρόσθεσε αυτό
    private lateinit var tvGrade: TextView

    private lateinit var tvCurrentGrade: TextView // κλιση εδαφους σε συγκεκριμενο σημειο

    private lateinit var fabSearch: ImageButton
    private lateinit var buttonPlanMode: ImageButton
    private lateinit var buttonUndoPlan: ImageButton
    private lateinit var buttonClearMap: ImageButton

    private lateinit var tvSteps: TextView     // Το UI στοιχείο
    private var currentSteps: Int = 0          // Τα βήματα της τρέχουσας διαδρομής
    private var initialSteps: Int = 0          // Η αρχική τιμή του αισθητήρα

    //μετρηση βηματων
    private lateinit var stepCounterManager: StepCounterManager

    private var myLocationOverlay: MyLocationNewOverlay? = null // Ορισμός εδώ!
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var locationManager: LocationManager
    private lateinit var startButton: View
    private lateinit var stopButton: View
    private lateinit var viewStatsButton: View
    private lateinit var viewSatellitesButton: View
    private lateinit var statsDisplay: TextView
    private lateinit var fabLoadKml: View
    private lateinit var fabTogglePOI: View  // New FAB for POI toggle
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
    private var totalDistance: Float = 0f // Αποθηκεύει την απόσταση σε μέτρα
    private var startTime: Long = 0
    private val handler = Handler()
    private var locationMarker: Marker? = null // Declare locationMarker here
    private var lastPlanningPoint: GeoPoint? = null
    private lateinit var updateStatsRunnable: Runnable

    private var availableSatellites = 0
    private var connectedSatellites = 0
    private var arePOIsVisible = false  // Flag to control POI visibility

    private val PICK_KML_REQUEST = 1
    private val CHANNEL_ID = "gps_tracker_channel"
    private val poiMarkers = mutableListOf<org.osmdroid.views.overlay.Marker>()

    private lateinit var routePlanner: RoutePlannerHelper
    private var planningInfoMarker: Marker? = null
    private var isPlanningEnabled = false

    private var globalMarkerIcon: Drawable? = null // Αποθηκεύει το εικονίδιο μόνιμα

    private var lastTimeStr: String = "00:00:00"
    private var lastDistanceStr: String = "0.00 km"
    private var lastSpeedStr: String = "0.0 km/h"
    private var lastSteps: String = "0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 1. CONFIGURATION (ΜΙΑ ΦΟΡΑ ΣΤΗΝ ΑΡΧΗ) ---
        val conf = Configuration.getInstance()
        conf.cacheMapTileCount = 100   // περισσότερα tiles στη μνήμη
        conf.cacheMapTileOvershoot = 30 // πιο επιθετικό preload
        conf.load(this, getPreferences(Context.MODE_PRIVATE))
        conf.userAgentValue = packageName

        // Ρυθμίσεις Μνήμης (Υψηλές τιμές για ομαλότητα)
        conf.cacheMapTileCount = 30
        conf.cacheMapTileOvershoot = 20

        // --- 2. LAYOUT & IDS (ΜΙΑ ΦΟΡΑ) ---
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        startButton = findViewById(R.id.button_start)
        stopButton = findViewById(R.id.button_stop)
        viewStatsButton = findViewById(R.id.button_view_stats)
        viewSatellitesButton = findViewById(R.id.button_view_satellites)
        fabLoadKml = findViewById(R.id.fab_load_kml)
        fabSearch = findViewById(R.id.fab_search)
        fabTogglePOI = findViewById(R.id.fab_toggle_poi)

        tvDistance = findViewById(R.id.tv_distance)
        tvTime = findViewById(R.id.tv_time)
        tvCurrentSpeed = findViewById(R.id.tv_current_speed)
        tvAvgSpeed = findViewById(R.id.tv_avg_speed)
        tvSteps = findViewById(R.id.tv_steps)
        tvAccuracy = findViewById(R.id.tv_accuracy)
        tvGrade = findViewById(R.id.tv_grade)
        tvCurrentGrade = findViewById(R.id.tvCurrentGrade)
        statsContainer = findViewById(R.id.stats_container)

// --- 3. Map basic settings ---
        map.setMultiTouchControls(true)        // pinch + drag
        map.setBuiltInZoomControls(false)      // κλείνουμε τα buttons
        map.isTilesScaledToDpi = false          // smooth scrolling με σωστό scale
        map.tilesScaleFactor = 1.0f
        map.setFlingEnabled(true)              // smooth inertia scroll
        map.setUseDataConnection(true)         // κατέβασμα tiles
        map.setMapOrientation(0f)              // North up
        map.isHorizontalMapRepetitionEnabled = false
        map.isVerticalMapRepetitionEnabled = false

// --- 4. TilesOverlay tweaks για flicker ---
        val tilesOverlay = map.overlayManager.tilesOverlay
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT)
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT)

        // Λύση για το Flickering
        map.getOverlayManager().getTilesOverlay().setLoadingBackgroundColor(Color.TRANSPARENT)
        map.getOverlayManager().getTilesOverlay().setLoadingLineColor(Color.TRANSPARENT)

        // Ορισμός Google Tiles
        val googleHybrid = object : OnlineTileSourceBase(
            "GoogleHybrid", 0, 20, 256, ".png",
            arrayOf("https://mt1.google.com/vt/lyrs=y&")
        ) {
            override fun getTileURLString(pTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pTileIndex)
                val x = MapTileIndex.getX(pTileIndex)
                val y = MapTileIndex.getY(pTileIndex)
                return "${baseUrl}x=$x&y=$y&z=$zoom"
            }
        }
        map.setTileSource(googleHybrid)

        // Rotation
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled = true
        map.overlays.add(rotationGestureOverlay)

        // Αρχικοποίηση του Helper
        routePlanner = RoutePlannerHelper(this, map)

        routePlanner.routeUpdateListener = object : RoutePlannerHelper.OnRouteUpdateListener {
            override fun onDistanceChanged(newDistance: Double, lastPoint: GeoPoint) {
                // Αυτό καλείται κάθε φορά που κάνεις drag ένα σημείο!
                updatePlanningInfoWindow(lastPoint, newDistance)
            }
        }

        // 2. Σύνδεση του κουμπιού Clear Map (Σωστά το έβαλες, απλά σιγουρέψου ότι η συνάρτηση είναι η "σαρωτική")
        val btnClearMap = findViewById<android.widget.ImageButton>(R.id.button_clear_map)
        btnClearMap.setOnClickListener {
            clearMapRouting()
        }

// 3. Ο Listener για το Long Click (Όπως τον έχεις, είναι σωστός!)
        val mEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false

                if (isPlanningEnabled) {
                    val totalDistance = routePlanner.addPoint(p)
                    lastPlanningPoint = p
                    updatePlanningInfoWindow(p, totalDistance) // Ενημέρωση κατά το πρώτο κλικ
                } else {
                    handleLongClick(p)
                }
                return true
            }
        }

        stepCounterManager = StepCounterManager(this) { steps ->
            if (isTracking) { // Μετράμε μόνο αν έχουμε πατήσει Start
                if (initialSteps == 0) {
                    initialSteps = steps
                }
                currentSteps = steps - initialSteps

                // ΑΥΤΟ ΕΙΝΑΙ ΤΟ ΚΛΕΙΔΙ: Ενημέρωση της οθόνης
                runOnUiThread {
                    tvSteps.text = currentSteps.toString()
                }
            }
        }

        // Προσθήκη του overlay στη θέση 0 (για να πιάνει πάντα τα κλικ)
        map.overlays.add(0, MapEventsOverlay(mEventsReceiver))

// Ένα κουμπί (π.χ. ImageButton) για ενεργοποίηση/απενεργοποίηση του Planning
        val btnPlan = findViewById<android.widget.ImageButton>(R.id.button_plan_mode)
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

        val btnUndo = findViewById<android.widget.ImageButton>(R.id.button_undo_plan)
        btnUndo.setOnClickListener {
            if (isPlanningEnabled) {
                routePlanner.undoLastPoint()
                // Σε περίπτωση Undo, επειδή χάνουμε το προηγούμενο σημείο,
                // κλείνουμε το info window για να μην δείχνει λάθος απόσταση
                planningInfoMarker?.closeInfoWindow()
                lastPlanningPoint = null
            }
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

        // Έλεγχος αν η εφαρμογή άνοιξε από εξωτερικό αρχείο (File Manager)
        intent?.data?.let { uri ->
            handleKmlFromUri(uri)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Αν δόθηκαν οι άδειες, ξεκινάμε το tracking
                startTracking()
            } else {
                showCustomToast("Οι άδειες τοποθεσίας και βημάτων είναι απαραίτητες!")
            }
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

    //οταν ανοιγω την εφαρμογη
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
                map.post {
                    map.controller.setCenter(geoPoint)
                    // Δοκίμασε 19.0 για να δεις τη διαφορά με το 17.0 της αρχικής
                    map.controller.animateTo(geoPoint, 18.5, 1000L)
                    map.invalidate()
                }

                // Προσθήκη marker για την αρχική θέση (ανθρωπάκι)
                initialLocationMarker = Marker(map).apply {
                    position = geoPoint
                    icon = ContextCompat.getDrawable(
                        this@MainActivity,
                        R.drawable.baseline_run_circle_24
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Η θέση μου"
                }
                map.overlays.add(initialLocationMarker)
            }

            // ΕΔΩ ΔΕΝ ΒΑΖΟΥΜΕ locationManager.requestLocationUpdates!
            // Η ενημέρωση της θέσης θα γίνεται αυτόματα από το Service
            // μόλις πατήσεις Start GPS μέσω του locationReceiver.
        }
    }

    private fun checkPermissionsAndStartTracking() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

        // Πρόσθεσε την άδεια βημάτων μόνο αν το κινητό είναι Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // ΠΡΟΣΟΧΗ: Αφαιρέσαμε το ACCESS_BACKGROUND_LOCATION από εδώ.
        // Το Android απαγορεύει να το ζητάς μαζί με τα υπόλοιπα.

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
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

        // 1. ΚΑΘΑΡΙΣΜΟΣ ΧΑΡΤΗ ΚΑΙ ΜΝΗΜΗΣ

        // ΑΥΤΟ ΕΙΝΑΙ ΤΟ ΚΡΙΣΙΜΟ: Καθαρίζουμε τις παλιές συντεταγμένες από τα SharedPreferences
        val sharedPrefs = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("route_data").apply()

        // --- ΚΛΕΙΣΙΜΟ ΟΛΩΝ ΤΩΝ INFO WINDOWS ---
        // Κλείνει τα InfoWindows όλων των markers που υπάρχουν στον χάρτη
        for (overlay in map.overlays) {
            if (overlay is Marker) {
                overlay.closeInfoWindow()
            }
        }

        map.overlays.removeAll { it is Marker || it is Polyline || it is FolderOverlay }

        // 1. ΚΑΘΑΡΙΣΜΟΣ ΧΑΡΤΗ (Ο κώδικας που είχες παραμένει ίδιος)
        kmlRoute?.let { map.overlays.remove(it) }
        kmlBorderRoute?.let { map.overlays.remove(it) }
        route?.let { map.overlays.remove(it) }
        borderRoute?.let { map.overlays.remove(it) }
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        greenMarker?.let { map.overlays.remove(it) }
        kmlGreenMarker?.let { map.overlays.remove(it) }
        kmlPurpleMarker?.let { map.overlays.remove(it) }
        initialLocationMarker?.let { map.overlays.remove(it) }
        initialLocationMarker = null

        // Reset markers και variables
        startMarker = null
        endMarker = null
        greenMarker = null
        kmlGreenMarker = null
        kmlPurpleMarker = null
        lastLocation = null // Πολύ σημαντικό για να ξεκινήσει σωστά η νέα μέτρηση
        isTracking = true
        hasZoomedToTracking = false // <--- ΠΡΟΣΘΕΣΕ ΑΥΤΟ ΕΔΩ
        totalDistance = 0f
        currentSpeed = 0f // Μηδένισε και την ταχύτητα για σιγουριά
        startTime = System.currentTimeMillis()
        pathPoints.clear() // Καθαρισμός για τη νέα διαδρομή

// ΚΑΘΑΡΙΣΜΟΣ ΤΟΥ ΖΩΝΤΑΝΟΥ ΒΕΛΟΥΣ
        currentLocationMarker?.let { map.overlays.remove(it) }
        currentLocationMarker = null

        tvGrade.text = "0.0" // μηδενισμος κλισης
        tvGrade.setTextColor(Color.WHITE)
        tvDistance.text = "0.00 km"
        tvDistance.text = "0.00 km"

        // 5. Καθαρισμός UI (Αν θες να μηδενίζονται αμέσως)
        tvTime.text = "00:00:00"
        tvAvgSpeed.text = "0.0"
        // Μην ξεχάσεις τα βήματα!
        tvSteps.text = "0"

        // Μέσα στη startTracking() σου, εκεί που καθαρίζεις τα overlays:
        currentLocationMarker?.let { map.overlays.remove(it) }
        currentLocationMarker = null

        map.invalidate() // Ανανέωση χάρτη για να φύγουν όλα τα παλιά

        // 2. ΔΗΜΙΟΥΡΓΙΑ POLYLINES (Glow Style)
        borderRoute = Polyline().apply {
            // Εξωτερική λάμψη (Glow) - Ημιδιάφανο μπλε
            outlinePaint.color = Color.parseColor("#FAF6F5") // 50% transparency Cyan
            outlinePaint.strokeWidth = 18.0f // Λίγο πιο παχύ για το εφέ λάμψης
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
            // Προσθήκη Blur effect αν θες ακόμα πιο μαλακό αποτέλεσμα (προαιρετικό)
            outlinePaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }

        route = Polyline().apply {
            // Η κεντρική γραμμή - Έντονο Cyan/Λευκό-Μπλε
            outlinePaint.color = Color.parseColor("#E60000")
            outlinePaint.strokeWidth = 9.0f // Πιο λεπτό για να φαίνεται το glow από κάτω
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }

        map.overlays.add(borderRoute)
        map.overlays.add(route)

        //χρειαζεται για τον καθαρισμο του χαρτη
        map.invalidate() // Ανανέωση για να φανεί ο άδειος χάρτης

        // 3. ΕΚΚΙΝΗΣΗ SERVICE
        val intent = Intent(this, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)

        // ΠΡΟΣΘΕΣΕ ΑΥΤΟ ΕΔΩ:
        currentSteps = 0 // Μηδενίζουμε για τη νέα διαδρομή
        stepCounterManager.start()

        showCustomToast("Tracking started")
        //zoomToLastKnownLocation()
        updateNotification("Tracking started")

        // 4. ΕΝΗΜΕΡΩΣΗ UI (ΜΟΝΟ ΓΙΑ ΤΟ ΧΡΟΝΟΜΕΤΡΟ)
        updateStatsRunnable = object : Runnable {
            override fun run() {
                if (isTracking) {
                    val currentTime = System.currentTimeMillis()
                    tvAccuracy.visibility = View.VISIBLE // Εμφάνιση
                    tvCurrentGrade.visibility = View.VISIBLE // <--- ΠΡΟΣΘΕΣΕ ΑΥΤΟ ΕΔΩ
                    // Ορίζουμε το elapsedTime εδώ για να το αναγνωρίζει παρακάτω
                    val elapsedTime = (currentTime - startTime) / 1000

                    // Μετατροπή totalDistance (σε μέτρα) σε χιλιόμετρα
                    val distanceInKm = totalDistance / 1000.0

                    // 1. Υπολογισμός Μέσης Ταχύτητας (Average Speed)
                    val avgSpeed = if (elapsedTime > 0) (distanceInKm / elapsedTime) * 3600 else 0.0

                    // 2. Ενημέρωση των νέων TextViews (Οι 3 στήλες)
                    tvDistance.text = String.format("%.2f km", distanceInKm)
                    tvTime.text = formatTime(elapsedTime)
                    tvCurrentSpeed.text = String.format("%.1f", currentSpeed)
                    tvAvgSpeed.text = String.format("%.1f", avgSpeed)
                    statsContainer.visibility = View.VISIBLE // Εμφάνιση του πάνελ

                    // ΠΡΟΣΟΧΗ: Διαγράψαμε το statsDisplay.text γιατί πλέον
                    // χρησιμοποιούμε τα tvDistance, tvTime κτλ.

                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(updateStatsRunnable)

        // 5. ΕΓΓΡΑΦΗ ΤΟΥ RECEIVER (Με το flag για Android 14)
        val filter = IntentFilter("LocationUpdate")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            locationReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private val locationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
            val lng = intent?.getDoubleExtra("lng", 0.0) ?: 0.0

            currentSpeed = intent?.getFloatExtra("current_speed", 0f) ?: 0f
            val accuracy = intent?.getFloatExtra("accuracy", 0f) ?: 0f
            val roadGrade = intent?.getDoubleExtra("grade", 0.0) ?: 0.0
            val deviceGrade = intent?.getDoubleExtra("device_pitch", 0.0) ?: 0.0
            val bearing = intent?.getFloatExtra("bearing", 0f) ?: 0f
            val newPoint = GeoPoint(lat, lng)
            val isValid = intent?.getBooleanExtra("is_valid", false) ?: false
            // ΜΟΝΟ αν το σημείο είναι έγκυρο (δηλ. κινούμαστε) το αποθηκεύουμε και το σχεδιάζουμε
            if (isValid) {
                // 1. Αποθήκευση στη λίστα για το KML Export
                pathPoints.add(newPoint)

                // 2. Σχεδίαση της γραμμής στον χάρτη
                route?.addPoint(newPoint)
                borderRoute?.addPoint(newPoint)
            }

            // Μέσα στον locationReceiver
            val distanceInMeters = intent?.getFloatExtra("distance", 0f) ?: 0f
            this@MainActivity.totalDistance = distanceInMeters

            if (distanceInMeters < 1000) {
                tvDistance.text = String.format("%.0f m", distanceInMeters)
            } else {
                val distanceInKm = distanceInMeters / 1000f
                tvDistance.text = String.format("%.2f km", distanceInKm)
            }

            // --- ΔΙΟΡΘΩΣΗ ΜΕΣΗΣ ΤΑΧΥΤΗΤΑΣ ---
            val timeElapsedHours = (System.currentTimeMillis() - startTime) / 3600000.0
            if (timeElapsedHours > 0.001) { // Μετά από μερικά δευτερόλεπτα
                val avgSpeedKmH = (distanceInMeters / 1000.0) / timeElapsedHours
                tvAvgSpeed.text = String.format("%.1f", avgSpeedKmH)
            }

            // Ενημέρωση Στιγμιαίας Ταχύτητας (Km/h)
            tvCurrentSpeed.text = String.format("%.1f", currentSpeed)

            // Accuracy UI
            tvAccuracy.text = "Accuracy GPS: ${String.format("%.1f", accuracy)}m"
            tvAccuracy.setTextColor(if (accuracy > 20) Color.RED else Color.parseColor("#006400"))

            // Λογική Zoom & Marker (Όπως τα είχες)
            if (isTracking && !hasZoomedToTracking) {
                map.controller.animateTo(newPoint, 18.5, 800L)
                hasZoomedToTracking = true
            } else {
                map.controller.setCenter(newPoint)
            }
            updateCurrentLocationMarker(newPoint, bearing)

            // Κλίση Συσκευής (Πάνω στον χάρτη)
            tvCurrentGrade.visibility = View.VISIBLE
            tvCurrentGrade.text = "Κλίση σημείου: ${String.format("%.1f", deviceGrade)}%"
            when {
                deviceGrade > 1.5 -> tvCurrentGrade.setTextColor(Color.parseColor("#D32F2F"))
                deviceGrade < -1.5 -> tvCurrentGrade.setTextColor(Color.parseColor("#388E3C"))
                else -> tvCurrentGrade.setTextColor(Color.BLACK)
            }

            // Κλίση Διαδρομής (Κεντρικό UI)
            tvGrade.text =
                if (Math.abs(roadGrade) < 0.5) "0.0" else String.format("%.1f", roadGrade)
            when {
                roadGrade > 1.0 -> tvGrade.setTextColor(Color.parseColor("#FF5252"))
                roadGrade < -1.0 -> tvGrade.setTextColor(Color.parseColor("#64DD17"))
                else -> tvGrade.setTextColor(Color.WHITE)
            }

            if (startMarker == null) {
                startMarker = Marker(map).apply {
                    position = newPoint
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.green_marker)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(startMarker)
            }

            // --- ΠΡΟΣΘΗΚΗ ΓΙΑ ΤΟ KML (Δεν επηρεάζει το UI) ---
            lastDistanceStr = tvDistance.text.toString()
            lastSpeedStr = tvCurrentSpeed.text.toString() + " km/h"
            lastSteps = tvSteps.text.toString()
            lastTimeStr = tvTime.text.toString()
            // -----------------------------------------------
            map.invalidate()
        }
    }

    private fun updateCurrentLocationMarker(point: GeoPoint, bearing: Float) {
        if (currentLocationMarker == null) {
            // --- Δημιουργία Bitmap ΜΙΑ ΦΟΡΑ ---
            val drawable = ContextCompat.getDrawable(this, R.drawable.arrow_vector)!!.mutate()
            drawable.setTint(Color.parseColor("#F55302"))

            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)

            val finalIcon = BitmapDrawable(resources, bitmap)

            currentLocationMarker = Marker(map).apply {
                icon = finalIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                map.overlays.add(this)
            }
        }

        // --- Μόνο Position και Rotation ---
        currentLocationMarker?.let { marker ->
            marker.position = point
            marker.rotation = -bearing // Στην osmdroid το bearing θέλει μείον
        }

        // ΠΡΟΣΟΧΗ: Μην βάζεις map.invalidate() αν δεν είσαι σε Tracking Mode
        // Αν ο χρήστης σκρολάρει, ο χάρτης κάνει invalidate μόνος του
        if (isTracking) {
            map.invalidate()
        }
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

    private fun saveRouteDataLocally() {
        val sharedPrefs = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)

        if (pathPoints.isEmpty()) {
            // Αν δεν υπάρχει διαδρομή, σβήσε το "last_kml_file" για να μην γίνει διπλή εγγραφή παλιάς διαδρομής
            sharedPrefs.edit().remove("last_kml_file").apply()
            return
        }

        val fileDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val fileTime = java.text.SimpleDateFormat("HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val prettyDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())

        val internalFileName = "route_${fileDate}_$fileTime.kml"

        // Χτίζουμε το περιεχόμενο με αλλαγές γραμμής (\n) για να μην είναι όλα μαζί
        // Χρησιμοποιούμε απλά emoji που υποστηρίζονται από το Android InfoWindow
        val statsForKml = "⏱️ $lastTimeStr | 📍 $lastDistanceStr | ⚡ $lastSpeedStr | 👣 $lastSteps βήματα"

        val kmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Διαδρομή $prettyDate</name>
    <Placemark>
      <name>Στατιστικά Διαδρομής</name>
      <description><![CDATA[$statsForKml]]></description>
      <LineString>
        <coordinates>"""

        // Το CDATA βοηθάει να διατηρηθούν τα emoji και οι ειδικοί χαρακτήρες

        val coordinates = pathPoints.joinToString(" ") { "${it.longitude},${it.latitude},0" }
        val kmlFooter = """
        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>"""

        val fullKml = kmlHeader + coordinates + kmlFooter

        try {
            val file = java.io.File(filesDir, internalFileName)
            file.writeText(fullKml, Charsets.UTF_8) // Σημαντικό: UTF-8 για τα emoji

            val sharedPrefs = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("last_kml_file", internalFileName).apply()
        } catch (e: Exception) {
            Log.e("GPS_TRACKER", "Error saving: ${e.message}")
        }
    }

    private fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        tvAccuracy.visibility = View.GONE

        // 1. Τελικοί Υπολογισμοί
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        val distanceInKm = totalDistance / 1000.0
        val formattedTime = formatTime(elapsedTime)
        val formattedDistance = String.format("%.2f", distanceInKm)

        // Υπολογισμός μέσης ταχύτητας
        val finalAvgSpeed = if (elapsedTime > 30) (distanceInKm / (elapsedTime / 3600.0)) else 0.0
        val formattedAvgSpeed = String.format("%.1f", finalAvgSpeed)

        val finalSteps = currentSteps
        stepCounterManager.stop()

        // 2. ΑΠΟΘΗΚΕΥΣΗ (Εδώ γίνονται όλα: KML + Stats)
        saveStats(formattedTime, formattedDistance, formattedAvgSpeed, finalSteps.toString())

        // 3. Καθαρισμός Service & Receiver
        val intent = Intent(this, LocationTrackingService::class.java)
        stopService(intent)
        try {
            unregisterReceiver(locationReceiver)
        } catch (e: Exception) {
            Log.e("STOP_TRACKING", "Receiver already unregistered")
        }
        handler.removeCallbacks(updateStatsRunnable)

        // 4. Καθαρισμός λίστας για την επόμενη διαδρομή
        pathPoints.clear()

        map.invalidate()
        showCustomToast("Τερματισμός tracking")
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

    private fun formatKmlDate(rawDate: String): String {
        return try {
            if (rawDate.length >= 8) {
                val year = rawDate.substring(0, 4)
                val month = rawDate.substring(4, 6)
                val day = rawDate.substring(6, 8)
                "$day/$month/$year"
            } else {
                rawDate
            }
        } catch (e: Exception) {
            rawDate
        }
    }

    private fun saveStats(time: String, distance: String, avgSpeed: String, steps: String) {
        // Α. Δημιουργούμε το κρυφό KML αρχείο και παίρνουμε το όνομά του
        val kmlFileName = saveKmlInternal(time, distance, avgSpeed, steps)

        // ΑΝ το kmlFileName επέστρεψε το παλιό ή "no_path", σταμάτα εδώ!
        if (kmlFileName == "no_path" || kmlFileName == "error_kml") return

        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val stats = sharedPreferences.getString("stats", "") ?: ""

        // Χρησιμοποιούμε displayDate ίδια με αυτή που μπήκε στο KML για συνέπεια
        val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        // Format: Ημερομηνία Χρόνος Απόσταση Ταχύτητα Βήματα Όνομα_Αρχείου
        val newStat = "$displayDate $time $distance $avgSpeed $steps $kmlFileName\n"

        sharedPreferences.edit().putString("stats", stats + newStat).apply()
        Log.d("GPS_TRACKER", "Stats saved with KML: $kmlFileName")
    }

    private fun saveKmlInternal(
        time: String,
        distance: String,
        avgSpeed: String,
        steps: String
    ): String {

        if (pathPoints.isEmpty()) return "no_path"

        // Όνομα αρχείου (παραμένει σε yyyyMMdd_HHmmss για μοναδικότητα)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "route_$timestamp.kml"

        // Ημερομηνία για εμφάνιση στα stats ή στο KML (dd/MM/yyyy)
        val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val coords = pathPoints.joinToString(" ") { "${it.longitude},${it.latitude},0" }

        val kml = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>

<name>Διαδρομή $timestamp</name>

<Placemark>

<ExtendedData>
<Data name="time"><value>$time</value></Data>
<Data name="distance"><value>$distance</value></Data>
<Data name="avg_speed"><value>$avgSpeed</value></Data>
<Data name="steps"><value>$steps</value></Data>
</ExtendedData>

<LineString>
<coordinates>
$coords
</coordinates>
</LineString>

</Placemark>
</Document>
</kml>
""".trimIndent()

        return try {

            val file = File(filesDir, fileName)
            file.writeText(kml)

            fileName

        } catch (e: Exception) {

            Log.e("SAVE_KML", e.message ?: "error")
            "error_kml"

        }
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

    private fun openFileChooser() {
        // 1. Εύρεση όλων των πιθανών φακέλων (SD και Εσωτερική)
        val externalDirs = getExternalFilesDirs(null)
        val allFiles = mutableListOf<File>()

        // Λίστα με τα paths που θέλουμε να ελέγξουμε
        val pathsToSearch = mutableListOf<File>()

        // Προσθήκη Εσωτερικής Μνήμης (Documents)
        pathsToSearch.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FT Gps Tracker"))

        // Προσθήκη SD Κάρτας (αν υπάρχει)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val sdRoot = externalDirs[1].absolutePath.split("/Android")[0]
            pathsToSearch.add(File(File(sdRoot, "Documents"), "FT Gps Tracker"))
        }

        // 2. Συλλογή αρχείων KML από όλες τις τοποθεσίες
        for (path in pathsToSearch) {
            if (path.exists() && path.isDirectory) {
                val found = path.listFiles { file -> file.extension.lowercase() == "kml" }
                if (found != null) {
                    allFiles.addAll(found)
                }
            }
        }

        if (allFiles.isEmpty()) {
            showCustomToast("Δεν βρέθηκαν αρχεία KML")
            return
        }

        // 3. Ταξινόμηση όλων των αρχείων μαζί (Τα πιο πρόσφατα πάνω-πάνω)
        val sortedFiles = allFiles.sortedByDescending { it.lastModified() }

        // 4. Δημιουργία της λίστας δεδομένων
        val displayList = sortedFiles.map { file ->
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

            // Προαιρετικά: Προσθέτουμε ένα εικονίδιο ή κείμενο αν είναι στην SD
            val locationTag = if (file.absolutePath.contains("storage/emulated/0")) " [Internal]" else " [SD]"

            mapOf("name" to (file.name), "date" to "$date$locationTag")
        }

        val adapter = android.widget.SimpleAdapter(
            this,
            displayList,
            android.R.layout.simple_list_item_2,
            arrayOf("name", "date"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )

        // ViewBinder για το padding (όπως το είχες)
        adapter.viewBinder = android.widget.SimpleAdapter.ViewBinder { view, data, _ ->
            if (view.id == android.R.id.text2) {
                val textView = view as TextView
                textView.text = data.toString()
                val paddingInPx = (8 * resources.displayMetrics.density + 0.5f).toInt()
                textView.setPadding(textView.paddingLeft, paddingInPx, textView.paddingRight, textView.paddingBottom)
                true
            } else false
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Επιλέξτε Διαδρομή")
            .setAdapter(adapter) { _, which ->
                val selectedFile = sortedFiles[which]
                loadKmlFromFile(selectedFile)
            }
            .setNegativeButton("Ακύρωση", null)
            .setNeutralButton("Διαγραφή Όλων") { _, _ ->
                // Διάλογος επιβεβαίωσης για τη διαγραφή όλων
                AlertDialog.Builder(this)
                    .setTitle("Προσοχή!")
                    .setMessage("Είστε σίγουροι ότι θέλετε να διαγράψετε ΚΑΙ ΤΑ ${sortedFiles.size} αρχεία; Αυτή η ενέργεια δεν αναιρείται.")
                    .setPositiveButton("Διαγραφή όλων") { _, _ ->
                        var deletedCount = 0
                        for (file in sortedFiles) {
                            if (file.delete()) deletedCount++
                        }
                        showCustomToast("Διαγράφηκαν $deletedCount αρχεία")
                        openFileChooser() // Ανανέωση της λίστας (θα δείξει "Δεν βρέθηκαν αρχεία")
                    }
                    .setNegativeButton("Ακύρωση", null)
                    .show()
            }
            .create()

        dialog.show()

        // Ρύθμιση διαχωριστικής γραμμής
        val metrics = resources.displayMetrics
        val dividerHeight = (2 * metrics.density).toInt()
        val sideMargin = (20 * metrics.density).toInt()
        val redDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
        val insetDivider = android.graphics.drawable.InsetDrawable(redDrawable, sideMargin, 2, sideMargin, 2)

        dialog.listView.divider = insetDivider
        dialog.listView.dividerHeight = dividerHeight

        // Long Click για Διαγραφή
        dialog.listView.setOnItemLongClickListener { _, _, which, _ ->
            val fileToDelete = sortedFiles[which]
            AlertDialog.Builder(this)
                .setTitle("Διαγραφή αρχείου")
                .setMessage("Θέλετε να διαγράψετε το αρχείο:\n${fileToDelete.name}?")
                .setPositiveButton("Διαγραφή") { _, _ ->
                    if (fileToDelete.delete()) {
                        showCustomToast("Το αρχείο διαγράφηκε")
                        dialog.dismiss()
                        openFileChooser()
                    } else {
                        showCustomToast("Αποτυχία διαγραφής")
                    }
                }
                .setNegativeButton("Ακύρωση", null)
                .show()
            true
        }
    }

    private fun formatDate(rawDate: String): String {
        return if (rawDate.length == 8) {
            // Από 20260305 σε 05/03/2026
            "${rawDate.substring(6, 8)}/${rawDate.substring(4, 6)}/${rawDate.substring(0, 4)}"
        } else rawDate
    }

    private fun formatStatsForDisplay(prettyDate: String, kmlDescription: String): String {
        return try {
            // Το kmlDescription από την parseKmlFile είναι πλέον: "⏱️ 00:10:00 | 📍 1.5 | ⚡ 5.0 | 👣 2000"
            val parts = kmlDescription.split("|").map { it.trim() }

            val time = parts.getOrNull(0) ?: "⏱️ --"
            val dist = parts.getOrNull(1) ?: "📍 --"
            val speed = parts.getOrNull(2) ?: "⚡ --"
            val steps = parts.getOrNull(3) ?: "👣 --"

// Σειρά 1: Χρόνος και Απόσταση (με "χλμ")
            // Σειρά 2: Ταχύτητα (με "Avg") και Βήματα (με "Steps")
            "$time &nbsp;&nbsp;&nbsp; $dist χλμ &nbsp;&nbsp;<br/>" +
            "&nbsp;&nbsp;&nbsp; $speed km/h &nbsp;&nbsp;&nbsp; $steps Steps &nbsp;&nbsp;"

        } catch (e: Exception) {
            kmlDescription // fallback αν κάτι πάει στραβά
        }
    }

    private fun loadKmlFromFile(file: File) {
        try {
            val inputStream = file.inputStream()
            val rawFilename = file.nameWithoutExtension
            parseKmlFile(inputStream)
            inputStream.close()

            val kmlDescription = kmlRoute?.snippet ?: kmlGreenMarker?.snippet ?: ""
            val points = kmlRoute?.points

            if (points != null && points.isNotEmpty()) {
                val datePart = rawFilename.removePrefix("route_").split("_")[0]
                val prettyDate = formatKmlDate(datePart)

                kmlGreenMarker?.let { marker ->
                    marker.closeInfoWindow()

                    // 1. Σύνδεση με το custom layout
                    marker.infoWindow = CustomKmlInfoWindow(map)

                    // 2. Δεδομένα
                    val datePart = rawFilename.removePrefix("route_").split("_")[0]
                    val prettyDate = formatKmlDate(datePart)

                    marker.title = "📅 $prettyDate"

                    val rawStats = marker.snippet ?: ""
                    // Προσοχή: Εδώ περνάμε το HTML string
                    marker.snippet = formatStatsForDisplay(prettyDate, rawStats)

                    // ΤΟ ΚΛΕΙΔΙ ΓΙΑ ΤΟ ΥΨΟΣ:
                    // Το (0.5f, 0.0f) το βάζει ακριβώς πάνω από το marker.
                    // Αν θέλουμε να το "πετάξουμε" πιο ψηλά, χρησιμοποιούμε αρνητική τιμή στο y (π.χ. -0.2f)
                    marker.setInfoWindowAnchor(0.5f, -0.4f)

                    // 4. Εμφάνιση
                    marker.showInfoWindow()
                    map.invalidate()
                }

                map.controller.animateTo(points.first())
                map.invalidate()
            }
        } catch (e: Exception) {
            Log.e("LOAD_KML", "Error: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_KML_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Καλούμε την ίδια συνάρτηση που καλεί και η onCreate!
                handleKmlFromUri(uri)
            }
        }
    }

    private fun handleKmlFromUri(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // 1. Παίρνουμε το όνομα αρχείου
                val filename = getFileName(uri)

                // 2. Βγάζουμε την απόσταση
                val distance = extractDistanceFromFilename(filename)
                showCustomToast("Distance: $distance km")

                // 3. Φορτώνουμε τη διαδρομή στον χάρτη
                parseKmlFile(inputStream)
            }
        } catch (e: Exception) {
            showCustomToast("Σφάλμα κατά το άνοιγμα του αρχείου")
            Log.e("KML_IMPORT", "Error: ${e.message}")
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
        // Το Regex αυτό πιάνει:
        // 1) Αριθμούς με κόμμα ή τελεία (\d+[.,]?\d*)
        // 2) Που ακολουθούνται είτε από "χιλιόμετρα" είτε από "km"
        val regex = Regex("""(\d+[.,]?\d*)(?:χιλιόμετρα|km)""")
        val matchResult = regex.find(filename)

        return matchResult?.groups?.get(1)?.value ?: "Unknown"
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

            // Για τα στατιστικά
            var time: String? = null
            var distance: String? = null
            var avgSpeed: String? = null
            var steps: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when {
                            parser.name.equals("Placemark", true) -> inPlacemark = true

                            inPlacemark && parser.name.equals("coordinates", true) -> inCoordinates = true

                            inPlacemark && parser.name.equals("Data", true) -> {
                                val name = parser.getAttributeValue(null, "name")
                                parser.nextTag() // πάμε στο <value>
                                if (parser.name.equals("value", true)) {
                                    val value = parser.nextText()
                                    when (name) {
                                        "time" -> time = value
                                        "distance" -> distance = value
                                        "avg_speed" -> avgSpeed = value
                                        "steps" -> steps = value
                                    }
                                }
                            }
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
                        when {
                            parser.name.equals("coordinates", true) -> inCoordinates = false
                            parser.name.equals("Placemark", true) -> inPlacemark = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (pathPoints.isNotEmpty()) {
                // Αφαίρεση προηγούμενων overlays
                kmlRoute?.let { map.overlays.remove(it) }
                kmlBorderRoute?.let { map.overlays.remove(it) }
                initialLocationMarker?.let { map.overlays.remove(it) }
                initialLocationMarker = null
                route?.let { map.overlays.remove(it) }
                borderRoute?.let { map.overlays.remove(it) }
                startMarker?.let { map.overlays.remove(it) }
                endMarker?.let { map.overlays.remove(it) }
                greenMarker?.let { map.overlays.remove(it) }

                // Δημιουργία Polyline για περίγραμμα
                kmlBorderRoute = Polyline().apply {
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = Color.parseColor("#FAF6F5") // 50% transparency
                    outlinePaint.strokeWidth = 18.0f
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                }

                // Κύρια Polyline
                kmlRoute = Polyline().apply {
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = Color.parseColor("#0456B5")
                    outlinePaint.strokeWidth = 8.0f
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }

                // Προσθήκη σημείων
                kmlBorderRoute?.setPoints(pathPoints)
                kmlRoute?.setPoints(pathPoints)
                kmlBorderRoute?.let { map.overlays.add(it) }
                kmlRoute?.let { map.overlays.add(it) }

                // Πράσινο marker στην αρχή
                if (pathPoints.isNotEmpty()) {
                    val startPoint = pathPoints.first()
                    kmlGreenMarker = Marker(map).apply {
                        position = startPoint
                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.green_marker)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                        // ΜΗΝ βάλεις title, snippet ή showInfoWindow εδώ.
                        // Θα τα βάλουμε στην loadKmlFromFile για να δουλέψει το Custom XML.
                    }
                    map.overlays.add(kmlGreenMarker)

                    // Αποθήκευσε το description (τα στατιστικά) σε μια μεταβλητή
                    // ή στο snippet προσωρινά για να το βρει η loadKmlFromFile
                    val infoText = "⏱️ ${time ?: "-"} | 📍 ${distance ?: "-"} | ⚡ ${avgSpeed ?: "-"} | 👣 ${steps ?: "-"}"
                    kmlGreenMarker?.snippet = infoText
                }

                // Μοβ marker στο τέλος
                if (pathPoints.isNotEmpty()) {
                    val endPoint = pathPoints.last()
                    kmlPurpleMarker = Marker(map).apply {
                        position = endPoint
                        icon = ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.purple_marker
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(kmlPurpleMarker)
                }

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
        val distance: Float,
        val importance: Double = 0.0,
        val placeRank: Int = 30
    )

    private fun performNominatimSearch(query: String) {
        runOnUiThread { clearMapRouting() }
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return

        val searchedNumber = cleanQuery.split(" ").find { it.toIntOrNull() != null }

        // 1. Σημείο αναφοράς για το API (Κέντρο χάρτη)
        val mapCenter = map.mapCenter
        val refLat = mapCenter.latitude
        val refLon = mapCenter.longitude

        // 2. Σημείο αναφοράς για τα ΧΙΛΙΟΜΕΤΡΑ (Το GPS σου)
        // Αν το GPS δεν είναι έτοιμο, χρησιμοποιούμε το κέντρο του χάρτη
        val userLocation = myLocationOverlay?.myLocation
        val distRefLat = userLocation?.latitude ?: refLat
        val distRefLon = userLocation?.longitude ?: refLon

        val urlQuery = cleanQuery.replace(",", " ")

        // Προετοιμασία φίλτρου χωρίς τόνους
        val filterKey = cleanQuery.split(",")[0].split(" ")[0].lowercase()
            .replace("ώ", "ω").replace("έ", "ε").replace("ά", "α")
            .replace("ή", "η").replace("ί", "ι").replace("ό", "ο").replace("ύ", "υ")

        val url = "https://photon.komoot.io/api/" +
                "?q=${URLEncoder.encode(urlQuery, "UTF-8")}" +
                "&limit=50" +
                "&lat=$refLat" +
                "&lon=$refLon" +
                "&location_bias_scale=0.5"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).header("User-Agent", "GPSTrackerApp").build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val jsonData = response.body?.string() ?: return
                try {
                    val jsonObject = JSONObject(jsonData)
                    val features = jsonObject.getJSONArray("features")
                    val resultsList = mutableListOf<SearchResult>()

                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val props = feature.getJSONObject("properties")

                        val road = props.optString("street", "")
                        val name = props.optString("name", "")
                        val houseNumber = props.optString("housenumber", "")

                        val fullTextForFilter = "$road $name".lowercase()
                            .replace("ώ", "ω").replace("έ", "ε").replace("ά", "α")
                            .replace("ή", "η").replace("ί", "ι").replace("ό", "ο").replace("ύ", "υ")

                        if (!fullTextForFilter.contains(filterKey)) continue

                        val geometry = feature.getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        val resLon = coords.getDouble(0)
                        val resLat = coords.getDouble(1)

                        val displayTitle = buildString {
                            if (road.isNotEmpty()) {
                                append(road)
                                if (houseNumber.isNotEmpty()) append(" $houseNumber")
                            } else append(name)

                            val city = props.optString("city", "")
                            val district =
                                props.optString("district", props.optString("suburb", ""))
                            val area = if (district.isNotEmpty()) district else city
                            if (area.isNotEmpty()) append(", $area")
                        }

                        // ΥΠΟΛΟΓΙΣΜΟΣ ΑΠΟΣΤΑΣΗΣ ΑΠΟ ΤΟ GPS (distRefLat/Lon)
                        val distArray = FloatArray(1)
                        android.location.Location.distanceBetween(
                            distRefLat,
                            distRefLon,
                            resLat,
                            resLon,
                            distArray
                        )

                        resultsList.add(
                            SearchResult(
                                displayTitle,
                                resLat,
                                resLon,
                                distArray[0],
                                0.0,
                                30
                            )
                        )
                    }

                    // ΤΑΞΙΝΟΜΗΣΗ ΚΑΙ ΛΗΨΗ 20 ΑΠΟΤΕΛΕΣΜΑΤΩΝ
                    val finalResults = resultsList.sortedBy { it.distance }
                        .distinctBy { it.shortName }
                        .take(30)

                    runOnUiThread {
                        if (finalResults.isEmpty()) {
                            showCustomToast("Δεν βρέθηκε κάτι")
                        } else {
                            showNominatimSelectionDialog(finalResults)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
        })
    }

    private fun showNominatimSelectionDialog(results: List<SearchResult>) {
        val displayNames = results.map {
            val kms = it.distance / 1000
            // Εμφανίζουμε: "Δελφών, Ανάληψη (0.2 km)"
            "${it.shortName} (${String.format("%.1f", kms)} km)"
        }.toTypedArray()

        val listView = ListView(this).apply {
            adapter =
                ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, displayNames)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Επιλέξτε τοποθεσία:")
            .setView(listView)
            .setNegativeButton("Άκυρο", null)
            .create()

        listView.setOnItemClickListener { _, _, which, _ ->
            val selected = results[which]

            // Χρήση πραγματικού GPS για την αφετηρία
            val lastKnown = if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val startPoint = lastKnown?.let { GeoPoint(it.latitude, it.longitude) }
                ?: locationOverlay.myLocation
                ?: (map.mapCenter as GeoPoint)

            zoomToLocation(selected.lat, selected.lon, selected.shortName)
            calculateRoute(startPoint, GeoPoint(selected.lat, selected.lon))
            dialog.dismiss()
        }
        dialog.show()
    }

    // οταν κανω αναζητηση
    private fun zoomToLocation(lat: Double, lon: Double, name: String) {
        val geoPoint = GeoPoint(lat, lon)
// Χρησιμοποιούμε handler για να δώσουμε χρόνο στο MapView να κάνει το animation
        map.post {
            map.controller.setCenter(geoPoint)
            // Δοκίμασε 19.0 για να δεις τη διαφορά με το 17.0 της αρχικής
            map.controller.animateTo(geoPoint, 18.5, 1000L)
            map.invalidate()
        }

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
                roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)
                val road = roadManager.getRoad(arrayListOf(startPoint, endPoint))

                runOnUiThread {
                    if (road.mStatus == Road.STATUS_OK) {
                        roadOverlay?.let { map.overlays.remove(it) }
                        roadOverlay = RoadManager.buildRoadOverlay(road)
                        roadOverlay?.outlinePaint?.apply {
                            color = Color.parseColor("#5E31F7")
                            strokeWidth = 12f
                        }
                        map.overlays.add(1, roadOverlay)

                        // Σύντομο info χωρίς toast
                        val walkingMinutes = (road.mLength / 5.0) * 60.0
                        val timeText =
                            if (walkingMinutes >= 60) "${(walkingMinutes / 60).toInt()}ω ${(walkingMinutes % 60).toInt()}λ" else "${walkingMinutes.toInt()}λ"
                        val info = "🚶 $timeText | ${String.format("%.2f", road.mLength)} km"

                        val activeMarker = currentSearchMarker ?: endMarker
                        activeMarker?.let {
                            it.snippet = info
                            it.showInfoWindow()
                        }
                        map.zoomToBoundingBox(road.mBoundingBox.increaseByScale(1.3f), true)
                        map.invalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e("ROUTING", e.message ?: "")
            }
        }.start()
    }

    private fun handleLongClick(point: GeoPoint) {
        if (startPoint == null || (startPoint != null && endPoint != null)) {
            // Καθαρισμός αν υπήρχε προηγούμενη διαδρομή και ορισμός νέας αφετηρίας
            clearMapRouting()
            startPoint = point
            startMarker = addMarker(
                point,
                "Αφετηρία",
                R.drawable.edit_location_alt_24px
            ) // Βάλε ένα δικό σου εικονίδιο
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

    private fun updatePlanningInfoWindow(point: GeoPoint, totalDistance: Double) {
        if (planningInfoMarker == null) {
            planningInfoMarker = Marker(map)

            // Διάφανο εικονίδιο για να μη φαίνεται το "χεράκι"
            val transparentBitmap =
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            transparentBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            planningInfoMarker?.icon =
                android.graphics.drawable.BitmapDrawable(resources, transparentBitmap)

            planningInfoMarker?.setInfoWindowAnchor(0.5f, -64.5f)
        }

        // --- ΥΠΟΛΟΓΙΣΜΟΣ ΧΡΟΝΟΥ (5 km/h) ---
        val walkingMinutes = (totalDistance / 5.0) * 60.0
        val timeText = if (walkingMinutes >= 60) {
            val hours = (walkingMinutes / 60).toInt()
            val mins = (walkingMinutes % 60).toInt()
            "${hours}ω και ${mins}λ"
        } else {
            "${walkingMinutes.toInt()} λεπτά"
        }

        // --- ΕΝΗΜΕΡΩΣΗ ΚΕΙΜΕΝΟΥ ---
        planningInfoMarker?.position = point
        planningInfoMarker?.title = "Σχεδιασμός Διαδρομής"
        // Το Snippet τώρα μοιάζει με αυτό της απλής δρομολόγησης
        planningInfoMarker?.snippet =
            "Περπάτημα: $timeText\nΑπόσταση: ${String.format("%.2f", totalDistance)} km"

        if (!map.overlays.contains(planningInfoMarker)) {
            map.overlays.add(planningInfoMarker)
        }

        planningInfoMarker?.showInfoWindow()
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
        currentLocationMarker = null // Μηδενίζουμε και το βέλος για να μην έχουμε διπλά είδωλα

        // --- Η ΠΡΟΣΘΗΚΗ ΣΟΥ ΕΔΩ ---
        // 7. Επαναφορά στην αρχική κατάσταση (Ανθρωπάκι + Zoom)
        zoomToLastKnownLocation()

        // 8. Εξαφάνιση του πάνελ πληροφοριών
        statsContainer.visibility = View.GONE

        // 9. Ανανέωση χάρτη
        map.invalidate()
        showCustomToast("Ο χάρτης καθαρίστηκε")
    }
}

class CustomKmlInfoWindow(mapView: MapView) : MarkerInfoWindow(R.layout.custom_info_window, mapView) {
    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val titleView = mView.findViewById<TextView>(R.id.bubble_title)
        val descView = mView.findViewById<TextView>(R.id.bubble_description)

        // Λευκό κείμενο για το Dark Mode style
        titleView?.setTextColor(Color.WHITE)
        descView?.setTextColor(Color.parseColor("#EEEEEE"))

        titleView?.text = android.text.Html.fromHtml(marker.title ?: "", android.text.Html.FROM_HTML_MODE_LEGACY)
        descView?.text = android.text.Html.fromHtml(marker.snippet ?: "", android.text.Html.FROM_HTML_MODE_LEGACY)

        mView.visibility = View.VISIBLE
    }
}
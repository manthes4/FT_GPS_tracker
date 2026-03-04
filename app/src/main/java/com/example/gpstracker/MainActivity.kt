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
            outlinePaint.color = Color.parseColor("#F26C13")
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
        if (pathPoints.isEmpty()) {
            Log.e("GPS_TRACKER", "Δεν υπάρχουν σημεία για αποθήκευση")
            return
        }

        // Δημιουργία ονόματος αρχείου βάσει ημερομηνίας/ώρας (π.χ. route_20240520_1530.kml)
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val internalFileName = "route_$timestamp.kml"

        // Δημιουργία του περιεχομένου KML
        val kmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Διαδρομή $timestamp</name>
    <Placemark>
      <LineString>
        <coordinates>"""

        val coordinates = pathPoints.joinToString(" ") { "${it.longitude},${it.latitude},0" }

        val kmlFooter = """
        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>"""

        val fullKml = kmlHeader + coordinates + kmlFooter

        try {
            // Αποθήκευση στον εσωτερικό φάκελο (data/data/com.example.gpstracker/files)
            val file = java.io.File(filesDir, internalFileName)
            file.writeText(fullKml)

            // Σώζουμε ΜΟΝΟ το όνομα του αρχείου στα SharedPreferences των στατιστικών
            // ώστε η StatsActivity να ξέρει ποιο αρχείο αντιστοιχεί σε αυτή τη διαδρομή
            val sharedPrefs = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("last_kml_file", internalFileName).apply()

            Log.d("GPS_TRACKER", "Το αρχείο σώθηκε κρυφά: $internalFileName")
        } catch (e: Exception) {
            Log.e("GPS_TRACKER", "Σφάλμα κρυφής αποθήκευσης: ${e.message}")
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

    private fun saveStats(time: String, distance: String, avgSpeed: String, steps: String) {
        // Α. Δημιουργούμε το κρυφό KML αρχείο και παίρνουμε το όνομά του
        val kmlFileName = saveKmlInternal()

        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val stats = sharedPreferences.getString("stats", "") ?: ""
        val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())

        // Β. Προσθέτουμε το kmlFileName ως 6ο στοιχείο (διαχωρισμένο με κενό)
        // Format: Ημερομηνία Χρόνος Απόσταση Ταχύτητα Βήματα Όνομα_Αρχείου
        val newStat = "$date $time $distance $avgSpeed $steps $kmlFileName\n"

        sharedPreferences.edit().putString("stats", stats + newStat).apply()
        Log.d("GPS_TRACKER", "Stats saved with KML: $kmlFileName")
    }

    private fun saveKmlInternal(): String {
        if (pathPoints.isEmpty()) return "no_path"

        // Μοναδικό όνομα αρχείου π.χ. route_20240520_153022.kml
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "route_$timestamp.kml"

        // Δημιουργία περιεχομένου KML
        val kmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Διαδρομή $timestamp</name>
    <Placemark>
      <LineString><coordinates>"""

        val coords = pathPoints.joinToString(" ") { "${it.longitude},${it.latitude},0" }

        val kmlFooter = """</coordinates></LineString>
    </Placemark>
  </Document>
</kml>"""

        return try {
            // Αποθήκευση στον εσωτερικό φάκελο της εφαρμογής (FilesDir)
            val file = File(filesDir, fileName)
            file.writeText(kmlHeader + coords + kmlFooter)
            fileName // Επιστρέφουμε το όνομα για να γραφτεί στα stats
        } catch (e: Exception) {
            Log.e("SAVE_KML", "Error: ${e.message}")
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
        val path = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "FT Gps Tracker"
        )

        if (!path.exists() || !path.isDirectory) {
            showCustomToast("Ο φάκελος FT Gps Tracker δεν βρέθηκε")
            return
        }

        val files = path.listFiles { file -> file.extension.lowercase() == "kml" }

        if (files.isNullOrEmpty()) {
            showCustomToast("Δεν βρέθηκαν αρχεία KML")
            return
        }

        // Ταξινόμηση: Τα πιο πρόσφατα αρχεία πάνω-πάνω
        val sortedFiles = files.sortedByDescending { it.lastModified() }

        // Δημιουργία της λίστας δεδομένων (Όνομα και Ημερομηνία)
        val displayList = sortedFiles.map { file ->
            val date = SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                Locale.getDefault()
            ).format(Date(file.lastModified()))
            mapOf("name" to file.name, "date" to date)
        }

        // Χρήση SimpleAdapter για εμφάνιση δύο γραμμών (Title και Subtitle)
// Χρήση SimpleAdapter με παρέμβαση στον κώδικα για το περιθώριο
        val adapter = android.widget.SimpleAdapter(
            this,
            displayList,
            android.R.layout.simple_list_item_2,
            arrayOf("name", "date"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )

        // Παρέμβαση μέσω ViewBinder για προσθήκη περιθωρίου στο text2 (ημερομηνία)
        adapter.viewBinder = android.widget.SimpleAdapter.ViewBinder { view, data, _ ->
            if (view.id == android.R.id.text2) {
                val textView = view as TextView
                textView.text = data.toString()
                // Προσθέτουμε 8dp περιθώριο στο πάνω μέρος (μετατροπή dp σε px)
                val paddingInDp = 8
                val scale = resources.displayMetrics.density
                val paddingInPx = (paddingInDp * scale + 0.5f).toInt()

                // Διατηρούμε τα υπάρχοντα padding και αλλάζουμε μόνο το top
                textView.setPadding(
                    textView.paddingLeft,
                    paddingInPx,
                    textView.paddingRight,
                    textView.paddingBottom
                )
                true
            } else {
                false
            }
        }

        // Δημιουργία του διαλόγου χωρίς να τον εμφανίσουμε αμέσως (.create() αντί για .show())
        val dialog = AlertDialog.Builder(this)
            .setTitle("Επιλέξτε Διαδρομή")
            .setAdapter(adapter) { _, which ->
                val selectedFile = sortedFiles[which]
                loadKmlFromFile(selectedFile)
            }
            .setNegativeButton("Ακύρωση", null)
            .create()

        // Εμφάνιση του διαλόγου
        // Εμφάνιση του διαλόγου
        dialog.show()

        // Προσθήκη κόκκινης διαχωριστικής γραμμής 2dp
        // Ρύθμιση της διαχωριστικής γραμμής
        val metrics = resources.displayMetrics
        val dividerHeight = (2 * metrics.density).toInt() // Πάχος 2dp
        val sideMargin = (20 * metrics.density).toInt()   // Κενό 20dp αριστερά και δεξιά

        // Δημιουργούμε ένα κόκκινο χρώμα
        val redDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)


        // Το τυλίγουμε σε ένα InsetDrawable για να του δώσουμε περιθώρια
        val insetDivider = android.graphics.drawable.InsetDrawable(
            redDrawable,
            sideMargin, 2, sideMargin, 2
        )

        dialog.listView.divider = insetDivider
        dialog.listView.dividerHeight = dividerHeight
        // Τώρα που ο διάλογος εμφανίστηκε, μπορούμε να πιάσουμε τη ListView του
        dialog.listView.setOnItemLongClickListener { _, _, which, _ ->
            // ... ο υπόλοιπος κώδικας για τη διαγραφή παραμένει ίδιος ...
            val fileToDelete = sortedFiles[which]

            // Εμφάνιση επιβεβαίωσης για διαγραφή
            AlertDialog.Builder(this)
                .setTitle("Διαγραφή αρχείου")
                .setMessage("Θέλετε να διαγράψετε το αρχείο:\n${fileToDelete.name}?")
                .setPositiveButton("Διαγραφή") { _, _ ->
                    if (fileToDelete.delete()) {
                        showCustomToast("Το αρχείο διαγράφηκε")
                        dialog.dismiss() // Κλείνουμε τον αρχικό διάλογο
                        openFileChooser() // Τον ξανανοίγουμε για να ανανεωθεί η λίστα
                    } else {
                        showCustomToast("Αποτυχία διαγραφής")
                    }
                }
                .setNegativeButton("Ακύρωση", null)
                .show()

            true // Επιστρέφουμε true για να δείξουμε ότι το κλικ καταναλώθηκε
        }
    }

    private fun getStatsFromFilename(filename: String): String {
        return try {
            val cleanName = filename.replace(".kml", "")
            val parts = cleanName.split("_")

            val date = parts.getOrNull(1) ?: "-"
            val time = parts.getOrNull(2)?.replace("-", ":") ?: "00:00:00"
            val dist = parts.find { it.contains("km") } ?: "0.00km"
            val steps = parts.find { it.contains("steps") }?.replace("steps", "") ?: "0"

            "<b><big>📅 &nbsp;&nbsp;$date &nbsp;&nbsp;&nbsp; 👣 $steps βήματα</big></b><br/>" +
                    "<b><big>📍 &nbsp;&nbsp;$dist &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ⏱️ $time</big></b>"

        } catch (e: Exception) {
            "<b><big>Πληροφορίες διαδρομής</big></b>"
        }
    }

    private fun loadKmlFromFile(file: File) {
        try {
            val inputStream = file.inputStream()
            val filename = file.name

            // 1. Εκτελείται η υπάρχουσα συνάρτηση που σχεδιάζει τα πάντα
            parseKmlFile(inputStream)
            inputStream.close()

            // 2. Παίρνουμε τα σημεία από την Polyline που μόλις δημιούργησε η parseKmlFile
            val points = kmlRoute?.points

            if (points != null && points.isNotEmpty()) {
                val statsSummary = getStatsFromFilename(filename)

                // 3. Δημιουργούμε έναν αόρατο ή διάφανο Marker για να δείξει το InfoWindow
                val infoMarker = Marker(map)
                infoMarker.position = points.first() // Στο πρώτο σημείο

                // Αν θέλεις να φαίνεται πάνω από τον πράσινο marker:
                infoMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Για να μην φαίνεται δεύτερο εικονίδιο, μπορούμε να του βάλουμε
                // ένα διάφανο χρώμα ή να χρησιμοποιήσουμε τον ήδη υπάρχοντα kmlGreenMarker
                kmlGreenMarker?.let {
                    it.title = "Στατιστικά Διαδρομής"
                    it.snippet = statsSummary
                    it.showInfoWindow() // Εμφάνιση των stats
                } ?: run {
                    // Αν για κάποιο λόγο δεν υπάρχει ο kmlGreenMarker
                    infoMarker.title = "Στατιστικά Διαδρομής"
                    infoMarker.snippet = statsSummary
                    map.overlays.add(infoMarker)
                    infoMarker.showInfoWindow()
                }

                // Εστίαση στην αρχή
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
                    outlinePaint.color = android.graphics.Color.WHITE
                    outlinePaint.strokeWidth = 18.0f
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                }

                // Δημιουργία κύριας Polyline για τη διαδρομή KML
                kmlRoute = Polyline().apply {
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = android.graphics.Color.YELLOW
                    outlinePaint.strokeWidth = 8.0f
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
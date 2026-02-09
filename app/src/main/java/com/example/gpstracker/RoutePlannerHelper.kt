package com.example.gpstracker

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RoutePlannerHelper(private val context: Context, private val map: MapView) {

    private var planningPoints = mutableListOf<GeoPoint>()
    private var planningMarkers = mutableListOf<Marker>()

    // Δημιουργία της γραμμής με κόκκινο χρώμα και μεγαλύτερο πάχος
    private var planningPolyline = Polyline().apply {
        outlinePaint.color = Color.RED
        outlinePaint.strokeWidth = 15f // Πιο παχιά γραμμή
        outlinePaint.strokeCap = Paint.Cap.ROUND // Στρογγυλεμένες άκρες
        outlinePaint.isAntiAlias = true
    }

    fun addPoint(point: GeoPoint): Double {
        if (planningPoints.isEmpty()) {
            planningPolyline.setPoints(mutableListOf())
            if (!map.overlays.contains(planningPolyline)) {
                map.overlays.add(planningPolyline)
            }
        }

        planningPoints.add(point)
        planningPolyline.addPoint(point)

        val marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Σημείο ${planningPoints.size}"
            icon = context.getDrawable(R.drawable.baseline_gps_fixed_24)

            // --- ΕΝΕΡΓΟΠΟΙΗΣΗ ΜΕΤΑΚΙΝΗΣΗΣ ---
            isDraggable = true
            infoWindow = null // Κλείνουμε το default για να μην ενοχλεί

            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {}

                override fun onMarkerDrag(marker: Marker) {
                    // Καθώς σέρνεις τον marker, η γραμμή τεντώνεται και ακολουθεί
                    updateLineFromMarkers()
                }

                override fun onMarkerDragEnd(marker: Marker) {
                    // Όταν αφήνεις τον marker, υπολογίζεται η τελική απόσταση
                    updateLineFromMarkers()

                    // ΠΡΟΑΙΡΕΤΙΚΑ: Αν θέλεις να ενημερώνεται η "καρτέλα" στην MainActivity
                    // μπορείς να καλέσεις εδώ μια μέθοδο ενημέρωσης.
                }
            })
        }

        planningMarkers.add(marker)
        map.overlays.add(marker)

        map.invalidate()
        return calculateTotalDistance()
    }

    private fun updateLineFromMarkers() {
        val newPoints = mutableListOf<GeoPoint>()
        for (m in planningMarkers) {
            newPoints.add(m.position)
        }

        // Ενημέρωση της λίστας σημείων και της γραμμής
        planningPoints.clear()
        planningPoints.addAll(newPoints)
        planningPolyline.setPoints(planningPoints)

        map.invalidate() // Ανανέωση χάρτη
    }

    private fun calculateTotalDistance(): Double {
        var total = 0.0
        if (planningPoints.size < 2) return 0.0

        for (i in 0 until planningPoints.size - 1) {
            val start = planningPoints[i]
            val end = planningPoints[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
            total += results[0]
        }
        return total / 1000.0
    }

    fun undoLastPoint(): Double {
        if (planningPoints.isNotEmpty()) {
            // 1. Αφαίρεση του τελευταίου σημείου από τη λίστα
            planningPoints.removeAt(planningPoints.size - 1)

            // 2. Ενημέρωση της γραμμής (την αδειάζουμε και ξαναβάζουμε τα σημεία που έμειναν)
            planningPolyline.setPoints(planningPoints)

            // 3. Αφαίρεση του τελευταίου Marker από τον χάρτη και τη λίστα
            if (planningMarkers.isNotEmpty()) {
                val lastMarker = planningMarkers.removeAt(planningMarkers.size - 1)
                map.overlays.remove(lastMarker)
            }

            map.invalidate()
        }
        return calculateTotalDistance()
    }

    fun clearAll() {
        map.overlays.remove(planningPolyline)
        planningPolyline.setPoints(mutableListOf()) // Πλήρες άδειασμα των σημείων
        for (marker in planningMarkers) {
            map.overlays.remove(marker)
        }
        planningMarkers.clear()
        planningPoints.clear()
        map.invalidate()
    }
}
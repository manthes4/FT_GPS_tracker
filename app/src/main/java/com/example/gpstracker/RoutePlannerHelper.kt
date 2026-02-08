package com.example.gpstracker

import android.content.Context
import android.graphics.Color
import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RoutePlannerHelper(private val context: Context, private val map: MapView) {

    private var planningPoints = mutableListOf<GeoPoint>()
    private var planningMarkers = mutableListOf<Marker>()
    private var planningPolyline = Polyline().apply {
        outlinePaint.color = Color.BLUE
        outlinePaint.strokeWidth = 7f
    }

    // Προσθήκη σημείου και επιστροφή της συνολικής απόστασης σε km
    fun addPoint(point: GeoPoint): Double {
        planningPoints.add(point)

        // Σχεδίαση γραμμής
        if (!map.overlays.contains(planningPolyline)) {
            map.overlays.add(planningPolyline)
        }
        planningPolyline.addPoint(point)

        // Προσθήκη Marker
        val marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Σημείο ${planningPoints.size}"
            icon = context.getDrawable(R.drawable.baseline_push_pin_24) // Βεβαιώσου ότι υπάρχει
        }
        planningMarkers.add(marker)
        map.overlays.add(marker)

        map.invalidate()
        return calculateTotalDistance()
    }

    private fun calculateTotalDistance(): Double {
        var total = 0.0
        for (i in 0 until planningPoints.size - 1) {
            val start = planningPoints[i]
            val end = planningPoints[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
            total += results[0]
        }
        return total / 1000.0 // Επιστροφή σε χιλιόμετρα
    }

    fun clearAll() {
        map.overlays.remove(planningPolyline)
        planningPolyline.points.clear()
        for (marker in planningMarkers) {
            map.overlays.remove(marker)
        }
        planningMarkers.clear()
        planningPoints.clear()
        map.invalidate()
    }

    fun getPointsCount(): Int = planningPoints.size
}
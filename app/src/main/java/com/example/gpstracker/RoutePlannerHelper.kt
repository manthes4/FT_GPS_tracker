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

    interface OnRouteUpdateListener {
        fun onDistanceChanged(newDistance: Double, lastPoint: GeoPoint)
    }

    var routeUpdateListener: OnRouteUpdateListener? = null

    private var planningPoints = mutableListOf<GeoPoint>()
    private var planningMarkers = mutableListOf<Marker>()

    // 1. Εξωτερικό Μαύρο Περίγραμμα (Border)
    private var planningBorderPolyline = Polyline().apply {
        outlinePaint.color = Color.BLACK
        outlinePaint.strokeWidth = 17f
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
    }

    // 2. Εσωτερικό Έγχρωμο Γέμισμα (Core)
    private var planningPolyline = Polyline().apply {
        outlinePaint.color = Color.parseColor("#00D7E6")
        outlinePaint.strokeWidth = 9f
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
    }

    fun addPoint(point: GeoPoint): Double {
        if (planningPoints.isEmpty()) {
            planningBorderPolyline.setPoints(mutableListOf())
            planningPolyline.setPoints(mutableListOf())

            // <--- ΠΡΟΣΘΗΚΗ: Προσθέτουμε πρώτα το border και μετά την κύρια γραμμή
            if (!map.overlays.contains(planningBorderPolyline)) {
                map.overlays.add(planningBorderPolyline)
            }
            if (!map.overlays.contains(planningPolyline)) {
                map.overlays.add(planningPolyline)
            }
        }

        planningPoints.add(point)
        planningBorderPolyline.addPoint(point) // <--- ΠΡΟΣΘΗΚΗ
        planningPolyline.addPoint(point)

        val marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Σημείο ${planningPoints.size}"
            icon = context.getDrawable(R.drawable.baseline_gps_fixed_24)

            isDraggable = true
            infoWindow = null

            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {}

                override fun onMarkerDrag(marker: Marker) {
                    updateLineFromMarkers()
                }

                override fun onMarkerDragEnd(marker: Marker) {
                    updateLineFromMarkers()
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

        planningPoints.clear()
        planningPoints.addAll(newPoints)

        planningBorderPolyline.setPoints(planningPoints) // <--- ΠΡΟΣΘΗΚΗ
        planningPolyline.setPoints(planningPoints)

        map.invalidate()

        val newDistance = calculateTotalDistance()
        val lastPoint = planningPoints.lastOrNull()
        if (lastPoint != null) {
            routeUpdateListener?.onDistanceChanged(newDistance, lastPoint)
        }
    }

    fun calculateTotalDistance(): Double {
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
            planningPoints.removeAt(planningPoints.size - 1)
            planningBorderPolyline.setPoints(planningPoints) // <--- ΠΡΟΣΘΗΚΗ
            planningPolyline.setPoints(planningPoints)

            if (planningMarkers.isNotEmpty()) {
                val lastMarker = planningMarkers.removeAt(planningMarkers.size - 1)
                map.overlays.remove(lastMarker)
            }
            map.invalidate()
        }
        return calculateTotalDistance()
    }

    fun clearAll() {
        // <--- ΠΡΟΣΘΗΚΗ: Καθαρισμός και του border
        map.overlays.remove(planningBorderPolyline)
        planningBorderPolyline.setPoints(mutableListOf())

        map.overlays.remove(planningPolyline)
        planningPolyline.setPoints(mutableListOf())

        for (marker in planningMarkers) {
            map.overlays.remove(marker)
        }
        planningMarkers.clear()
        planningPoints.clear()
        map.invalidate()
    }

    fun getPlannedPoints(): List<GeoPoint> {
        return planningPoints
    }
}
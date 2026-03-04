package com.example.gpstracker

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val statsContainer: LinearLayout = findViewById(R.id.stats_container)
        statsContainer.removeAllViews()

        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val statsText = sharedPreferences.getString("stats", "") ?: ""
        val statsList = statsText.split("\n").filter { it.isNotEmpty() }

        statsList.forEachIndexed { index, stat ->
            val statView = layoutInflater.inflate(R.layout.stat_item, statsContainer, false)
            val parts = stat.split("\\s+".toRegex()).filter { it.isNotEmpty() }

            // ΕΛΕΓΧΟΣ: Πλέον έχουμε 6 μέρη (Ημερομηνία, Χρόνος, Απόσταση, Ταχύτητα, Βήματα, Αρχείο)
            if (parts.size >= 6) {
                statView.findViewById<TextView>(R.id.stat_date).text = parts[0]
                statView.findViewById<TextView>(R.id.stat_time).text = "Χρόνος: ${parts[1]}"
                statView.findViewById<TextView>(R.id.stat_dist).text = "Dist: ${parts[2]} km"
                statView.findViewById<TextView>(R.id.stat_avg_speed).text = "Avg: ${parts[3]} km/h"
                statView.findViewById<TextView>(R.id.stat_steps).text = "Steps: ${parts[4]}"

                val internalKmlName = parts[5] // Το όνομα του κρυφού αρχείου

                // Κουμπί Export: Αντιγράφει το κρυφό αρχείο στα Documents
                val exportIcon: ImageButton = statView.findViewById(R.id.export_button)
                exportIcon.setOnClickListener {
                    copyInternalKmlToPublic(internalKmlName)
                }
            }

            // Long Click για διαγραφή
            statView.setOnLongClickListener {
                showDeleteConfirmationDialog(index)
                true
            }

            statsContainer.addView(statView)
        }

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fab_delete_all)
            .setOnClickListener { showDeleteAllConfirmationDialog() }
    }

    private fun copyInternalKmlToPublic(internalFileName: String) {
        if (internalFileName == "no_path" || internalFileName == "error_kml") {
            Toast.makeText(this, "Δεν υπάρχουν γεωγραφικά δεδομένα", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 1. Πηγή: Εσωτερικός φάκελος της εφαρμογής (data/data/com.example.gpstracker/files)
            val sourceFile = File(filesDir, internalFileName)

            if (!sourceFile.exists()) {
                Toast.makeText(this, "Το αρχείο δεν βρέθηκε στο εσωτερικό σύστημα", Toast.LENGTH_SHORT).show()
                return
            }

            // 2. Προορισμός: Documents/FT Gps Tracker (Ορατό στον χρήστη)
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FT Gps Tracker")
            if (!publicDir.exists()) publicDir.mkdirs()

            val destinationFile = File(publicDir, internalFileName)

            // 3. Αντιγραφή (Stream Copy)
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "Εξαγωγή επιτυχής στα Documents/FT Gps Tracker!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("EXPORT_ERROR", "Σφάλμα αντιγραφής: ${e.message}")
            Toast.makeText(this, "Αποτυχία εξαγωγής αρχείου", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteStat(index: Int) {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val statsText = sharedPreferences.getString("stats", "") ?: ""
        val statsList = statsText.split("\n").filter { it.isNotEmpty() }

        if (index < statsList.size) {
            // Προαιρετικά: Εδώ θα μπορούσες να διαγράψεις και το φυσικό αρχείο KML από το filesDir
            // αν θέλεις να καθαρίζεις τελείως τον χώρο.

            val updatedStats = statsList.filterIndexed { i, _ -> i != index }.joinToString("\n")
            sharedPreferences.edit().putString("stats", updatedStats).apply()
            recreate()
            Toast.makeText(this, "Η διαδρομή διαγράφηκε.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Διαγραφή")
            .setMessage("Θέλετε να διαγράψετε αυτό το στατιστικό;")
            .setPositiveButton("Ναι") { _, _ -> deleteStat(index) }
            .setNegativeButton("Όχι", null)
            .show()
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Διαγραφή Όλων")
            .setMessage("Είστε σίγουροι ότι θέλετε να διαγράψετε όλο το ιστορικό;")
            .setPositiveButton("Ναι") { _, _ ->
                getSharedPreferences("gps_stats", Context.MODE_PRIVATE).edit().putString("stats", "").apply()
                recreate()
            }
            .setNegativeButton("Όχι", null)
            .show()
    }
}
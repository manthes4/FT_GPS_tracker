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
        val statsList = statsText.split("\n")
            .map { it.trim() }             // Αφαιρεί κενά διαστήματα από την αρχή και το τέλος κάθε γραμμής
            .filter { it.isNotEmpty() }    // Κρατάει μόνο τις γραμμές που έχουν πραγματικά δεδομένα

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

                val internalKmlName = parts[5] // Το όνομα του κρυφού αρχείου (π.χ. route_20260318_1804.kml)
                val distanceValue = parts[2]   // Η απόσταση (π.χ. 3.25)

                val exportIcon: ImageButton = statView.findViewById(R.id.export_button)
                exportIcon.setOnClickListener {
                    // Καλούμε τη συνάρτηση στέλνοντας και το όνομα ΚΑΙ την απόσταση
                    copyInternalKmlToPublic(internalKmlName, distanceValue)
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

    // Προσθέσαμε την παράμετρο distance
    fun copyInternalKmlToPublic(internalFileName: String, distance: String) {
        try {
            val sourceFile = File(filesDir, internalFileName)
            if (!sourceFile.exists()) return

            // Εύρεση φακέλου Documents (SD ή Εσωτερική)
            val externalDirs = getExternalFilesDirs(null)
            val baseDir = if (externalDirs.size > 1 && externalDirs[1] != null) {
                val sdRoot = externalDirs[1].absolutePath.split("/Android")[0]
                File(sdRoot, "Documents")
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            }

            val ftGpsDir = File(baseDir, "FT Gps Tracker")
            if (!ftGpsDir.exists()) ftGpsDir.mkdirs()

            // --- ΤΟ ΝΕΟ ΟΝΟΜΑ ΠΟΥ ΖΗΤΗΣΕΣ ---
            // Μετατροπή κόμματος σε τελεία για το όνομα αρχείου (π.χ. 3,25 -> 3.25)
            val cleanDist = distance.replace(",", ".")

            // Το τελικό όνομα αρχείου
            val finalFileName = "route_$cleanDist.kml"

            val destinationFile = File(ftGpsDir, finalFileName)

            // Αντιγραφή αρχείου
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Ενημέρωση του Android Media Scanner για να φανεί το αρχείο αμέσως
            android.media.MediaScannerConnection.scanFile(this, arrayOf(destinationFile.absolutePath), null, null)

            Toast.makeText(this, "Αποθηκεύτηκε ως: $finalFileName", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("EXPORT", "Error: ${e.message}")
        }
    }

    private fun doCopy(source: File, destination: File, label: String) {
        try {
            // Αντιγραφή byte-by-byte
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Ενημέρωση του συστήματος για το νέο αρχείο
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(destination.absolutePath),
                null
            ) { path, _ ->
                // Το αρχείο είναι πλέον ορατό παντού
            }

            Toast.makeText(this, "Αποθηκεύτηκε στο $label\n${destination.name}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Αποτυχία αντιγραφής: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Βοηθητική συνάρτηση για περίπτωση που η SD έχει περιορισμούς εγγραφής
    private fun copyInternalKmlToPublicFallback(internalFileName: String) {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FT Gps Tracker")
        if (!publicDir.exists()) publicDir.mkdirs()
        val sourceFile = File(filesDir, internalFileName)
        val destinationFile = File(publicDir, internalFileName)
        sourceFile.inputStream().use { input -> destinationFile.outputStream().use { output -> input.copyTo(output) } }
        Toast.makeText(this, "Αποθηκεύτηκε στην εσωτερική μνήμη (Fallback)", Toast.LENGTH_SHORT).show()
    }

    private fun deleteStat(index: Int) {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val statsText = sharedPreferences.getString("stats", "") ?: ""
        val statsList = statsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }


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
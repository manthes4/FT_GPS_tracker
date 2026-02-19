package com.example.gpstracker

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.* // For Locale and Date

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
            // 1. Φόρτωση του stat_item layout
            val statView = layoutInflater.inflate(R.layout.stat_item, statsContainer, false)

            // 2. Χωρισμός του κειμένου (Ημερομηνία Χρόνος Χιλιόμετρα)
            val parts = stat.split("\\s+".toRegex()).filter { it.isNotEmpty() }

            if (parts.size >= 4) { // Τώρα ελέγχουμε για 4 μέρη
                val tvDate: TextView = statView.findViewById(R.id.stat_date)
                val tvTime: TextView = statView.findViewById(R.id.stat_time)
                val tvDist: TextView = statView.findViewById(R.id.stat_dist)
                val tvAvg: TextView = statView.findViewById(R.id.stat_avg_speed)

                tvDate.text = parts[0]
                tvTime.text = "Χρόνος: ${parts[1]}"
                tvDist.text = "${parts[2]} km"
                tvAvg.text = "Avg: ${parts[3]} km/h"
            }

            // 3. Κουμπί Export (μέσα σε κάθε item)
            val exportIcon: ImageButton = statView.findViewById(R.id.export_button)
            exportIcon.setOnClickListener {
                showExportOptionsDialog(index, stat)
            }

            // 4. Long Click για διαγραφή της συγκεκριμένης διαδρομής
            statView.setOnLongClickListener {
                showDeleteConfirmationDialog(index)
                true
            }

            // 5. Προσθήκη του item στο container
            statsContainer.addView(statView)
        }

        // 6. Σύνδεση του Floating Button για διαγραφή όλων (ΕΞΩ από το loop)
        val deleteAllButton: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton = findViewById(R.id.fab_delete_all)
        deleteAllButton.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun formatStat(stat: String, index: Int): String {
        val parts = stat.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        // Έστω ότι το stat είναι: "19/02/26 00:45:10 5.20 6.5"
        return if (parts.size >= 4) {
            val date = parts[0]
            val time = parts[1]
            val dist = parts[2]
            val pace = parts[3]

            // Χρησιμοποιούμε \n για να αλλάξουμε σειρά εσωτερικά στο TextView
            "${index}. $date — $dist χλμ\n   Διάρκεια: $time | Ταχύτητα: $pace km/h"
        } else {
            "$index. $stat"
        }
    }

    private fun showDeleteConfirmationDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Επιβεβαίωση Διαγραφής")
            .setMessage("Είστε σίγουροι ότι θέλετε να διαγράψετε αυτό το στατιστικό;")
            .setPositiveButton("Ναι") { _, _ ->
                deleteStat(index)
            }
            .setNegativeButton("Όχι", null)
            .create()
            .show()
    }

    private fun deleteStat(index: Int) {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val statsText = sharedPreferences.getString("stats", "") ?: ""
        val statsList = statsText.split("\n").filter { it.isNotEmpty() }

        if (index < statsList.size) {
            val updatedStats = statsList.filterIndexed { i, _ -> i != index }.joinToString("\n")
            sharedPreferences.edit().putString("stats", updatedStats).apply()

            recreate()
            Toast.makeText(this, "Το στατιστικό διαγράφηκε επιτυχώς.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportOptionsDialog(index: Int, stat: String) {
        val options = arrayOf("Εξαγωγή σε KML")
        AlertDialog.Builder(this)
            .setTitle("Επιλογή Μορφής Εξαγωγής")
            .setItems(options) { _, which ->
                if (which == 0) {
                    exportToKML(index, stat) // Pass 'stat' here
                }
            }
            .create()
            .show()
    }

    private fun exportToKML(index: Int, stat: String) {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        val routeData = sharedPreferences.getString("route_data", "") ?: ""

        val coordinatesList = routeData.trim().split("\n").map { line ->
            val parts = line.trim().split(",")
            if (parts.size >= 2) {
                "${parts[1].trim()},${parts[0].trim()}"
            } else {
                ""
            }
        }.filter { it.isNotEmpty() }.joinToString(" ")

        val kmlContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
            <Document>
                <name>Διαδρομή $index</name>
                <Placemark>
                    <name>Διαδρομή $index</name>
                    <LineString>
                        <coordinates>
                            $coordinatesList
                        </coordinates>
                    </LineString>
                </Placemark>
            </Document>
        </kml>
    """.trimIndent()

        // 1. Get Date and Distance
        val dateFormat = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        // Extract distance and unit separately
        val parts = stat.split("\\s+".toRegex())
        val distance = parts.getOrNull(parts.size - 2)?.trim() ?: "" // Get distance value
        val unit = parts.lastOrNull()?.trim() ?: "χλμ" // Get unit (e.g., "χλμ")

        // 2. Create File Name
        val fileName = "${currentDate}_${distance}${unit}.kml" // Combine date, distance, and unit

        // 3. Update saveToDocuments Call
        saveToDocuments(fileName, kmlContent, "application/vnd.google-earth.kml+xml")
    }

    private fun saveToDocuments(fileName: String, content: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/FT Gps Tracker")
            }

            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri != null) {
                var outputStream: OutputStream? = null
                try {
                    outputStream = contentResolver.openOutputStream(uri)
                    outputStream?.write(content.toByteArray())
                    outputStream?.flush()
                    Toast.makeText(this, "Το αρχείο αποθηκεύτηκε ως $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("StatsActivity", "Σφάλμα κατά την αποθήκευση του αρχείου", e)
                    Toast.makeText(this, "Σφάλμα κατά την αποθήκευση", Toast.LENGTH_LONG).show()
                } finally {
                    outputStream?.close()
                }
            } else {
                Toast.makeText(this, "Αποτυχία αποθήκευσης αρχείου", Toast.LENGTH_LONG).show()
            }
        } else {
            saveFileLegacy(fileName, content)
        }
    }

    private fun saveFileLegacy(fileName: String, content: String) {
        try {
            val file = File(getExternalFilesDir(null), fileName)
            val fos = FileOutputStream(file)
            fos.write(content.toByteArray())
            fos.flush()
            fos.close()

            Toast.makeText(this, "Το αρχείο αποθηκεύτηκε ως $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("StatsActivity", "Σφάλμα κατά την αποθήκευση του αρχείου", e)
            Toast.makeText(this, "Σφάλμα κατά την αποθήκευση", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Επιβεβαίωση Διαγραφής Όλων")
            .setMessage("Είστε σίγουροι ότι θέλετε να διαγράψετε όλα τα στατιστικά;")
            .setPositiveButton("Ναι") { _, _ ->
                deleteAllStats()
            }
            .setNegativeButton("Όχι", null)
            .create()
            .show()
    }

    private fun deleteAllStats() {
        val sharedPreferences = getSharedPreferences("gps_stats", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("stats", "").apply()

        recreate()
        Toast.makeText(this, "Όλα τα στατιστικά διαγράφηκαν επιτυχώς.", Toast.LENGTH_SHORT).show()
    }
}

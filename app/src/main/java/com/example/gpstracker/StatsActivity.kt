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

            // 2. Χωρισμός του κειμένου (Ημερομηνία Χρόνος Χιλιόμετρα Ταχύτητα Βήματα)
            val parts = stat.split("\\s+".toRegex()).filter { it.isNotEmpty() }

            // ΕΛΕΓΧΟΣ: Περιμένουμε πλέον 5 μέρη (Date, Time, Distance, Speed, Steps)
            if (parts.size >= 5) {
                val tvDate: TextView = statView.findViewById(R.id.stat_date)
                val tvTime: TextView = statView.findViewById(R.id.stat_time)
                val tvDist: TextView = statView.findViewById(R.id.stat_dist)
                val tvAvg: TextView = statView.findViewById(R.id.stat_avg_speed)
                val tvSteps: TextView = statView.findViewById(R.id.stat_steps)

                // Εμφάνιση μόνο της ημερομηνίας χωρίς το λεκτικό "Ημερομηνία:"
                tvDate.text = parts[0]

                // Προσθήκη της λέξης "Χρόνος:" πριν από την τιμή (π.χ. Χρόνος: 00:45:12)
                tvTime.text = "Χρόνος: ${parts[1]}"

                // Απόσταση
                tvDist.text = "Dist: ${parts[2]} km"

                // Ταχύτητα με το πρόθεμα Avg:
                tvAvg.text = "Avg: ${parts[3]} km/h"

                // Βήματα με το λεκτικό
                tvSteps.text = "Steps: ${parts[4]}"
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

        // Τώρα περιμένουμε 5 μέρη: Ημερομηνία, Χρόνος, Απόσταση, Ταχύτητα, Βήματα
        return if (parts.size >= 5) {
            val date = parts[0]
            val time = parts[1]
            val dist = parts[2]
            val avgSpeed = parts[3]
            val steps = parts[4]

            // Προσθέτουμε και τα βήματα στην εμφάνιση
            "${index + 1}. $date — $dist χλμ\n   Διάρκεια: $time | Ταχύτητα: $avgSpeed km/h | Βήματα: $steps"
        } else {
            "${index + 1}. $stat"
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
        // Παίρνουμε τα δεδομένα που σώθηκαν από την MainActivity
        val routeData = sharedPreferences.getString("route_data", "") ?: ""

        // 1. ΕΛΕΓΧΟΣ: Αν δεν υπάρχουν συντεταγμένες, ενημέρωσε και βγες
        if (routeData.isBlank()) {
            Toast.makeText(this, "Δεν βρέθηκαν γεωγραφικά δεδομένα για εξαγωγή", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. ΕΠΕΞΕΡΓΑΣΙΑ ΣΥΝΤΕΤΑΓΜΕΝΩΝ
        // Εφόσον είναι ήδη "lon,lat", απλά καθαρίζουμε τις αλλαγές γραμμής σε κενά
        val coordinatesList = routeData.trim().replace("\n", " ")

        // 3. ΔΗΜΙΟΥΡΓΙΑ ΠΕΡΙΕΧΟΜΕΝΟΥ KML
        val kmlContent = """
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
    <Document>
        <name>Διαδρομή ${index + 1}</name>
        <Placemark>
            <name>Στατιστικά: $stat</name>
            <description>$stat</description>
            <LineString>
                <extrude>1</extrude>
                <tessellate>1</tessellate>
                <coordinates>$coordinatesList</coordinates>
            </LineString>
        </Placemark>
    </Document>
</kml>
""".trimIndent()

        // --- ΠΑΙΡΝΟΥΜΕ DATE / TIME / DISTANCE / STEPS ΑΠΟ ΤΟ stat ---
        val parts = stat.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        val date = parts.getOrNull(0)?.replace("/", "-") ?: "no_date"
        val time = parts.find { it.contains(":") }?.replace(":", "-") ?: "00-00-00"
        val distance = parts.find { it.contains("km") }?.replace("km", "")?.replace(",", ".") ?: "0"
        val steps = parts.find { it.contains("βήματα") }?.replace("βήματα", "") ?: "0"

        // ✅ ΤΩΡΑ αποθηκεύουμε και τον χρόνο
        val fileName = "Route_${date}_${time}_${distance}km_${steps}steps.kml"

        // 5. ΑΠΟΘΗΚΕΥΣΗ
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

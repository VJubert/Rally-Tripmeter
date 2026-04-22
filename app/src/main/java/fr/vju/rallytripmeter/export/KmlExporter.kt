package fr.vju.rallytripmeter.export

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import fr.vju.rallytripmeter.database.LocationPointEntity
import fr.vju.rallytripmeter.database.PathEntity
import java.io.File
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KmlExporter(private val context: Context) {
    
    fun exportPathToKml(path: PathEntity, locationPoints: List<LocationPointEntity>): File? {
        // Legacy method for Android 9 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val kmlContent = generateKmlContent(path, locationPoints)

            val documentsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "RallyTrips"
            )

            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }

            val fileName = "${path.name.replace(" ", "_")}.kml"
            val file = File(documentsDir, fileName)

            return try {
                FileWriter(file).use { writer ->
                    writer.write(kmlContent)
                }
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        // For Android 10+, use SAF via exportPathToKmlWithUri
        return null
    }

    fun exportPathToKmlWithUri(uri: Uri, path: PathEntity, locationPoints: List<LocationPointEntity>): Boolean {
        val kmlContent = generateKmlContent(path, locationPoints)

        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                outputStream.write(kmlContent.toByteArray())
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateKmlContent(path: PathEntity, locationPoints: List<LocationPointEntity>): String {
        // KML is a standardized data interchange format that requires decimal points (.)
        // Using Locale.US ensures consistent formatting regardless of user's device locale
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        
        val coordinates = locationPoints.joinToString("\n") { point ->
            "${point.longitude},${point.latitude},0"
        }
        
        return """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>${path.name}</name>
    <description>
        Distance: ${String.format(Locale.US, "%.2f", path.totalDistance / 1000)} km
        Average Speed: ${String.format(Locale.US, "%.1f", path.averageSpeed)} km/h
        Start: ${dateFormat.format(Date(path.startTime))}
        ${if (path.endTime != null) "End: ${dateFormat.format(Date(path.endTime))}" else ""}
    </description>
    
    <Placemark>
      <name>Path</name>
      <LineString>
        <coordinates>
$coordinates
        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>"""
    }
}

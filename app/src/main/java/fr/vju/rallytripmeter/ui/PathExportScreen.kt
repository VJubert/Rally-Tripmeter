package fr.vju.rallytripmeter.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vju.rallytripmeter.R
import fr.vju.rallytripmeter.database.AppDatabase
import fr.vju.rallytripmeter.database.PathEntity
import fr.vju.rallytripmeter.export.KmlExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PathExportScreen() {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val coroutineScope = rememberCoroutineScope()

    val paths by database.pathDao().getAllPaths().collectAsState(initial = emptyList())

    // State for SAF export
    var pathToExport by remember { mutableStateOf<PathEntity?>(null) }
    var locationPointsToExport by remember { mutableStateOf<List<fr.vju.rallytripmeter.database.LocationPointEntity>>(emptyList()) }

    // Pre-resolve string resources for use in callbacks
    val exportedToDocuments = stringResource(R.string.exported_to_documents)
    val exportError = stringResource(R.string.export_error)

    // SAF launcher for Android 10+
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { uri ->
        if (uri != null && pathToExport != null) {
            coroutineScope.launch {
                val exporter = KmlExporter(context)
                val success = exporter.exportPathToKmlWithUri(uri, pathToExport!!, locationPointsToExport)
                if (success) {
                    Toast.makeText(
                        context,
                        exportedToDocuments,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        exportError,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pathToExport = null
                locationPointsToExport = emptyList()
            }
        }
    }

    // Group paths by date
    val pathsByDate = paths.groupBy { path ->
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date(path.startTime))
    }
    
    Surface(color = colorResource(R.color.dark_gray)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.export_paths),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Paths list grouped by date
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                pathsByDate.forEach { (date, datePaths) ->
                    item {
                        Text(
                            text = date,
                            color = Color(0xFFA4A4A4),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(datePaths) { path ->
                        PathItem(
                            path = path,
                            onExportClick = {
                                coroutineScope.launch {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // Android 10+: Use SAF to let user choose save location in Documents
                                        pathToExport = path
                                        locationPointsToExport = database.locationPointDao().getLocationPointsByPathIdSync(path.id)
                                        val fileName = "${path.name.replace(" ", "_")}.kml"
                                        safLauncher.launch(fileName)
                                    } else {
                                        // Android 9 and below: Use legacy method
                                        exportPath(context, database, path, exportError)
                                    }
                                }
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PathItem(
    path: PathEntity,
    onExportClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startDate = dateFormat.format(Date(path.startTime))
    val inProgressText = stringResource(R.string.in_progress)
    val endDate = path.endTime?.let { dateFormat.format(Date(it)) } ?: inProgressText

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = path.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$startDate - $endDate",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "${String.format(Locale.getDefault(), "%.2f", path.totalDistance / 1000)} km | ${String.format(Locale.getDefault(), "%.1f", path.averageSpeed)} km/h",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.green))
            ) {
                Text(stringResource(R.string.export_kml), color = Color.White)
            }
        }
    }
}

private suspend fun exportPath(context: Context, database: AppDatabase, path: PathEntity, exportError: String) {
    val locationPoints = database.locationPointDao().getLocationPointsByPathIdSync(path.id)
    val exporter = KmlExporter(context)
    val file = exporter.exportPathToKml(path, locationPoints)

    if (file != null) {
        Toast.makeText(
            context,
            "Exporté: ${file.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    } else {
        Toast.makeText(
            context,
            exportError,
            Toast.LENGTH_SHORT
        ).show()
    }
}

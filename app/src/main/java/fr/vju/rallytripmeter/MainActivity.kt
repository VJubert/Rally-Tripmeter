package fr.vju.rallytripmeter

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import fr.vju.rallytripmeter.ui.theme.RallyTripmeterTheme

private val DigitalFont = FontFamily(Font(R.font.digital_7_regular))

class MainActivity : ComponentActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var currentSpeed by mutableFloatStateOf(0f)
    private var satelliteCount by mutableIntStateOf(0)
    private var locationPermissionGranted by mutableStateOf(false)
    
    // Recording state
    private var isRecording by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private var startTime by mutableLongStateOf(0L)
    private var startLocation: Location? = null
    private var lastLocation: Location? = null
    private var totalDistance by mutableFloatStateOf(0f)
    private var averageSpeed by mutableFloatStateOf(0f)
    
    // UI state
    private var showExportScreen by mutableStateOf(false)
    private var showExitDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        locationPermissionGranted = isGranted
        if (isGranted) {
            startLocationUpdates()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Notification permission granted or denied
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "LOCATION_UPDATE") {
                val speed = intent.getFloatExtra("speed", 0f)
                val satellites = intent.getIntExtra("satellites", 0)
                val latitude = intent.getDoubleExtra("latitude", 0.0)
                val longitude = intent.getDoubleExtra("longitude", 0.0)
                
                currentSpeed = speed
                satelliteCount = satellites
                
                // Track distance if recording and not paused
                if (isRecording && !isPaused && lastLocation != null) {
                    val newLocation = Location("service").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    }
                    val distance = lastLocation!!.distanceTo(newLocation)
                    totalDistance += distance
                    updateAverageSpeed()
                }
                
                lastLocation = Location("service").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure edge-to-edge with dark gray system bars
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        checkLocationPermission()
        checkNotificationPermission()
        
        // Register broadcast receiver for location updates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, IntentFilter("LOCATION_UPDATE"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationReceiver, IntentFilter("LOCATION_UPDATE"))
        }
        
        setContent {
            RallyTripmeterTheme {
                Surface(color = colorResource(R.color.dark_gray)) {
                    if (showExportScreen) {
                        fr.vju.rallytripmeter.ui.PathExportScreen()
                    } else {
                        RallyTripmeterScreen(
                            speed = currentSpeed,
                            satelliteCount = satelliteCount,
                            permissionGranted = locationPermissionGranted,
                            isRecording = isRecording,
                            isPaused = isPaused,
                            totalDistance = totalDistance,
                            averageSpeed = averageSpeed,
                            onRecordClick = { startRecording() },
                            onPauseClick = { pauseRecording() },
                            onStopClick = { stopRecording() },
                            onResetDistanceClick = { resetDistance() },
                            onResetAverageSpeedClick = { resetAverageSpeed() },
                            onExportClick = { showExportScreen = true },
                            configuration = resources.configuration
                        )
                    }
                }
                
                // Exit dialog
                if (showExitDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text(stringResource(R.string.quit_app)) },
                        text = {
                            if (isRecording) {
                                Text(stringResource(R.string.recording_in_progress))
                            } else {
                                Text(stringResource(R.string.really_quit))
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showExitDialog = false
                                    if (isRecording) {
                                        // Keep service running
                                        finish()
                                    } else {
                                        finish()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.yes))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showExitDialog = false
                                    if (isRecording) {
                                        stopRecording()
                                    }
                                    finish()
                                }
                            ) {
                                Text(if (isRecording) stringResource(R.string.no_stop) else stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
        
        // Handle back button
        onBackPressedDispatcher.addCallback(this) {
            if (showExportScreen) {
                showExportScreen = false
            } else {
                showExitDialog = true
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                locationPermissionGranted = true
                startLocationUpdates()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission granted
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentSpeed = location.speed * 3.6f // Convert m/s to km/h
        
        // Get satellite count from extra
        satelliteCount = location.extras?.getInt("satellites", 0) ?: 0
        
        // Track distance if recording and not paused
        if (isRecording && !isPaused && lastLocation != null) {
            val distance = lastLocation!!.distanceTo(location) // Distance in meters
            totalDistance += distance
            updateAverageSpeed()
        }
        
        lastLocation = location
    }
    
    private fun startRecording() {
        if (!isRecording) {
            isRecording = true
            isPaused = false
            startTime = System.currentTimeMillis()
            startLocation = lastLocation
            totalDistance = 0f
            averageSpeed = 0f
            startLocationService()
        } else if (isPaused) {
            isPaused = false
        }
    }
    
    private fun pauseRecording() {
        if (isRecording && !isPaused) {
            isPaused = true
        }
    }
    
    private fun stopRecording() {
        isRecording = false
        isPaused = false
        startLocation = null
        lastLocation = null
        totalDistance = 0f
        averageSpeed = 0f
        stopLocationService()
    }
    
    private fun resetDistance() {
        totalDistance = 0f
        startLocation = lastLocation
    }
    
    private fun resetAverageSpeed() {
        averageSpeed = 0f
        startTime = if (isRecording && !isPaused) System.currentTimeMillis() else 0L
    }
    
    private fun updateAverageSpeed() {
        if (startTime > 0) {
            val currentTime = System.currentTimeMillis()
            val elapsedTimeHours = (currentTime - startTime) / (1000.0 * 60 * 60) // Convert to hours
            val distanceKm = totalDistance / 1000.0 // Convert meters to km
            averageSpeed = if (elapsedTimeHours > 0) (distanceKm / elapsedTimeHours).toFloat() else 0f
        }
    }
    
    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
    }
    
    private fun stopLocationService() {
        val intent = Intent(this, LocationService::class.java)
        stopService(intent)
    }

    @Deprecated("Deprecated in Android API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        satelliteCount = extras?.getInt("satellites", 0) ?: 0
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {
        currentSpeed = 0f
        satelliteCount = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        unregisterReceiver(locationReceiver)
        stopLocationService()
    }
}

@Composable
fun RallyTripmeterScreen(
    speed: Float,
    satelliteCount: Int,
    permissionGranted: Boolean,
    isRecording: Boolean,
    isPaused: Boolean,
    totalDistance: Float,
    averageSpeed: Float,
    onRecordClick: () -> Unit,
    onPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetDistanceClick: () -> Unit,
    onResetAverageSpeedClick: () -> Unit,
    onExportClick: () -> Unit,
    configuration: Configuration
) {
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp, 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!permissionGranted) {
                Text(
                    text = stringResource(R.string.permission_location_required),
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                return
            }

            // Left side: Speed display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.0f", speed),
                    color = if (speed > 90) Color(0xFFFF6B6B) else Color.White,
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DigitalFont
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.kmh),
                    color = colorResource(R.color.light_gray),
                    fontSize = 30.sp
                )
            }

            // Right side: Recording data and controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Satellite info
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.satellites),
                            color = colorResource(R.color.light_gray),
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = satelliteCount.toString(),
                            color = if (satelliteCount >= 4) colorResource(R.color.green) else Color.Red,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2196F3), CircleShape)
                            .padding(2.dp)
                            .clickable { onExportClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "Export",
                            tint = Color.White
                        )
                    }
                }

                // Distance
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.distance),
                            color = colorResource(R.color.light_gray),
                            fontSize = 14.sp
                        )

                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", totalDistance / 1000)} km",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onResetDistanceClick,
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reset),
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Average speed
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.average_speed),
                            color = colorResource(R.color.light_gray),
                            fontSize = 14.sp
                        )

                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", averageSpeed)} km/h",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onResetAverageSpeedClick,
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reset),
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onRecordClick,
                        enabled = !isRecording || isPaused,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource( if (!isRecording || isPaused) R.color.green else android.R.color.transparent)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (!isRecording) stringResource(R.string.record) else stringResource(R.string.resume),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onPauseClick,
                        enabled = isRecording && !isPaused,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording && !isPaused) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.pause),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = onStopClick,
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color.Red else Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.stop),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    } else {
        // Portrait layout (original)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp, 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!permissionGranted) {
                Text(
                    text = stringResource(R.string.permission_location_required),
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                return
            }

            // Satellite info at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.satellites),
                        color = colorResource(R.color.light_gray),
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = satelliteCount.toString(),
                        color = if (satelliteCount >= 4) colorResource(R.color.green) else Color.Red,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2196F3), CircleShape)
                        .padding(2.dp)
                        .clickable { onExportClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = "Export",
                        tint = Color.White
                    )
                }
            }

            // Speed section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.0f", speed),
                    color = if (speed > 90) Color(0xFFFF6B6B) else Color.White,
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DigitalFont
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.kmh),
                    color = colorResource(R.color.light_gray),
                    fontSize = 50.sp
                )
            }

            // Recording data section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.distance),
                            color = colorResource(R.color.light_gray),
                            fontSize = 18.sp
                        )

                        Text(
                            text = "${String.format(Locale.getDefault(), "%.2f", totalDistance / 1000)} km",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onResetDistanceClick,
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reset),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 12.sp,
                                stepSize = 1.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.average_speed),
                            color = colorResource(R.color.light_gray),
                            fontSize = 18.sp
                        )

                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", averageSpeed)} km/h",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onResetAverageSpeedClick,
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFFFA500) else Color.Gray
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reset),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 12.sp,
                                stepSize = 1.sp
                            )
                        )
                    }
                }
            }

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onRecordClick,
                    enabled = !isRecording || isPaused,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource( if (!isRecording || isPaused) R.color.green else android.R.color.transparent)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (!isRecording) stringResource(R.string.record) else stringResource(R.string.resume),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onPauseClick,
                    enabled = isRecording && !isPaused,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording && !isPaused) Color(0xFFFFA500) else Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.pause),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onStopClick,
                    enabled = isRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color.Red else Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.stop),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RallyTripmeterScreenPreview() {
    val configuration = Configuration()
    configuration.orientation = Configuration.ORIENTATION_PORTRAIT
    RallyTripmeterTheme {
        RallyTripmeterScreen(
            speed = 45.5f,
            satelliteCount = 8,
            permissionGranted = true,
            isRecording = true,
            isPaused = false,
            totalDistance = 12500f,
            averageSpeed = 42.3f,
            onRecordClick = {},
            onPauseClick = {},
            onStopClick = {},
            onResetDistanceClick = {},
            onResetAverageSpeedClick = {},
            onExportClick = {},
            configuration = configuration
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun RallyTripmeterScreenLandscapePreview() {
    val configuration = Configuration()
    configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
    RallyTripmeterTheme {
        RallyTripmeterScreen(
            speed = 45.5f,
            satelliteCount = 8,
            permissionGranted = true,
            isRecording = true,
            isPaused = false,
            totalDistance = 12500f,
            averageSpeed = 42.3f,
            onRecordClick = {},
            onPauseClick = {},
            onStopClick = {},
            onResetDistanceClick = {},
            onResetAverageSpeedClick = {},
            onExportClick = {},
            configuration = configuration
        )
    }
}
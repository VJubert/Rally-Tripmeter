package fr.vju.rallytripmeter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import fr.vju.rallytripmeter.R
import fr.vju.rallytripmeter.database.AppDatabase
import fr.vju.rallytripmeter.database.LocationPointEntity
import fr.vju.rallytripmeter.database.PathEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val CHANNEL_ID = "location_service_channel"
    private val NOTIFICATION_ID = 1
    
    private lateinit var database: AppDatabase
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var currentPathId: Long? = null
    private var pathStartTime: Long = 0
    private var totalDistance: Float = 0f
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        database = AppDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Create new path when service starts
        createNewPath()
        
        startLocationUpdates()
        return START_STICKY
    }

    private fun createNewPath() {
        pathStartTime = System.currentTimeMillis()
        totalDistance = 0f
        lastLocation = null
        
        serviceScope.launch {
            val path = PathEntity(
                startTime = pathStartTime,
                name = "Path ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(pathStartTime))}"
            )
            currentPathId = database.pathDao().insertPath(path)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onLocationChanged(location: Location) {
        // Send broadcast to MainActivity with location data
        val intent = Intent("LOCATION_UPDATE")
        intent.putExtra("speed", location.speed * 3.6f)
        intent.putExtra("satellites", location.extras?.getInt("satellites", 0) ?: 0)
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        sendBroadcast(intent)
        
        // Save to database
        saveLocationPoint(location)
    }
    
    private fun saveLocationPoint(location: Location) {
        val pathId = currentPathId ?: return
        
        // Calculate distance
        if (lastLocation != null) {
            totalDistance += lastLocation!!.distanceTo(location)
        }
        lastLocation = location
        
        serviceScope.launch {
            val locationPoint = LocationPointEntity(
                pathId = pathId,
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed * 3.6f,
                timestamp = System.currentTimeMillis(),
                satelliteCount = location.extras?.getInt("satellites", 0) ?: 0
            )
            database.locationPointDao().insertLocationPoint(locationPoint)
            
            // Update path with current distance
            val path = database.pathDao().getPathById(pathId)
            path?.let {
                val elapsedTime = System.currentTimeMillis() - pathStartTime
                val avgSpeed = if (elapsedTime > 0) (totalDistance / 1000.0) / (elapsedTime / 1000.0 / 3600.0) else 0f
                val updatedPath = it.copy(
                    totalDistance = totalDistance,
                    averageSpeed = avgSpeed.toFloat()
                )
                database.pathDao().updatePath(updatedPath)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Update path with end time
        serviceScope.launch {
            currentPathId?.let { pathId ->
                val path = database.pathDao().getPathById(pathId)
                path?.let {
                    val updatedPath = it.copy(
                        endTime = System.currentTimeMillis()
                    )
                    database.pathDao().updatePath(updatedPath)
                }
            }
        }
        
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

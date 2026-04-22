package fr.vju.rallytripmeter.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paths")
data class PathEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistance: Float = 0f,
    val averageSpeed: Float = 0f,
    val name: String = ""
)

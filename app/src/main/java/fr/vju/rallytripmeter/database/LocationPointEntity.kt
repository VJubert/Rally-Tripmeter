package fr.vju.rallytripmeter.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = PathEntity::class,
            parentColumns = ["id"],
            childColumns = ["pathId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pathId")]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pathId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val timestamp: Long,
    val satelliteCount: Int
)

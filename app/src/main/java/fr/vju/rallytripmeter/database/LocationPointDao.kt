package fr.vju.rallytripmeter.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insertLocationPoint(locationPoint: LocationPointEntity)

    @Insert
    suspend fun insertLocationPoints(locationPoints: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE pathId = :pathId ORDER BY timestamp ASC")
    fun getLocationPointsByPathId(pathId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE pathId = :pathId ORDER BY timestamp ASC")
    suspend fun getLocationPointsByPathIdSync(pathId: Long): List<LocationPointEntity>
}

package fr.vju.rallytripmeter.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PathDao {
    @Insert
    suspend fun insertPath(path: PathEntity): Long

    @Update
    suspend fun updatePath(path: PathEntity)

    @Query("SELECT * FROM paths ORDER BY startTime DESC")
    fun getAllPaths(): Flow<List<PathEntity>>

    @Query("SELECT * FROM paths WHERE id = :pathId")
    suspend fun getPathById(pathId: Long): PathEntity?

    @Query("SELECT * FROM paths WHERE id = :pathId")
    fun getPathByIdFlow(pathId: Long): Flow<PathEntity?>

    @Query("SELECT * FROM paths WHERE date(startTime / 1000, 'unixepoch') = date(:date / 1000, 'unixepoch') ORDER BY startTime DESC")
    fun getPathsByDate(date: Long): Flow<List<PathEntity>>

    @Query("DELETE FROM paths WHERE id = :pathId")
    suspend fun deletePath(pathId: Long)

    @Query("DELETE FROM location_points WHERE pathId = :pathId")
    suspend fun deleteLocationPointsByPathId(pathId: Long)
}

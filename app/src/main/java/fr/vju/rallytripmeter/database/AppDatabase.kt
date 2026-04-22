package fr.vju.rallytripmeter.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PathEntity::class, LocationPointEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pathDao(): PathDao
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rally_tripmeter_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

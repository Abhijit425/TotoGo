package com.totogo.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Local cache of completed/cancelled rides (booking history for passengers, fare history for drivers). */
@Entity(tableName = "ride_history")
data class RideHistoryEntity(
    @PrimaryKey val rideId: String,
    val role: String,
    val pickupEn: String,
    val pickupBn: String,
    val destinationEn: String,
    val destinationBn: String,
    val fare: Int,
    val km: Double,
    val status: String,
    val paymentMethod: String,
    val counterpartName: String,
    val vehicleNumber: String,
    val rating: Int,
    val feedback: String,
    val completedAt: Long,
    val isDemo: Boolean,
    val synced: Boolean = false,
)

@Dao
interface RideHistoryDao {
    @Query("SELECT * FROM ride_history WHERE role = :role ORDER BY completedAt DESC")
    fun observe(role: String): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history WHERE synced = 0")
    suspend fun unsynced(): List<RideHistoryEntity>

    @Query("SELECT COUNT(*) FROM ride_history WHERE role = :role")
    suspend fun count(role: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: RideHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(e: List<RideHistoryEntity>)

    @Query("UPDATE ride_history SET rating = :rating, feedback = :feedback, paymentMethod = :method WHERE rideId = :rideId")
    suspend fun rate(rideId: String, rating: Int, feedback: String, method: String)

    @Query("UPDATE ride_history SET synced = 1 WHERE rideId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM ride_history WHERE isDemo = 1 AND completedAt < :before")
    suspend fun pruneDemo(before: Long)
}

@Database(entities = [RideHistoryEntity::class], version = 1, exportSchema = false)
abstract class TotoDatabase : RoomDatabase() {
    abstract fun history(): RideHistoryDao

    companion object {
        @Volatile private var instance: TotoDatabase? = null
        fun get(context: Context): TotoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TotoDatabase::class.java, "totogo.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}

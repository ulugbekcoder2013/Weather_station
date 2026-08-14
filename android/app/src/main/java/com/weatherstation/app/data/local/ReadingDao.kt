package com.weatherstation.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Query("SELECT * FROM cached_readings ORDER BY recordedAt DESC, id DESC LIMIT 1")
    fun getLatestReadingFlow(): Flow<ReadingEntity?>

    @Query("SELECT * FROM cached_readings ORDER BY recordedAt DESC, id DESC LIMIT 1")
    suspend fun getLatestReading(): ReadingEntity?

    @Query("SELECT * FROM cached_readings ORDER BY recordedAt ASC")
    fun getAllReadingsFlow(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM cached_readings ORDER BY recordedAt ASC")
    suspend fun getAllReadings(): List<ReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: ReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<ReadingEntity>)

    @Query("DELETE FROM cached_readings WHERE cachedAtTimestamp < :expiryTimestamp")
    suspend fun purgeOlderThan(expiryTimestamp: Long): Int

    @Query("DELETE FROM cached_readings")
    suspend fun clearAll()
}

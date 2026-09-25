package com.runcoach.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RunningRecord): Long

    @Query("SELECT * FROM running_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<RunningRecord>>

    @Query("SELECT * FROM running_records ORDER BY date DESC")
    suspend fun getAllRecordsSync(): List<RunningRecord>

    @Query("SELECT * FROM running_records WHERE id = :id")
    suspend fun getById(id: Long): RunningRecord?

    @Query("SELECT * FROM running_records ORDER BY date DESC LIMIT 1")
    suspend fun getLastRecord(): RunningRecord?

    @Query("SELECT * FROM running_records ORDER BY date DESC LIMIT :count")
    suspend fun getRecentRecords(count: Int): List<RunningRecord>

    @Query("SELECT * FROM running_records WHERE date >= :weekStart ORDER BY date DESC")
    fun getRecordsThisWeek(weekStart: Long): Flow<List<RunningRecord>>

    @Query("SELECT * FROM running_records WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): RunningRecord?
}

package com.runcoach.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BriefingRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BriefingRecord): Long

    @Query("SELECT * FROM briefing_records WHERE runRecordId = :runRecordId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForRun(runRecordId: Long): BriefingRecord?

    @Query("SELECT * FROM briefing_records ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BriefingRecord>>
}

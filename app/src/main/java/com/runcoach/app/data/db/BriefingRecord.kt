package com.runcoach.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "briefing_records",
    indices = [Index(value = ["briefingKey"], unique = true), Index(value = ["runRecordId"])]
)
data class BriefingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runRecordId: Long,
    val briefingKey: String,
    val briefingType: String,
    val content: String,
    val createdAt: Long,
    val provider: String = "template"
)

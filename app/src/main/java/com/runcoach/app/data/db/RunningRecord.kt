package com.runcoach.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "running_records",
    indices = [Index(value = ["externalId"], unique = true)]
)
data class RunningRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val distanceKm: Float,
    val targetKm: Float,
    val avgPace: String,
    val targetPace: String,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val durationSec: Int,
    val fatigueLevel: String,
    val completed: Boolean,
    val source: String = "runcoach",
    val externalId: String? = null,
    val sourcePackage: String? = null
)

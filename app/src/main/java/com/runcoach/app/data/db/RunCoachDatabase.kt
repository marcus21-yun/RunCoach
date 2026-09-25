package com.runcoach.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RunningRecord::class, WeeklyGoal::class, BriefingRecord::class],
    version = 2
)
abstract class RunCoachDatabase : RoomDatabase() {
    abstract fun runningRecordDao(): RunningRecordDao
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun briefingRecordDao(): BriefingRecordDao
}

package com.runcoach.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: WeeklyGoal)

    @Query("SELECT * FROM weekly_goals ORDER BY weekStart DESC LIMIT 1")
    fun getCurrentGoal(): Flow<WeeklyGoal?>

    @Query("SELECT * FROM weekly_goals ORDER BY weekStart DESC LIMIT 1")
    suspend fun getCurrentGoalOnce(): WeeklyGoal?

    @Query("SELECT * FROM weekly_goals ORDER BY weekStart DESC LIMIT 1")
    fun getCurrentGoalSync(): WeeklyGoal?

    @Query("SELECT * FROM weekly_goals ORDER BY weekStart DESC")
    fun getAllGoals(): Flow<List<WeeklyGoal>>
}

package com.runcoach.app.di

import android.content.Context
import androidx.room.Room
import com.runcoach.app.data.db.RunCoachDatabase
import com.runcoach.app.data.history.DefaultHistoryRepository
import com.runcoach.app.data.health.SamsungHealthImportManager
import com.runcoach.app.data.supabase.SupabaseSyncService
import com.runcoach.app.domain.history.HistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RunCoachDatabase =
        Room.databaseBuilder(
            context,
            RunCoachDatabase::class.java,
            "runcoach.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideRunningRecordDao(db: RunCoachDatabase) = db.runningRecordDao()

    @Provides
    fun provideWeeklyGoalDao(db: RunCoachDatabase) = db.weeklyGoalDao()

    @Provides
    fun provideBriefingRecordDao(db: RunCoachDatabase) = db.briefingRecordDao()

    @Provides
    @Singleton
    fun provideSupabaseSyncService() = SupabaseSyncService()

    @Provides
    @Singleton
    fun provideHistoryRepository(
        db: RunCoachDatabase,
        healthImportManager: SamsungHealthImportManager,
        supabaseSyncService: SupabaseSyncService
    ): HistoryRepository = DefaultHistoryRepository(
        recordDao = db.runningRecordDao(),
        briefingRecordDao = db.briefingRecordDao(),
        healthImportManager = healthImportManager,
        supabaseSyncService = supabaseSyncService
    )

    @Provides
    @Singleton
    fun provideSamsungHealthImportManager(
        @ApplicationContext context: Context,
        db: RunCoachDatabase,
        composer: com.runcoach.app.domain.BriefingComposer
    ) = SamsungHealthImportManager(
        context = context,
        runningRecordDao = db.runningRecordDao(),
        briefingRecordDao = db.briefingRecordDao(),
        briefingComposer = composer
    )
}

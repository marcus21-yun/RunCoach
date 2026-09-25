package com.runcoach.wear.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WearDataStore, AlarmTriggerManager, RunningRepository 모두
 * @Inject constructor + @Singleton 으로 자동 제공되므로
 * 별도 @Provides 불필요 — 중복 바인딩 방지를 위해 비워둠
 */
@Module
@InstallIn(SingletonComponent::class)
object WearModule

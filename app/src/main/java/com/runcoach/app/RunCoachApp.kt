package com.runcoach.app

import android.app.Application
import com.runcoach.app.alarm.WeeklyAlarmReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RunCoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeeklyAlarmReceiver.schedule(this)
    }
}

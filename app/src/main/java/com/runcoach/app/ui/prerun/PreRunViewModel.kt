package com.runcoach.app.ui.prerun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.db.WeeklyGoalDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PreRunViewModel @Inject constructor(
    recordDao: RunningRecordDao,
    goalDao: WeeklyGoalDao
) : ViewModel() {

    val lastRecord = recordDao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentGoal = goalDao.getCurrentGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

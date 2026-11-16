
package com.wakeguard.alarm.data.repository

import com.wakeguard.alarm.data.local.AlarmDao
import com.wakeguard.alarm.data.local.AlarmEntity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmRepository(
    private val alarmDao: AlarmDao
) {

    suspend fun insertAlarm(
        alarm: AlarmEntity
    ): Long = withContext(Dispatchers.IO) {
        alarmDao.insertAlarm(alarm)
    }

    suspend fun updateAlarm(
        alarm: AlarmEntity
    ): Int = withContext(Dispatchers.IO) {
        alarmDao.updateAlarm(alarm)
    }

    suspend fun deleteAlarm(
        alarm: AlarmEntity
    ): Int = withContext(Dispatchers.IO) {
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun getAllAlarms(): List<AlarmEntity> =
        withContext(Dispatchers.IO) {
            alarmDao.getAllAlarms()
        }

    suspend fun getAlarmById(
        id: Long
    ): AlarmEntity? = withContext(Dispatchers.IO) {
        alarmDao.getAlarmById(id)
    }

    suspend fun getEnabledAlarms(): List<AlarmEntity> =
        withContext(Dispatchers.IO) {
            alarmDao.getEnabledAlarms()
        }

    suspend fun deleteAlarmById(
        id: Long
    ): Int = withContext(Dispatchers.IO) {
        alarmDao.deleteAlarmById(id)
    }
}

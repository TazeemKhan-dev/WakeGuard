
package com.wakeguard.alarm.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarms",
    indices = [
        Index(value = ["schedulerId"], unique = true)
    ]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val schedulerId: Int,

    val hour: Int,
    val minute: Int,

    val label: String = "New Alarm",
    val isEnabled: Boolean = true,

    // Future weekday repeat support
    val repeatDays: String = "",

    // Future Linked Alarm Groups
    val groupId: Long? = null,

    val snoozeMinutes: Int = 5,
    val vibrationEnabled: Boolean = true,

    val soundUri: String? = null,

    // Incremented when a scheduled occurrence is replaced.
    @ColumnInfo(defaultValue = "1")
    val scheduleGeneration: Long = 1L,

    @ColumnInfo(defaultValue = "'SWIPE'")
    val challengeType: String = "SWIPE",

    @ColumnInfo(defaultValue = "'EASY'")
    val mathDifficulty: String = "EASY",

    @ColumnInfo(defaultValue = "3")
    val mathQuestionCount: Int = 3,

    @ColumnInfo(defaultValue = "10")
    val shakeCount: Int = 10,

    @ColumnInfo(defaultValue = "0")
    val awakeCheckEnabled: Boolean = false,

    @ColumnInfo(defaultValue = "5")
    val awakeCheckDelayMinutes: Int = 5,

    @ColumnInfo(defaultValue = "60")
    val awakeCheckTimeoutSeconds: Int = 60,

    @ColumnInfo(defaultValue = "'EASY'")
    val memoryDifficulty: String = "EASY",

    @ColumnInfo(defaultValue = "3")
    val memoryRounds: Int = 3,

    @ColumnInfo(defaultValue = "10")
    val memoryDurationSeconds: Int = 10
)

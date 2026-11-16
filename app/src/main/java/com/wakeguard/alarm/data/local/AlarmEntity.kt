
package com.wakeguard.alarm.data.local

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

    val soundUri: String? = null
)


package com.wakeguard.alarm.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AlarmDao {

    @Insert
    long insertAlarm(AlarmEntity alarm);

    @Update
    int updateAlarm(AlarmEntity alarm);

    @Delete
    int deleteAlarm(AlarmEntity alarm);

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    List<AlarmEntity> getAllAlarms();

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    AlarmEntity getAlarmById(long id);

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    List<AlarmEntity> getEnabledAlarms();

    @Query("DELETE FROM alarms WHERE id = :id")
    int deleteAlarmById(long id);
}

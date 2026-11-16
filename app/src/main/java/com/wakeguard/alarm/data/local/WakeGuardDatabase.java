package com.wakeguard.alarm.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = { AlarmEntity.class }, version = 1, exportSchema = false)
public abstract class WakeGuardDatabase
        extends RoomDatabase {

    public abstract AlarmDao alarmDao();

    private static volatile WakeGuardDatabase INSTANCE;

    public static WakeGuardDatabase getInstance(
            Context context) {
        if (INSTANCE == null) {
            synchronized (WakeGuardDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            WakeGuardDatabase.class,
                            "wakeguard_database").build();
                }
            }
        }

        return INSTANCE;
    }
}

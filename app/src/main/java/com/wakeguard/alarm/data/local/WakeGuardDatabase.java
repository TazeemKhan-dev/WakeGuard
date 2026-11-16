
package com.wakeguard.alarm.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {AlarmEntity.class},
        version = 2,
        exportSchema = false
)
public abstract class WakeGuardDatabase extends RoomDatabase {

    public abstract AlarmDao alarmDao();

    private static volatile WakeGuardDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {
                    database.execSQL(
                            "ALTER TABLE alarms " +
                                    "ADD COLUMN scheduleGeneration " +
                                    "INTEGER NOT NULL DEFAULT 1"
                    );
                }
            };

    public static WakeGuardDatabase getInstance(
            Context context
    ) {
        if (INSTANCE == null) {
            synchronized (WakeGuardDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    WakeGuardDatabase.class,
                                    "wakeguard_database"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}

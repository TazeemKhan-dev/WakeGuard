
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
        version = 5,
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

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN challengeType TEXT NOT NULL DEFAULT 'SWIPE'");
            database.execSQL("ALTER TABLE alarms ADD COLUMN mathDifficulty TEXT NOT NULL DEFAULT 'EASY'");
            database.execSQL("ALTER TABLE alarms ADD COLUMN mathQuestionCount INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE alarms ADD COLUMN shakeCount INTEGER NOT NULL DEFAULT 10");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN awakeCheckEnabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE alarms ADD COLUMN awakeCheckDelayMinutes INTEGER NOT NULL DEFAULT 5");
            database.execSQL("ALTER TABLE alarms ADD COLUMN awakeCheckTimeoutSeconds INTEGER NOT NULL DEFAULT 60");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN memoryDifficulty TEXT NOT NULL DEFAULT 'EASY'");
            database.execSQL("ALTER TABLE alarms ADD COLUMN memoryRounds INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE alarms ADD COLUMN memoryDurationSeconds INTEGER NOT NULL DEFAULT 10");
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}

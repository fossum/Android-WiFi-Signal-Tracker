package com.example.wifisignaltracker;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * The Room database for the application.
 * Defines the entities and provides access to the DAOs.
 */
@Database(entities = {SignalMeasurement.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SignalDao signalDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Singleton pattern to get the database instance.
     */
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "wifi_signal_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
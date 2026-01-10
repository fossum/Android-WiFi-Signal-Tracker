package com.example.wifisignaltracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object (DAO) for the measurements table.
 * Defines the database operations.
 */
@Dao
public interface SignalDao {
    @Insert
    void insert(SignalMeasurement measurement);

    @Insert
    void insertAll(List<SignalMeasurement> measurements);

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    List<SignalMeasurement> getAllMeasurements();

    @Query("SELECT * FROM measurements WHERE ssid = :ssid")
    List<SignalMeasurement> getMeasurementsBySsid(String ssid);

    @Query("SELECT DISTINCT ssid FROM measurements")
    List<String> getUniqueSsids();

    @Query("SELECT DISTINCT ssid FROM measurements WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    List<String> getUniqueSsidsInBounds(double minLat, double maxLat, double minLng, double maxLng);

    @Query("SELECT * FROM measurements WHERE ssid IN (:ssids)")
    List<SignalMeasurement> getMeasurementsForSsids(List<String> ssids);

    @Query("DELETE FROM measurements")
    void deleteAll();
}
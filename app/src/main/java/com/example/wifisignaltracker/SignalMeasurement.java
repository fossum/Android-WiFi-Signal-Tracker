package com.example.wifisignaltracker;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Data class representing a WiFi signal measurement at a specific location.
 * Annotated as a Room Entity for SQLite persistence.
 */
@Entity(tableName = "measurements")
public class SignalMeasurement {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private double latitude;
    private double longitude;
    private int signalStrength; // in dBm
    private long timestamp;
    private String ssid;

    /**
     * Default constructor for Room.
     */
    public SignalMeasurement() {
    }

    /**
     * Convenience constructor for creating new measurements in code.
     * Annotated with @Ignore so Room doesn't get confused about which constructor to use.
     */
    @Ignore
    public SignalMeasurement(double latitude, double longitude, int signalStrength, String ssid) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.signalStrength = signalStrength;
        this.ssid = ssid;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters required by Room
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getSignalStrength() { return signalStrength; }
    public void setSignalStrength(int signalStrength) { this.signalStrength = signalStrength; }

    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * Map signal strength to a hue for Google Maps markers.
     */
    public float getHue() {
        if (signalStrength >= -50) return 120f;      // Green (Excellent)
        else if (signalStrength >= -60) return 60f; // Yellow (Good)
        else if (signalStrength >= -70) return 30f; // Orange (Fair)
        else return 0f;                             // Red (Poor)
    }
}
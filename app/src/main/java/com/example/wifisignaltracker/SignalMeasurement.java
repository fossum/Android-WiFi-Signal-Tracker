package com.example.wifisignaltracker;

/**
 * Data class to store WiFi signal measurement with location
 */
public class SignalMeasurement {
    private final double latitude;
    private final double longitude;
    private final int signalStrength; // in dBm
    private final long timestamp;
    private final String ssid;

    public SignalMeasurement(double latitude, double longitude, int signalStrength, String ssid) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.signalStrength = signalStrength;
        this.ssid = ssid;
        this.timestamp = System.currentTimeMillis();
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public String getSsid() {
        return ssid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get color based on signal strength
     * Excellent: > -50 dBm
     * Good: -50 to -60 dBm
     * Fair: -60 to -70 dBm
     * Poor: < -70 dBm
     */
    public float getHue() {
        if (signalStrength >= -50) {
            return 120f; // Green (Excellent)
        } else if (signalStrength >= -60) {
            return 60f; // Yellow (Good)
        } else if (signalStrength >= -70) {
            return 30f; // Orange (Fair)
        } else {
            return 0f; // Red (Poor)
        }
    }
}

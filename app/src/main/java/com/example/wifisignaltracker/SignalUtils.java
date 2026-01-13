package com.example.wifisignaltracker;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class SignalUtils {

    // Weighted centroid algorithm constants
    private static final int SIGNAL_FILTER_THRESHOLD_DB = 25; // Filter signals weaker than max by this amount
    private static final double WEIGHT_OFFSET = 110.0; // Offset to ensure positive weights (min RSSI ~-110 dBm)
    private static final double WEIGHT_EXPONENT = 6.0; // Exponential weight to heavily favor strong signals

    /**
     * Improved Weighted Centroid algorithm.
     * To prevent a large number of weak signals from overwhelming a few strong ones,
     * we use a higher power for weighting and only consider measurements within
     * a reasonable range of the strongest detected signal.
     */
    public static LatLng calculateWeightedCentroid(List<SignalMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty()) return new LatLng(0,0);

        // 1. Find the strongest signal in the set
        int maxRssi = -127;
        for (SignalMeasurement m : measurements) {
            if (m.getSignalStrength() > maxRssi) maxRssi = m.getSignalStrength();
        }

        double totalWeight = 0;
        double weightedLat = 0;
        double weightedLng = 0;

        for (SignalMeasurement m : measurements) {
            // 2. Ignore signals that are significantly weaker than our best signal
            // This prevents "background noise" from distant measurements from pulling the center away.
            if (m.getSignalStrength() < (maxRssi - SIGNAL_FILTER_THRESHOLD_DB)) continue;

            // 3. Use an exponential weight to heavily favor strong signals.
            // A -30dBm signal will have vastly more influence than a -60dBm signal.
            double weight = Math.pow(Math.max(1, WEIGHT_OFFSET + m.getSignalStrength()), WEIGHT_EXPONENT);

            weightedLat += m.getLatitude() * weight;
            weightedLng += m.getLongitude() * weight;
            totalWeight += weight;
        }

        // Fallback if all were filtered
        if (totalWeight == 0) return new LatLng(measurements.get(0).getLatitude(), measurements.get(0).getLongitude());

        return new LatLng(weightedLat / totalWeight, weightedLng / totalWeight);
    }
}

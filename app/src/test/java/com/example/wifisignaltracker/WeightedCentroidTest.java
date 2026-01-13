package com.example.wifisignaltracker;

import com.google.android.gms.maps.model.LatLng;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class WeightedCentroidTest {

    @Test
    public void testCalculateWeightedCentroid_SinglePoint() {
        List<SignalMeasurement> measurements = new ArrayList<>();
        measurements.add(new SignalMeasurement(10.0, 20.0, -50, "TestSSID"));

        LatLng centroid = SignalUtils.calculateWeightedCentroid(measurements);

        assertNotNull(centroid);
        assertEquals(10.0, centroid.latitude, 0.0001);
        assertEquals(20.0, centroid.longitude, 0.0001);
    }

    @Test
    public void testCalculateWeightedCentroid_TwoEqualPoints() {
        List<SignalMeasurement> measurements = new ArrayList<>();
        measurements.add(new SignalMeasurement(10.0, 20.0, -50, "TestSSID"));
        measurements.add(new SignalMeasurement(10.0, 22.0, -50, "TestSSID"));

        LatLng centroid = SignalUtils.calculateWeightedCentroid(measurements);

        assertNotNull(centroid);
        // Midpoint
        assertEquals(10.0, centroid.latitude, 0.0001);
        assertEquals(21.0, centroid.longitude, 0.0001);
    }

    @Test
    public void testCalculateWeightedCentroid_StrongerPull() {
        List<SignalMeasurement> measurements = new ArrayList<>();
        // Strong signal at (10, 20)
        measurements.add(new SignalMeasurement(10.0, 20.0, -40, "TestSSID"));
        // Weak signal at (10, 22)
        measurements.add(new SignalMeasurement(10.0, 22.0, -80, "TestSSID"));

        LatLng centroid = SignalUtils.calculateWeightedCentroid(measurements);

        assertNotNull(centroid);
        // Should be much closer to 20.0 than 22.0
        // Expected behavior: The -40dBm signal is much stronger than -80dBm.
        // The algorithm uses Math.pow(110 + RSSI, 6).
        // W1 = (110 - 40)^6 = 70^6
        // W2 = (110 - 80)^6 = 30^6
        // W1 is massively larger than W2, so centroid should be very close to 20.0.

        // Actually, check the filter threshold.
        // Max is -40. Threshold is 25.
        // Cutoff is -40 - 25 = -65.
        // The -80dBm signal should be IGNORED completely.

        assertEquals(10.0, centroid.latitude, 0.0001);
        assertEquals(20.0, centroid.longitude, 0.0001);
    }

}

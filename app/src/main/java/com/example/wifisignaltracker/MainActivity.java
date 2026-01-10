package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity handles UI and visualizes WiFi signal data.
 * Supports a "Suspected Location" view (default) and a "Detailed View" for individual SSIDs.
 * Uses an improved Weighted Centroid algorithm to estimate broadcast location.
 * Implements Marker Clustering and visible region filtering for performance.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnCameraIdleListener, ClusterManager.OnClusterItemClickListener<WifiClusterItem> {

    // Weighted centroid algorithm constants
    private static final int SIGNAL_FILTER_THRESHOLD_DB = 25; // Filter signals weaker than max by this amount
    private static final double WEIGHT_OFFSET = 110.0; // Offset to ensure positive weights (min RSSI ~-110 dBm)
    private static final double WEIGHT_EXPONENT = 6.0; // Exponential weight to heavily favor strong signals

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ClusterManager<WifiClusterItem> clusterManager;

    private Button startButton;
    private TextView signalInfoText;

    // Database components
    private AppDatabase db;
    private ExecutorService databaseExecutor;

    // Kept for Polylines in detailed view (Clustering handles markers)
    private final List<Polyline> polylines = new ArrayList<>();

    private String selectedSsid = null; // State: null = summary view, non-null = detailed view

    // Handler removed - we use onCameraIdle instead
    // private final Handler mapUpdateHandler = new Handler(Looper.getMainLooper());
    // private Runnable mapUpdateRunnable;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false))) {
                    // Precise location access granted.
                    enableMyLocationUI();
                    // trigger refresh
                    if (mMap != null) {
                        onCameraIdle();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Database and Executor
        db = AppDatabase.getDatabase(this);
        databaseExecutor = Executors.newSingleThreadExecutor();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Bind UI components
        startButton = findViewById(R.id.start_button);
        signalInfoText = findViewById(R.id.signal_info_text);
        Button clearButton = findViewById(R.id.clear_button);

        // Initialize button actions
        startButton.setOnClickListener(v -> toggleService());
        clearButton.setOnClickListener(v -> clearAllData());

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        updateButtonState();
        checkAndRequestPermissions();
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(this, TrackingService.class);
        if (TrackingService.isRunning()) {
            stopService(serviceIntent);
            Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
            Toast.makeText(this, "Tracking started in background", Toast.LENGTH_SHORT).show();
        }
        updateButtonState();
    }

    private void updateButtonState() {
        if (TrackingService.isRunning()) {
            startButton.setText(R.string.stop_tracking);
        } else {
            startButton.setText(R.string.start_tracking);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Initialize ClusterManager
        clusterManager = new ClusterManager<>(this, mMap);
        clusterManager.setRenderer(new DefaultClusterRenderer<WifiClusterItem>(this, mMap, clusterManager) {
            @Override
            protected void onBeforeClusterItemRendered(@NonNull WifiClusterItem item, @NonNull MarkerOptions markerOptions) {
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(item.getHue()));
                markerOptions.title(item.getTitle());
                markerOptions.snippet(item.getSnippet());
                super.onBeforeClusterItemRendered(item, markerOptions);
            }
        });

        clusterManager.setOnClusterItemClickListener(this);

        mMap.setOnCameraIdleListener(this);
        mMap.setOnMarkerClickListener(clusterManager);

        // Reset to non-detailed view when clicking on blank part of map
        mMap.setOnMapClickListener(latLng -> {
            if (selectedSsid != null) {
                selectedSsid = null;
                // Force a refresh logic
                onCameraIdle();
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationUI();
        }
    }

    @Override
    public void onCameraIdle() {
        if (mMap == null) return;

        // Get visible bounds
        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        refreshMarkersFromDatabase(bounds);

        // Notify cluster manager that camera changed
        clusterManager.onCameraIdle();
    }

    private void refreshMarkersFromDatabase(LatLngBounds bounds) {
        databaseExecutor.execute(() -> {
            // Fetch data based on bounds to optimize
            if (selectedSsid == null) {
                // Summary View: Get SSIDs visible in this region
                List<String> visibleSsids = db.signalDao().getSsidsInBounds(
                    bounds.southwest.latitude, bounds.northeast.latitude,
                    bounds.southwest.longitude, bounds.northeast.longitude
                );

                // For these visible SSIDs, get ALL measurements to calculate accurate centroid
                // As requested: "load the WiFi points that are in bounds and then reload all points for those WiFis"
                List<SignalMeasurement> relevantMeasurements = new ArrayList<>();
                if (!visibleSsids.isEmpty()) {
                    // Chunking if too many ssids to avoid huge query?
                    // For now, assume it's manageable or Room handles "IN" clause reasonably well for <999 items
                    // If visibleSsids is huge, we might need to batch this.
                    // Simple batching:
                    int batchSize = 900;
                    for (int i = 0; i < visibleSsids.size(); i += batchSize) {
                        List<String> batch = visibleSsids.subList(i, Math.min(i + batchSize, visibleSsids.size()));
                        relevantMeasurements.addAll(db.signalDao().getMeasurementsForSsids(batch));
                    }
                }

                runOnUiThread(() -> showSummaryView(relevantMeasurements, visibleSsids.size()));

            } else {
                // Detailed View: Get all measurements for the selected SSID
                // We typically want to see all points for the selected network, even if panning
                List<SignalMeasurement> allMeasurements = db.signalDao().getMeasurementsBySsid(selectedSsid);

                runOnUiThread(() -> showDetailedView(allMeasurements));
            }
        });
    }

    private void showSummaryView(List<SignalMeasurement> relevantMeasurements, int uniqueCount) {
        clusterManager.clearItems();
        clearPolylines();

        Map<String, List<SignalMeasurement>> grouped = groupBySsid(relevantMeasurements);

        for (Map.Entry<String, List<SignalMeasurement>> entry : grouped.entrySet()) {
            String ssid = entry.getKey();
            LatLng suspectedLoc = calculateWeightedCentroid(entry.getValue());

            WifiClusterItem item = new WifiClusterItem(
                suspectedLoc,
                "Suspected: " + ssid,
                ssid,
                BitmapDescriptorFactory.HUE_AZURE
            );
            clusterManager.addItem(item);
        }

        clusterManager.cluster();
        signalInfoText.setText(String.format(Locale.getDefault(), "Viewing %d unique networks in area", uniqueCount));
    }

    private void showDetailedView(List<SignalMeasurement> relevant) {
        clusterManager.clearItems();
        clearPolylines();

        if (relevant.isEmpty()) {
            selectedSsid = null;
            // Fallback to update view again
            onCameraIdle();
            return;
        }

        LatLng suspectedLoc = calculateWeightedCentroid(relevant);

        // Add supporting measurement markers
        for (SignalMeasurement m : relevant) {
            WifiClusterItem item = new WifiClusterItem(
                m.getLatitude(), m.getLongitude(),
                m.getSignalStrength() + " dBm",
                m.getSsid(),
                m.getHue()
            );
            clusterManager.addItem(item);

            // Draw connection line
            Polyline polyline = mMap.addPolyline(new PolylineOptions()
                    .add(suspectedLoc, new LatLng(m.getLatitude(), m.getLongitude()))
                    .width(5).color(Color.BLUE));
            if (polyline != null) {
                polyline.setClickable(false);
                polylines.add(polyline);
            }
        }

        // Add suspected location marker
        WifiClusterItem mainItem = new WifiClusterItem(
            suspectedLoc,
            "Suspected: " + selectedSsid,
            selectedSsid,
            BitmapDescriptorFactory.HUE_AZURE
        );
        clusterManager.addItem(mainItem);

        clusterManager.cluster();

        signalInfoText.setText(String.format(Locale.getDefault(), "Detail: %s (%d points)", selectedSsid, relevant.size()));
    }

    private Map<String, List<SignalMeasurement>> groupBySsid(List<SignalMeasurement> measurements) {
        Map<String, List<SignalMeasurement>> map = new HashMap<>();
        for (SignalMeasurement m : measurements) {
            if (!map.containsKey(m.getSsid())) map.put(m.getSsid(), new ArrayList<>());
            map.get(m.getSsid()).add(m);
        }
        return map;
    }

    @Override
    public boolean onClusterItemClick(WifiClusterItem item) {
        String ssid = item.getSnippet();
        if (ssid != null) {
            if (ssid.equals(selectedSsid)) {
                return false;
            }
            selectedSsid = ssid;
            // Trigger refresh for detailed view
            onCameraIdle();
            mMap.animateCamera(CameraUpdateFactory.newLatLng(item.getPosition()));
            return true;
        }
        return false;
    }

    /**
     * Improved Weighted Centroid algorithm.
     */
    private LatLng calculateWeightedCentroid(List<SignalMeasurement> measurements) {
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
            if (m.getSignalStrength() < (maxRssi - SIGNAL_FILTER_THRESHOLD_DB)) continue;

            // 3. Use an exponential weight to heavily favor strong signals.
            double weight = Math.pow(Math.max(1, WEIGHT_OFFSET + m.getSignalStrength()), WEIGHT_EXPONENT);

            weightedLat += m.getLatitude() * weight;
            weightedLng += m.getLongitude() * weight;
            totalWeight += weight;
        }

        // Fallback if all were filtered
        if (totalWeight == 0) return new LatLng(measurements.get(0).getLatitude(), measurements.get(0).getLongitude());

        return new LatLng(weightedLat / totalWeight, weightedLng / totalWeight);
    }

    private void clearPolylines() {
        for (Polyline p : polylines) p.remove();
        polylines.clear();
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationUI() {
        if (mMap == null) return;
        mMap.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && selectedSsid == null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), 17));
            }
        });
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.POST_NOTIFICATIONS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            requestPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void clearAllData() {
        selectedSsid = null;
        clusterManager.clearItems();
        clearPolylines();
        clusterManager.cluster();
        databaseExecutor.execute(() -> {
            db.signalDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(this, "Database cleared", Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
        // Removed automatic loop start
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Removed automatic loop stop
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseExecutor != null) databaseExecutor.shutdown();
    }
}

package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.ClusterManager;

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
 */
import com.google.maps.android.clustering.Cluster;

/**
 * MainActivity handles UI and visualizes WiFi signal data.
 * Supports a "Suspected Location" view (default) and a "Detailed View" for individual SSIDs.
 * Uses an improved Weighted Centroid algorithm to estimate broadcast location.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ClusterManager.OnClusterItemClickListener<WifiClusterItem>, ClusterManager.OnClusterClickListener<WifiClusterItem> {

    private GoogleMap mMap;
    private ClusterManager<WifiClusterItem> mClusterManager;
    private FusedLocationProviderClient fusedLocationClient;
    
    private Button startButton;
    private TextView signalInfoText;

    // Database components
    private AppDatabase db;
    private ExecutorService databaseExecutor;
    
    private final List<Marker> markers = new ArrayList<>();
    private final List<Polyline> polylines = new ArrayList<>();
    
    private String selectedSsid = null; // State: null = summary view, non-null = detailed view

    private final Handler mapUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable mapUpdateRunnable;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false))) {
                    // Precise location access granted.
                    enableMyLocationUI();
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
            stopMapUpdates(); // Stop refreshing when service is stopped
            Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
            startMapUpdates(); // Start refreshing when service starts
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
        mClusterManager = new ClusterManager<>(this, mMap);
        mClusterManager.setOnClusterItemClickListener(this);
        mClusterManager.setOnClusterClickListener(this);

        // Point the map\'s listeners at the ClusterManager
        mMap.setOnCameraIdleListener(() -> {
            mClusterManager.onCameraIdle();
            refreshMarkersFromDatabase();
        });
        mMap.setOnMarkerClickListener(mClusterManager);
        
        // Reset to non-detailed view when clicking on blank part of map
        mMap.setOnMapClickListener(latLng -> {
            if (selectedSsid != null) {
                selectedSsid = null;
                refreshMarkersFromDatabase();
            }
        });
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationUI();
        }
        // Initial data load
        refreshMarkersFromDatabase();
    }

    private void startMapUpdates() {
        if (mapUpdateRunnable != null) return; // Already running

        mapUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshMarkersFromDatabase();
                mapUpdateHandler.postDelayed(this, 5000); // Poll every 5 seconds
            }
        };
        mapUpdateHandler.post(mapUpdateRunnable);
    }

    private void stopMapUpdates() {
        if (mapUpdateRunnable != null) {
            mapUpdateHandler.removeCallbacks(mapUpdateRunnable);
            mapUpdateRunnable = null;
        }
    }

    private void refreshMarkersFromDatabase() {
        // Determine the bounds *before* going to the background thread
        // This must be done on the main thread
        final com.google.android.gms.maps.model.LatLngBounds bounds;
        if (mMap != null && selectedSsid == null) {
            bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        } else {
            bounds = null;
        }

        databaseExecutor.execute(() -> {
            if (selectedSsid == null) {
                // Summary View: Load based on bounds
                if (bounds == null) return;
                
                List<String> visibleSsids = db.signalDao().getUniqueSsidsInBounds(
                        bounds.southwest.latitude, bounds.northeast.latitude,
                        bounds.southwest.longitude, bounds.northeast.longitude);

                List<SignalMeasurement> relevantMeasurements = new ArrayList<>();
                if (visibleSsids != null && !visibleSsids.isEmpty()) {
                    // Batch queries to avoid SQLite limits (approx 999 variables per query)
                    int batchSize = 900;
                    for (int i = 0; i < visibleSsids.size(); i += batchSize) {
                        List<String> batch = visibleSsids.subList(i, Math.min(i + batchSize, visibleSsids.size()));
                        List<SignalMeasurement> batchMeasurements = db.signalDao().getMeasurementsForSsids(batch);
                        if (batchMeasurements != null) {
                            relevantMeasurements.addAll(batchMeasurements);
                        }
                    }
                }

                final List<SignalMeasurement> finalMeasurements = relevantMeasurements;
                runOnUiThread(() -> {
                    mClusterManager.clearItems(); // Clear previous clusters
                    clearMapVisuals(); // Clear any manual markers just in case
                    // Switch listener to ClusterManager for Summary View
                    mMap.setOnMarkerClickListener(mClusterManager);
                    showSummaryView(finalMeasurements);
                    mClusterManager.cluster(); // Force re-clustering
                });
            } else {
                // Detailed View: Load specifically for the selected SSID
                // We use existing getMeasurementsBySsid or reuse the bulk fetch if we had it
                List<SignalMeasurement> measurements = db.signalDao().getMeasurementsBySsid(selectedSsid);
                runOnUiThread(() -> {
                    mClusterManager.clearItems();
                    mClusterManager.cluster(); // Clear clusters visually
                    clearMapVisuals();
                    // Switch listener to \'this\' for Detailed View (manual markers)
                    mMap.setOnMarkerClickListener(MainActivity.this);
                    showDetailedView(measurements);
                });
            }
        });
    }

    private void showSummaryView(List<SignalMeasurement> allMeasurements) {
        Map<String, List<SignalMeasurement>> grouped = groupBySsid(allMeasurements);
        
        for (Map.Entry<String, List<SignalMeasurement>> entry : grouped.entrySet()) {
            String ssid = entry.getKey();
            LatLng suspectedLoc = SignalUtils.calculateWeightedCentroid(entry.getValue());
            
            // Add to ClusterManager instead of direct map markers
            WifiClusterItem item = new WifiClusterItem(
                    suspectedLoc.latitude, suspectedLoc.longitude,
                    "Suspected: " + ssid, ssid
            );
            mClusterManager.addItem(item);
        }
        
        signalInfoText.setText(String.format(Locale.getDefault(), "Viewing %d unique networks", grouped.size()));
    }

    private void showDetailedView(List<SignalMeasurement> allMeasurements) {
        List<SignalMeasurement> relevant = new ArrayList<>();
        for (SignalMeasurement m : allMeasurements) {
            if (m.getSsid().equals(selectedSsid)) relevant.add(m);
        }

        if (relevant.isEmpty()) {
            selectedSsid = null;
            showSummaryView(allMeasurements);
            return;
        }

        LatLng suspectedLoc = SignalUtils.calculateWeightedCentroid(relevant);

        // Add supporting measurement markers
        for (SignalMeasurement m : relevant) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(m.getLatitude(), m.getLongitude()))
                    .title(m.getSignalStrength() + " dBm")
                    .snippet(m.getSsid())
                    .icon(BitmapDescriptorFactory.defaultMarker(m.getHue())));
            if (marker != null) markers.add(marker);
            
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
        Marker mainMarker = mMap.addMarker(new MarkerOptions()
                .position(suspectedLoc)
                .title("Suspected: " + selectedSsid)
                .snippet(selectedSsid)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        if (mainMarker != null) {
            markers.add(mainMarker);
            mainMarker.showInfoWindow();
        }

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
    public boolean onMarkerClick(@NonNull Marker marker) {
        // This listener is only active in Detailed View.
        String ssid = marker.getSnippet();
        if (ssid != null && ssid.equals(selectedSsid)) {
            // Click on a measurement marker within the active detailed view.
            // Show its info window and consume the event to prevent the map click listener from resetting the view.
            marker.showInfoWindow();
            return true; // Consume the event.
        }

        // Fallback case: If a marker with a *different* SSID is clicked (which shouldn't happen in detailed view),
        // switch to that SSID's detailed view.
        if (ssid != null) {
            selectedSsid = ssid;
            refreshMarkersFromDatabase();
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
            return true;
        }

        // If it's a marker with no info, let the default behavior happen.
        return false;
    }

    @Override
    public boolean onClusterClick(Cluster<WifiClusterItem> cluster) {
        // Instead of zooming, show a dialog with the list of SSIDs in the cluster
        final List<String> ssids = new ArrayList<>();
        for (WifiClusterItem item : cluster.getItems()) {
            ssids.add(item.getSnippet());
        }

        final CharSequence[] items = ssids.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a WiFi Network from this cluster");
        builder.setItems(items, (dialog, which) -> {
            selectedSsid = (String) items[which];
            refreshMarkersFromDatabase();
            // We can also move the camera to the selected item\'s position, but cluster position is fine
            mMap.animateCamera(CameraUpdateFactory.newLatLng(cluster.getPosition()));
        });
        builder.show();
        
        return true;
    }

    @Override
    public boolean onClusterItemClick(WifiClusterItem item) {
        String ssid = item.getSnippet(); // Use snippet which holds the raw SSID
        if (ssid != null) {
            selectedSsid = ssid;
            refreshMarkersFromDatabase();
            mMap.animateCamera(CameraUpdateFactory.newLatLng(item.getPosition()));
            return true;
        }
        return false;
    }


    private void clearMapVisuals() {
        for (Marker m : markers) m.remove();
        markers.clear();
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
            // On Android 13+ request location + notification permissions
            requestPermissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            // On older versions, POST_NOTIFICATIONS is not a runtime permission
            requestPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void clearAllData() {
        selectedSsid = null;
        clearMapVisuals();
        databaseExecutor.execute(() -> {
            db.signalDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(this, "Database cleared", Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
        if (TrackingService.isRunning()) {
            startMapUpdates(); // Re-start updates if the service is running
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMapUpdates(); // Always stop updates when the app is paused
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove handler callbacks before shutting down executor to prevent IllegalStateException
        if (mapUpdateHandler != null && mapUpdateRunnable != null) {
            mapUpdateHandler.removeCallbacks(mapUpdateRunnable);
        }
        // Shutdown database executor
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
    }
}

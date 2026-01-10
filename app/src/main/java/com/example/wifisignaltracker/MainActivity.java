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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

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
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    // Weighted centroid algorithm constants
    private static final int SIGNAL_FILTER_THRESHOLD_DB = 25; // Filter signals weaker than max by this amount
    private static final double WEIGHT_OFFSET = 110.0; // Offset to ensure positive weights (min RSSI ~-110 dBm)
    private static final double WEIGHT_EXPONENT = 6.0; // Exponential weight to heavily favor strong signals

    private GoogleMap mMap;
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
                    startMapUpdates();
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
        mMap.setOnMarkerClickListener(this);
        
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
            startMapUpdates();
        }
    }

    private void startMapUpdates() {
        mapUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedSsid == null) {
                    refreshMarkersFromDatabase();
                }
                mapUpdateHandler.postDelayed(this, 5000);
            }
        };
        mapUpdateHandler.post(mapUpdateRunnable);
    }

    private void refreshMarkersFromDatabase() {
        databaseExecutor.execute(() -> {
            List<SignalMeasurement> allMeasurements = db.signalDao().getAllMeasurements();
            runOnUiThread(() -> {
                clearMapVisuals();
                
                if (selectedSsid == null) {
                    showSummaryView(allMeasurements);
                } else {
                    showDetailedView(allMeasurements);
                }
            });
        });
    }

    private void showSummaryView(List<SignalMeasurement> allMeasurements) {
        Map<String, List<SignalMeasurement>> grouped = groupBySsid(allMeasurements);
        
        for (Map.Entry<String, List<SignalMeasurement>> entry : grouped.entrySet()) {
            String ssid = entry.getKey();
            LatLng suspectedLoc = calculateWeightedCentroid(entry.getValue());
            
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(suspectedLoc)
                    .title("Suspected: " + ssid)
                    .snippet(ssid)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            if (marker != null) markers.add(marker);
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

        LatLng suspectedLoc = calculateWeightedCentroid(relevant);

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
        String ssid = marker.getSnippet();
        if (ssid != null) {
            if (ssid.equals(selectedSsid)) {
                return false;
            }
            selectedSsid = ssid;
            refreshMarkersFromDatabase();
            mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
            return true;
        }
        return false;
    }

    /**
     * Improved Weighted Centroid algorithm.
     * To prevent a large number of weak signals from overwhelming a few strong ones, 
     * we use a higher power for weighting and only consider measurements within 
     * a reasonable range of the strongest detected signal.
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        requestPermissionLauncher.launch(permissions.toArray(new String[0]));
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
        if (mMap != null) startMapUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapUpdateHandler.removeCallbacks(mapUpdateRunnable);
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
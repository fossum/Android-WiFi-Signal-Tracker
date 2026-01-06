package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity handles the UI, location tracking, and WiFi scanning logic.
 * It coordinates between the Google Map, the FusedLocationProvider, and the WifiManager.
 * Now integrated with Room Database for persistent storage.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "WiFiSignalTracker";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WIFI_SCAN_INTERVAL_MS = 5000;
    private static final int MIN_SIGNAL_STRENGTH_DBM = -90;

    private GoogleMap mMap;
    private WifiManager wifiManager;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    
    private Button startButton;
    private Button clearButton;
    private TextView signalInfoText;
    private TextView locationInfoText;
    
    private boolean isTracking = false;
    private Location currentLocation;
    
    // Database components
    private AppDatabase db;
    private ExecutorService databaseExecutor;
    
    private final List<Marker> markers = new ArrayList<>();
    
    private Handler wifiScanHandler;
    private Runnable wifiScanRunnable;
    private BroadcastReceiver wifiScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Database and Executor for background operations
        db = AppDatabase.getDatabase(this);
        databaseExecutor = Executors.newSingleThreadExecutor();

        // Initialize system services
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        wifiScanHandler = new Handler(Looper.getMainLooper());

        // Bind UI components
        startButton = findViewById(R.id.start_button);
        clearButton = findViewById(R.id.clear_button);
        signalInfoText = findViewById(R.id.signal_info_text);
        locationInfoText = findViewById(R.id.location_info_text);

        // Initialize button actions
        startButton.setOnClickListener(v -> toggleTracking());
        clearButton.setOnClickListener(v -> clearAllData());

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Handle WiFi scan results
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isTracking && currentLocation != null) {
                    processWifiScanResults();
                }
            }
        };

        // Handle location updates
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    updateLocationInfo();
                }
            }
        };

        // Periodic WiFi scan trigger
        wifiScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    try {
                        wifiManager.startScan();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start WiFi scan", e);
                    }
                    wifiScanHandler.postDelayed(this, WIFI_SCAN_INTERVAL_MS);
                }
            }
        };

        checkPermissions();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationUI();
            loadExistingMarkers();
        }
    }

    /**
     * Loads measurements from the database and displays them on the map.
     */
    private void loadExistingMarkers() {
        databaseExecutor.execute(() -> {
            List<SignalMeasurement> measurements = db.signalDao().getAllMeasurements();
            runOnUiThread(() -> {
                for (SignalMeasurement m : measurements) {
                    addMarkerToMap(m);
                }
            });
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationUI() {
        if (mMap != null) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
                }
            });
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocationUI();
                loadExistingMarkers();
            }
        }
    }

    private void toggleTracking() {
        if (isTracking) stopTracking(); else startTracking();
    }

    private void startTracking() {
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Please enable WiFi", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        isTracking = true;
        startButton.setText(R.string.stop_tracking);

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, 
                Looper.getMainLooper());

        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiScanHandler.post(wifiScanRunnable);

        Toast.makeText(this, "Recording to database...", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        startButton.setText(R.string.start_tracking);
        fusedLocationClient.removeLocationUpdates(locationCallback);
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (Exception ignored) {}
        wifiScanHandler.removeCallbacks(wifiScanRunnable);
    }

    /**
     * Processes WiFi scan results and saves them to the Room database.
     */
    @SuppressLint("MissingPermission")
    private void processWifiScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        List<SignalMeasurement> newMeasurements = new ArrayList<>();
        
        int strongestRssi = -127;
        String strongestSsid = "None";

        for (ScanResult result : results) {
            if (result.SSID == null || result.SSID.isEmpty() || result.level < MIN_SIGNAL_STRENGTH_DBM) {
                continue;
            }

            SignalMeasurement m = new SignalMeasurement(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    result.level,
                    result.SSID
            );
            newMeasurements.add(m);

            if (result.level > strongestRssi) {
                strongestRssi = result.level;
                strongestSsid = result.SSID;
            }
        }

        // Save to database on a background thread
        if (!newMeasurements.isEmpty()) {
            databaseExecutor.execute(() -> {
                db.signalDao().insertAll(newMeasurements);
                // Update UI on main thread
                runOnUiThread(() -> {
                    for (SignalMeasurement m : newMeasurements) {
                        addMarkerToMap(m);
                    }
                });
            });
        }

        updateSignalInfo(strongestRssi, strongestSsid, results.size());
    }

    private void updateSignalInfo(int rssi, String ssid, int totalFound) {
        String info = String.format(Locale.getDefault(), 
                "Strongest: %d dBm (%s)\nSaved %d networks", rssi, ssid, totalFound);
        signalInfoText.setText(info);
    }

    private void updateLocationInfo() {
        if (currentLocation != null) {
            locationInfoText.setText(String.format(Locale.getDefault(), 
                    "Location: %.6f, %.6f", currentLocation.getLatitude(), currentLocation.getLongitude()));
        }
    }

    private void addMarkerToMap(SignalMeasurement m) {
        if (mMap == null) return;
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(m.getLatitude(), m.getLongitude()))
                .title(m.getSsid() + ": " + m.getSignalStrength() + "dBm")
                .icon(BitmapDescriptorFactory.defaultMarker(m.getHue())));
        if (marker != null) markers.add(marker);
    }

    private void clearAllData() {
        if (mMap != null) mMap.clear();
        markers.clear();
        
        // Clear from database
        databaseExecutor.execute(() -> {
            db.signalDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Database cleared", Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTracking();
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
        }
    }
}
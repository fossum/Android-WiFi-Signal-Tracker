package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WIFI_SCAN_INTERVAL_MS = 2000; // Scan every 2 seconds

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
    private List<SignalMeasurement> measurements;
    private List<Marker> markers;
    
    private Handler wifiScanHandler;
    private Runnable wifiScanRunnable;
    
    private BroadcastReceiver wifiScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize components
        measurements = new ArrayList<>();
        markers = new ArrayList<>();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        wifiScanHandler = new Handler(Looper.getMainLooper());

        // Initialize UI components
        startButton = findViewById(R.id.start_button);
        clearButton = findViewById(R.id.clear_button);
        signalInfoText = findViewById(R.id.signal_info_text);
        locationInfoText = findViewById(R.id.location_info_text);

        // Set up button listeners
        startButton.setOnClickListener(v -> toggleTracking());
        clearButton.setOnClickListener(v -> clearAllMarkers());

        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Set up WiFi scan receiver
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isTracking && currentLocation != null) {
                    recordSignalMeasurement();
                }
            }
        };

        // Set up location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    updateLocationInfo();
                }
            }
        };

        // Set up WiFi scan runnable
        wifiScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    wifiManager.startScan();
                    wifiScanHandler.postDelayed(this, WIFI_SCAN_INTERVAL_MS);
                }
            }
        };

        // Check and request permissions
        checkPermissions();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        
        // Enable my location if permission granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            @SuppressLint("MissingPermission")
            boolean unused = mMap.setMyLocationEnabled(true);
            
            // Move camera to current location
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18));
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
                if (mMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                            == PackageManager.PERMISSION_GRANTED) {
                        @SuppressLint("MissingPermission")
                        boolean unused = mMap.setMyLocationEnabled(true);
                    }
                }
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        // Check WiFi is enabled
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, R.string.wifi_disabled, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        isTracking = true;
        startButton.setText(R.string.stop_tracking);

        // Start location updates
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, 
                Looper.getMainLooper());

        // Register WiFi scan receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // Start WiFi scanning
        wifiScanHandler.post(wifiScanRunnable);

        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        startButton.setText(R.string.start_tracking);

        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback);

        // Unregister WiFi scan receiver
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }

        // Stop WiFi scanning
        wifiScanHandler.removeCallbacks(wifiScanRunnable);

        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void recordSignalMeasurement() {
        if (currentLocation == null) {
            return;
        }

        // Get WiFi connection info
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int signalStrength = wifiInfo.getRssi(); // Signal strength in dBm
        String ssid = wifiInfo.getSSID();

        // Create measurement
        SignalMeasurement measurement = new SignalMeasurement(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                signalStrength,
                ssid
        );

        measurements.add(measurement);

        // Update UI
        updateSignalInfo(signalStrength, ssid);

        // Add marker to map
        addMarkerToMap(measurement);
    }

    private void updateSignalInfo(int signalStrength, String ssid) {
        String signalText = String.format("Signal: %d dBm (%s)", signalStrength, ssid);
        signalInfoText.setText(signalText);
    }

    private void updateLocationInfo() {
        if (currentLocation != null) {
            String locationText = String.format("Location: %.6f, %.6f",
                    currentLocation.getLatitude(), currentLocation.getLongitude());
            locationInfoText.setText(locationText);
        }
    }

    private void addMarkerToMap(SignalMeasurement measurement) {
        if (mMap == null) {
            return;
        }

        LatLng position = new LatLng(measurement.getLatitude(), measurement.getLongitude());
        
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(String.format("%d dBm", measurement.getSignalStrength()))
                .snippet(measurement.getSsid())
                .icon(BitmapDescriptorFactory.defaultMarker(measurement.getHue()));

        Marker marker = mMap.addMarker(markerOptions);
        if (marker != null) {
            markers.add(marker);
        }
    }

    private void clearAllMarkers() {
        // Remove all markers from map
        for (Marker marker : markers) {
            marker.remove();
        }
        markers.clear();
        measurements.clear();

        Toast.makeText(this, "All markers cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isTracking) {
            stopTracking();
        }
    }
}

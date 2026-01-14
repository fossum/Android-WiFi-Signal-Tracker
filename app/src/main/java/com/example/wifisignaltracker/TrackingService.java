package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground Service that handles location updates and WiFi scanning in the background.
 */
public class TrackingService extends Service {

    private static final String TAG = "TrackingService";
    private static final String CHANNEL_ID = "TrackingServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int DEFAULT_WIFI_SCAN_INTERVAL_MS = 10000; // 10 seconds default
    private static final int MIN_SIGNAL_STRENGTH_DBM = -90;
    
    public static final String EXTRA_SCAN_INTERVAL = "scan_interval_seconds";

    // Track service running state (alternative to deprecated getRunningServices)
    private static volatile boolean isRunning = false;
    
    private int wifiScanIntervalMs = DEFAULT_WIFI_SCAN_INTERVAL_MS;

    private WifiManager wifiManager;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    
    private AppDatabase db;
    private ExecutorService databaseExecutor;
    
    private Handler wifiScanHandler;
    private Runnable wifiScanRunnable;
    private BroadcastReceiver wifiScanReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        
        isRunning = true;
        db = AppDatabase.getDatabase(this);
        databaseExecutor = Executors.newSingleThreadExecutor();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        wifiScanHandler = new Handler(Looper.getMainLooper());

        setupLocationUpdates();
        setupWifiScanning();
    }

    private void setupLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                currentLocation = locationResult.getLastLocation();
            }
        };
    }

    private void setupWifiScanning() {
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (currentLocation != null) {
                    processWifiScanResults();
                }
            }
        };

        wifiScanRunnable = new Runnable() {
            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                try {
                    // Note: startScan() is deprecated and throttled on Android 10+ (API 29).
                    // The OS limits apps to 4 scans per 2-minute window.
                    // For production apps, consider using WifiManager.registerScanResultsCallback()
                    // or implementing exponential backoff to handle throttling.
                    wifiManager.startScan();
                } catch (Exception e) {
                    Log.e(TAG, "Scan failed", e);
                }
                wifiScanHandler.postDelayed(this, wifiScanIntervalMs);
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void processWifiScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        List<SignalMeasurement> newMeasurements = new ArrayList<>();

        for (ScanResult result : results) {
            if (result.SSID == null || result.SSID.isEmpty() || result.level < MIN_SIGNAL_STRENGTH_DBM) continue;
            
            SignalMeasurement m = new SignalMeasurement(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    result.level,
                    result.SSID
            );
            newMeasurements.add(m);
        }

        if (!newMeasurements.isEmpty()) {
            databaseExecutor.execute(() -> db.signalDao().insertAll(newMeasurements));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Get the scan interval from the intent if provided
        if (intent != null && intent.hasExtra(EXTRA_SCAN_INTERVAL)) {
            int scanIntervalSeconds = intent.getIntExtra(EXTRA_SCAN_INTERVAL, 10);
            wifiScanIntervalMs = scanIntervalSeconds * 1000;
            Log.d(TAG, "Using scan interval: " + scanIntervalSeconds + " seconds");
        }
        
        createNotificationChannel();
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiFi Signal Tracker")
                .setContentText("Tracking WiFi signals in background...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        startTracking();
        
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        // Check location permission before starting updates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted, cannot start tracking");
            stopSelf();
            return;
        }
        
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000).build();
        
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        ContextCompat.registerReceiver(this, wifiScanReceiver, 
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
        
        wifiScanHandler.post(wifiScanRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        wifiScanHandler.removeCallbacks(wifiScanRunnable);
        
        // Unregister receiver with proper error handling
        if (wifiScanReceiver != null) {
            try {
                unregisterReceiver(wifiScanReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver was not registered, ignore
            }
        }
        
        // Shutdown database executor
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
    }
    
    /**
     * Check if the tracking service is currently running.
     * @return true if service is running, false otherwise
     */
    public static boolean isRunning() {
        return isRunning;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Tracking Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
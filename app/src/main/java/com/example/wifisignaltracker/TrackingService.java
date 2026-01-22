package com.example.wifisignaltracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
@RequiresApi(api = Build.VERSION_CODES.S) // Set minimum API level for the entire service
public class TrackingService extends Service {

    private static final String TAG = "TrackingService";
    public static final String ACTION_STATE_CHANGED = "com.example.wifisignaltracker.TRACKING_STATE_CHANGED";
    public static final String EXTRA_IS_RUNNING = "is_running";

    private static final String CHANNEL_ID = "TrackingServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int WIFI_SCAN_INTERVAL_MS = 10000; // 10 seconds
    private static final int MIN_SIGNAL_STRENGTH_DBM = -90;

    private static volatile boolean isRunning = false;

    private WifiManager wifiManager;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Location currentLocation;

    private AppDatabase db;
    private ExecutorService databaseExecutor;

    private Handler wifiScanHandler;
    private Runnable wifiScanRunnable;
    private WifiManager.ScanResultsCallback scanResultsCallback;

    @Override
    public void onCreate() {
        super.onCreate();

        isRunning = true;
        sendStateBroadcast();

        db = AppDatabase.getDatabase(this);
        databaseExecutor = Executors.newSingleThreadExecutor();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
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
        scanResultsCallback = new WifiManager.ScanResultsCallback() {
            @Override
            public void onScanResultsAvailable() {
                if (currentLocation != null) {
                    try {
                        @SuppressLint("MissingPermission")
                        List<ScanResult> results = wifiManager.getScanResults();
                        processWifiScanResults(results);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permission error getting scan results", e);
                    }
                }
            }
        };

        wifiScanRunnable = () -> {
            try {
                boolean scanStarted = wifiManager.startScan();
                if (!scanStarted) {
                    Log.w(TAG, "Wi-Fi scan failed to start.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during startScan()", e);
            }
            wifiScanHandler.postDelayed(wifiScanRunnable, WIFI_SCAN_INTERVAL_MS);
        };
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation") // Using deprecated SSID and level to resolve persistent build errors.
    private void processWifiScanResults(List<ScanResult> results) {
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
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot start tracking.");
            stopSelf();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000).build();

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        wifiManager.registerScanResultsCallback(ContextCompat.getMainExecutor(this), scanResultsCallback);
        wifiScanHandler.post(wifiScanRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        sendStateBroadcast();

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        wifiScanHandler.removeCallbacks(wifiScanRunnable);
        wifiManager.unregisterScanResultsCallback(scanResultsCallback);

        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }

    private void sendStateBroadcast() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_IS_RUNNING, isRunning);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Tracking Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
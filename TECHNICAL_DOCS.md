# WiFi Signal Tracker - Technical Documentation

## Overview

This Android application provides real-time WiFi signal strength tracking with GPS location mapping. It helps users identify areas with strong or weak WiFi signals by visualizing measurements on Google Maps.

## Architecture

### Components

1. **MainActivity.java**
   - Main entry point and UI controller
   - Manages WiFi scanning and location tracking
   - Handles map interactions and marker placement
   - Coordinates between WiFi, GPS, and Map services

2. **SignalMeasurement.java**
   - Data model for storing signal measurements
   - Contains: latitude, longitude, signal strength (dBm), SSID, timestamp
   - Provides color mapping for signal strength visualization

### Key Features

#### WiFi Signal Tracking
- Scans WiFi networks every 2 seconds when tracking is active
- Captures signal strength in dBm (Received Signal Strength Indicator)
- Records the SSID of the connected network
- Uses Android's WifiManager for network information

#### GPS Location Tracking
- Uses Google Play Services FusedLocationProviderClient
- High-accuracy location updates every 1 second
- Minimum update interval of 500ms for smooth tracking
- Updates user's position on the map in real-time

#### Map Visualization
- Google Maps integration for visual representation
- Color-coded markers based on signal strength:
  - **Green (Excellent)**: > -50 dBm
  - **Yellow (Good)**: -50 to -60 dBm
  - **Orange (Fair)**: -60 to -70 dBm
  - **Red (Poor)**: < -70 dBm
- Markers show signal strength in dBm and network SSID
- Camera automatically moves to current location on startup

## Signal Strength Interpretation

WiFi signal strength is measured in dBm (decibels relative to one milliwatt):

| dBm Range | Quality | Description |
|-----------|---------|-------------|
| -30 to -50 | Excellent | Maximum signal, very close to access point |
| -50 to -60 | Good | Strong signal, good for all applications |
| -60 to -70 | Fair | Acceptable signal, may affect streaming |
| -70 to -80 | Poor | Weak signal, basic connectivity only |
| < -80 | Very Poor | Minimal or no connectivity |

## Permissions

The app requires several runtime permissions:

1. **ACCESS_FINE_LOCATION** - Required for:
   - GPS location tracking
   - WiFi scanning (Android 6.0+)
   
2. **ACCESS_COARSE_LOCATION** - Backup for location access

3. **ACCESS_WIFI_STATE** - Read WiFi connection information

4. **CHANGE_WIFI_STATE** - Initiate WiFi scans

5. **INTERNET** - Load Google Maps tiles

## Usage Flow

1. User launches app → Requests location permissions
2. Map loads showing current location
3. User taps "Start Tracking"
4. App begins:
   - WiFi scans every 2 seconds
   - Location updates every 1 second
   - Recording measurements when both available
5. Each measurement creates a color-coded marker
6. User can:
   - View real-time signal strength in UI
   - See current GPS coordinates
   - Tap markers to see details
   - Clear all markers to start fresh
   - Stop tracking when done

## Implementation Details

### Scan Mechanism
```java
// WiFi scanning triggered every 2 seconds
wifiScanHandler.postDelayed(wifiScanRunnable, WIFI_SCAN_INTERVAL_MS);

// On scan completion, record measurement
wifiManager.startScan();
// BroadcastReceiver receives SCAN_RESULTS_AVAILABLE_ACTION
// Calls recordSignalMeasurement()
```

### Data Collection
```java
WifiInfo wifiInfo = wifiManager.getConnectionInfo();
int signalStrength = wifiInfo.getRssi(); // dBm
String ssid = wifiInfo.getSSID();
```

### Marker Placement
```java
MarkerOptions markerOptions = new MarkerOptions()
    .position(new LatLng(lat, lng))
    .title(String.format("%d dBm", signalStrength))
    .snippet(ssid)
    .icon(BitmapDescriptorFactory.defaultMarker(hue));
```

## Building the App

### Requirements
- Android Studio Arctic Fox or later
- Android SDK 24 (Android 7.0) or higher
- Google Maps API key

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Configuration

### Google Maps API Key
1. Get key from Google Cloud Console
2. Enable "Maps SDK for Android"
3. Add to `app/build.gradle`:
```gradle
manifestPlaceholders = [MAPS_API_KEY: "YOUR_KEY"]
```

## Troubleshooting

### Common Issues

**WiFi scans not working**
- Ensure WiFi is enabled
- Check location permission is granted
- On Android 9+, location services must be ON

**No location updates**
- Verify GPS is enabled
- Check location permission
- Ensure device has GPS hardware

**Map not loading**
- Verify API key is correct
- Check internet connection
- Ensure Maps SDK is enabled in Google Cloud Console

**Markers not appearing**
- Confirm both WiFi and location are working
- Check if tracking is started
- Zoom in on map to see markers

## Future Enhancements

Potential improvements:
- Save tracking sessions to database
- Export data to CSV/GPX
- Heatmap visualization
- Multiple WiFi network tracking
- Signal strength graphs over time
- Offline map support
- Custom marker clustering

## Performance Considerations

- Battery usage: Continuous GPS + WiFi scanning = moderate drain
- Memory: ~50 bytes per measurement, 1800 measurements/hour = ~90KB/hour
- Network: Google Maps requires internet; cached tiles reduce usage
- CPU: Minimal, mostly idle between scans

## Privacy & Security

- Location data is only stored in memory
- No data transmitted to external servers
- App does not store WiFi passwords
- Clear markers removes all collected data

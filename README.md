# Android WiFi Signal Tracker

An Android application that tracks WiFi signal strength as you walk around and displays the measurements on a map. This app helps you locate the best WiFi signal areas by visualizing signal strength at different locations.

## Features

- **Real-time WiFi Signal Tracking**: Continuously monitors WiFi signal strength (RSSI in dBm)
- **GPS Location Tracking**: Records your location as you move
- **Map Visualization**: Displays signal measurements on Google Maps with color-coded markers
- **Signal Strength Indicators**:
  - Green: Excellent signal (> -50 dBm)
  - Yellow: Good signal (-50 to -60 dBm)
  - Orange: Fair signal (-60 to -70 dBm)
  - Red: Poor signal (< -70 dBm)
- **Easy Controls**: Start/stop tracking and clear markers with simple buttons
- **Real-time Updates**: Signal strength and location information updated in real-time

## Requirements

- Android device running Android 7.0 (API level 24) or higher
- WiFi capability
- GPS/Location services
- Google Maps API key

## Setup

1. Clone this repository
2. Open the project in Android Studio
3. Obtain a Google Maps API key from the [Google Cloud Console](https://console.cloud.google.com/)
   - Enable "Maps SDK for Android" API
4. Add your API key to `local.properties` file in the root directory:
   ```properties
   MAPS_API_KEY=your_actual_api_key_here
   ```
5. Build and run the app on your Android device or emulator

## Permissions

The app requires the following permissions:
- `ACCESS_WIFI_STATE`: To read WiFi signal information
- `CHANGE_WIFI_STATE`: To initiate WiFi scans
- `ACCESS_FINE_LOCATION`: For GPS location tracking
- `ACCESS_COARSE_LOCATION`: For approximate location
- `INTERNET`: For Google Maps

## Usage

1. Launch the app
2. Grant location and WiFi permissions when prompted
3. Tap "Start Tracking" to begin recording WiFi signal measurements
4. Walk around the area you want to map
5. Signal measurements will appear as colored markers on the map
6. Tap "Stop Tracking" when done
7. Use "Clear Markers" to remove all measurements and start fresh

## How It Works

The app combines WiFi scanning with GPS location tracking:

1. When tracking is active, the app scans WiFi networks every 2 seconds
2. For each scan, it records:
   - Current GPS coordinates
   - WiFi signal strength (RSSI)
   - Network SSID
   - Timestamp
3. Each measurement is displayed as a marker on the map
4. Marker colors indicate signal strength quality

## Building

Build the app using Gradle:

```bash
./gradlew assembleDebug
```

## CI/CD and Secrets

This project uses GitHub Actions for Continuous Integration.

### Secrets Configuration
To run the CI pipeline successfully, you must configure the following **GitHub Actions Secrets** in your repository settings:

*   `MAPS_API_KEY`: Your Google Maps API key (required for building the app).

### Workflows
*   **Pull Requests**: Runs Unit Tests, Lint, and builds a Debug APK.
*   **Main Branch**: Runs Unit Tests, Lint, and builds a Release APK (signed with debug keystore for installability).

## License

This project is open source and available for educational purposes.
# WiFi Signal Tracker - User Interface Guide

## Main Screen Layout

```
┌─────────────────────────────────────────┐
│  WiFi Signal Tracker          [⚙]  [☰] │
├─────────────────────────────────────────┤
│                                         │
│                                         │
│           [Google Maps View]            │
│                                         │
│     🔴 ← Poor signal marker            │
│          🟠 ← Fair signal marker        │
│               🟡 ← Good signal marker   │
│                    🟢 ← Excellent       │
│                                         │
│          📍 ← Your current location     │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  Signal: -65 dBm (MyWiFiNetwork)       │
│  Location: 37.422408, -122.084068       │
│                                         │
│  ┌───────────────┐  ┌─────────────────┐│
│  │Start Tracking │  │  Clear Markers  ││
│  └───────────────┘  └─────────────────┘│
└─────────────────────────────────────────┘
```

## Screen Elements

### Top Bar
- **App Title**: "WiFi Signal Tracker"
- **Settings Icon**: (Future feature)
- **Menu Icon**: (Future feature)

### Map Area (Main View)
- **Google Maps**: Interactive map showing your location
- **Signal Markers**: Color-coded pins showing WiFi measurements
- **Current Location**: Blue dot showing where you are
- **Zoom Controls**: Standard Google Maps zoom in/out

### Information Panel (Bottom)
- **Signal Info**: Current WiFi signal strength and network name
  - Format: "Signal: -XX dBm (NetworkName)"
  - Updates in real-time when tracking

- **Location Info**: Current GPS coordinates
  - Format: "Location: latitude, longitude"
  - Updates as you move

### Control Buttons
1. **Start Tracking / Stop Tracking**
   - Green button on the left
   - Text changes based on state
   - Starts/stops WiFi signal recording

2. **Clear Markers**
   - Gray button on the right
   - Removes all markers from map
   - Clears measurement history

## User Interactions

### Starting the App
1. App opens to map view
2. Requests location permissions (first time)
3. Map centers on your current location
4. Info panel shows "Signal: -- dBm" (no data yet)

### Starting Tracking
1. Tap "Start Tracking" button
2. Button text changes to "Stop Tracking"
3. WiFi and GPS scanning begins
4. Signal info updates every 2 seconds
5. Markers appear as you move around

### Viewing Signal Information
1. Tap any marker on the map
2. Info window appears showing:
   - Signal strength (dBm)
   - Network SSID
3. Tap elsewhere to close info window

### Stopping Tracking
1. Tap "Stop Tracking" button
2. Button text changes back to "Start Tracking"
3. Scanning stops
4. Existing markers remain visible
5. No new measurements recorded

### Clearing Data
1. Tap "Clear Markers" button
2. All markers disappear from map
3. Map view remains at current position
4. Can start fresh tracking session

## Visual Feedback

### Signal Strength Colors
The app uses a traffic light color scheme:

```
Excellent Signal:  🟢 Green   (Hue: 120°)  -30 to -50 dBm
Good Signal:       🟡 Yellow  (Hue: 60°)   -50 to -60 dBm
Fair Signal:       🟠 Orange  (Hue: 30°)   -60 to -70 dBm
Poor Signal:       🔴 Red     (Hue: 0°)    -70 to -90 dBm
```

### Map Markers
Each marker represents a single WiFi measurement at a specific location:
- **Pin Color**: Indicates signal strength
- **Title**: Shows exact dBm value (e.g., "-65 dBm")
- **Snippet**: Shows network name (e.g., "MyHomeWiFi")

## Example Use Case

**Scenario**: Finding the best WiFi spot in a large office

1. **Start**: Open app at one end of office
2. **Begin Tracking**: Tap "Start Tracking"
3. **Walk Around**: Move through different areas
   - Conference room: Sees 🔴 red markers (-75 dBm)
   - Near router: Sees 🟢 green markers (-45 dBm)
   - Break room: Sees 🟡 yellow markers (-58 dBm)
4. **Analyze**: View map to identify best signal areas
5. **Stop**: Tap "Stop Tracking" when done
6. **Share**: Take screenshot to show IT team

## Tips for Best Results

### For Accurate Measurements
- ✅ Walk slowly to get more data points
- ✅ Keep app in foreground for best tracking
- ✅ Ensure WiFi is connected to target network
- ✅ Enable high accuracy location mode
- ❌ Don't run in battery saver mode
- ❌ Avoid areas with poor GPS signal (indoors)

### Reading the Map
- **Clustered red markers**: WiFi dead zone, avoid this area
- **Green marker trail**: Strong signal path, good coverage
- **Color transition**: Signal strength boundary, edge of coverage
- **Sparse markers**: May need to walk slower for more data

### Battery Life
- Tracking uses ~10-15% battery per hour
- Stop tracking when not actively mapping
- Clear markers periodically to free memory

## Permissions Dialog

When you first open the app, you'll see:

```
┌─────────────────────────────────────────┐
│  WiFi Signal Tracker                    │
├─────────────────────────────────────────┤
│                                         │
│  This app needs location permission to: │
│                                         │
│  • Track your position on the map      │
│  • Scan for WiFi networks              │
│  • Record signal strength at locations │
│                                         │
│  ┌─────────────┐      ┌──────────────┐ │
│  │   Allow     │      │  Don't Allow │ │
│  └─────────────┘      └──────────────┘ │
└─────────────────────────────────────────┘
```

**Important**: The app cannot function without location permission as WiFi scanning requires it on Android 6.0+.

## Error Messages

### "WiFi is disabled. Please enable WiFi to track signal strength."
- **Cause**: WiFi is turned off
- **Solution**: Enable WiFi in device settings

### "Location permission is required to track WiFi signals"
- **Cause**: Permission denied
- **Solution**: Grant location permission in app settings

### Map shows gray tiles
- **Cause**: No internet connection or invalid API key
- **Solution**: Check connection and verify Maps API key

## Data Display Format

### Signal Strength
- Format: `Signal: -XX dBm (NetworkName)`
- Example: `Signal: -65 dBm (Office_WiFi_5G)`
- Range: -30 (excellent) to -90 (very poor)

### Location
- Format: `Location: XX.XXXXXX, -XX.XXXXXX`
- Example: `Location: 37.422408, -122.084068`
- Precision: 6 decimal places (~10cm accuracy)

## Accessibility

- Large touch targets for buttons (48dp minimum)
- High contrast text and colors
- Screen reader compatible (content descriptions provided)
- Works in both portrait and landscape orientations

package com.example.wifisignaltracker;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

public class WifiClusterItem implements ClusterItem {
    private final LatLng position;
    private final String title;
    private final String snippet;
    private final float hue;

    public WifiClusterItem(double lat, double lng, String title, String snippet, float hue) {
        this.position = new LatLng(lat, lng);
        this.title = title;
        this.snippet = snippet;
        this.hue = hue;
    }

    public WifiClusterItem(LatLng position, String title, String snippet, float hue) {
        this.position = position;
        this.title = title;
        this.snippet = snippet;
        this.hue = hue;
    }

    @Override
    public LatLng getPosition() {
        return position;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getSnippet() {
        return snippet;
    }

    public float getHue() {
        return hue;
    }
}

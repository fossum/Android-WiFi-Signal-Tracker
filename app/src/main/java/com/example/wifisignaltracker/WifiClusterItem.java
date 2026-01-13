package com.example.wifisignaltracker;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

public class WifiClusterItem implements ClusterItem {
    private final LatLng position;
    private final String title;
    private final String snippet;

    public WifiClusterItem(double lat, double lng, String title, String snippet) {
        this.position = new LatLng(lat, lng);
        this.title = title;
        this.snippet = snippet;
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

    @Override
    public Float getZIndex() {
        return 0.0f;
    }
}

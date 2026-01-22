package com.example.wifisignaltracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class JsonUtil {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static String toJson(List<SignalMeasurement> measurements) {
        Type listType = new TypeToken<List<SignalMeasurement>>() {}.getType();
        return gson.toJson(measurements, listType);
    }

    public static List<SignalMeasurement> fromJson(String json) {
        Type listType = new TypeToken<List<SignalMeasurement>>() {}.getType();
        return gson.fromJson(json, listType);
    }
}

package fr.inria.diverse.model;

import com.google.gson.annotations.SerializedName;

public enum EventType {
    @SerializedName("FIXED")
    FIXED,
    @SerializedName("INTRODUCED")
    INTRODUCED,
    @SerializedName("LAST_AFFECTED")
    LAST_AFFECTED,
    @SerializedName("LIMIT")
    LIMIT;   
}
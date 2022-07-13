package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class TimerFiredEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("name")
    public String name;

}

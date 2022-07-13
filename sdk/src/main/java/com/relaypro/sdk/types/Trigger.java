package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class Trigger {
    @SerializedName("type")
    public String type;

    @SerializedName("args")
    public Map<String, Object> args;
}

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class InteractionLifecycleEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("type")
    public String type;

    @SerializedName("reason")
    public String reason;

    @SerializedName("source_uri")
    public String sourceUri;

}

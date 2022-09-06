// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class ButtonEvent {
    @SerializedName("_type")
    String _type;
    
    @SerializedName("source_uri")
    public String sourceUri;

    @SerializedName("button")
    public String button;

    @SerializedName("taps")
    public String taps;
}

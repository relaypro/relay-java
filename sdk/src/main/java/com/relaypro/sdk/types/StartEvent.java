// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class StartEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("trigger")
    public Trigger trigger;
}

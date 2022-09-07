// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class SmsEvent {
    @SerializedName("id")
    String id;

    @SerializedName("event")
    String event;
}

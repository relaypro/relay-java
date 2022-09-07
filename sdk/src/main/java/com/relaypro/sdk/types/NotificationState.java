// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class NotificationState {
    @SerializedName("_type")
    String _type;

    @SerializedName("acknowledged")
    public String[] acknowledged;

    @SerializedName("created")
    public String[] created;

    @SerializedName("cancelled")
    public String[] cancelled;

    @SerializedName("timed_out")
    public String[] timedOut;

}

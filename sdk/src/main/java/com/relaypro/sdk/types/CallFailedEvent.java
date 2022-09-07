// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class CallFailedEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("call_id")
    public String callId;

    @SerializedName("direction")
    public String direction;

    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("device_name")
    public String deviceName;

    @SerializedName("uri")
    public String uri;

    @SerializedName("onnet")
    public String onnet;

    @SerializedName("reason")
    public String reason;

    @SerializedName("start_time_epoch")
    public String startTimeEpoch;

    @SerializedName("connect_time_epoch")
    public String connectTimeEpoch;

    @SerializedName("end_time_epoch")
    public String endTimeEpoch;
}

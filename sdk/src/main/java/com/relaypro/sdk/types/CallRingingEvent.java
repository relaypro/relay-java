package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class CallRingingEvent {
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

    @SerializedName("start_time_epoch")
    public String startTimeEpoch;
}

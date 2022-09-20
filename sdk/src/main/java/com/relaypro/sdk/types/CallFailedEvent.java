// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * A call attempt has failed to become connected.
 */
public class CallFailedEvent {
    @SerializedName("_type")
    String _type;

    /**
     * The ID of this new call.
     */
    @SerializedName("call_id")
    public String callId;

    /**
     * Call direction relative to this device: inbound or outbound.
     */
    @SerializedName("direction")
    public String direction;

    /**
     * The ID of the other device.
     */
    @SerializedName("device_id")
    public String deviceId;

    /**
     * The name of the other device.
     */
    @SerializedName("device_name")
    public String deviceName;

    /**
     * A SIP-style URI of the other device. Not currently supported.
     */
    @SerializedName("uri")
    public String uri;

    /**
     * Whether or not the other party is a Relay-hosted device. "True" or "False".
     */
    @SerializedName("onnet")
    public String onnet;

    /**
     * The reason discovered for failure of this call.
     */
    @SerializedName("reason")
    public String reason;

    /**
     * A timestamp of when the call was requested, in epoch seconds.
     */
    @SerializedName("start_time_epoch")
    public String startTimeEpoch;

    /**
     * A timestamp of when the call was connected, in epoch seconds.
     */
    @SerializedName("connect_time_epoch")
    public String connectTimeEpoch;

    /**
     * A timestamp of when the call was disconnected, in epoch seconds.
     */
    @SerializedName("end_time_epoch")
    public String endTimeEpoch;
}

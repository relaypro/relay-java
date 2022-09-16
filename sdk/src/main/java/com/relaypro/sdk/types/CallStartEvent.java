// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * There is a request to make an outbound call.
 */
public class CallStartEvent {
    @SerializedName("_type")
    String _type;

    /**
     * The URI of the device to call.
     */
    @SerializedName("uri")
    public String uri;

}

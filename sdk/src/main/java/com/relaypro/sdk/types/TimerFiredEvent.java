// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * A named timer has fired.
 */
public class TimerFiredEvent {
    @SerializedName("_type")
    String _type;

    /**
     * Name of the timer.
     */
    @SerializedName("name")
    public String name;

}

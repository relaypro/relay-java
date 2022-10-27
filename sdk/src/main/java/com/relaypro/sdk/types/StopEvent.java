// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * Your workflow has stopped, which might be due to normal completion after
 * you call terminate() or from an abnormal completion error.
 */
public class StopEvent {
    /**
     * The reason for the workflow stopping.
     */
    @SerializedName("reason")
    public String reason;
    
}

// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * Includes information on the state of the notification event.
 */
public class NotificationState {
    @SerializedName("_type")
    String _type;

    /**
     * List of devices that have acknowledged the alert.
     */
    @SerializedName("acknowledged")
    public String[] acknowledged;

    /**
     * List of devices on which the alert was created.
     */
    @SerializedName("created")
    public String[] created;

    /**
     * List of devices where the alert has been cancelled.
     */
    @SerializedName("cancelled")
    public String[] cancelled;

    /**
     * List of devices on which the alert timed out
     */
    @SerializedName("timed_out")
    public String[] timedOut;

}

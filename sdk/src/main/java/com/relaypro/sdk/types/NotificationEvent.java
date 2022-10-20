// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * A device has acknowledged an alert that was sent out to a group of devices.
 */
public class NotificationEvent {
    @SerializedName("_type")
    String _type;

    /**
     * The device that was listed as the originator of the alert.
     */
    @SerializedName("source_uri")
    public String sourceUri;

    /**
     * The type of notification event, such as "ack_event".
     */
    @SerializedName("event")
    public String event;

    /**
     * The name of the alert.
     */
    @SerializedName("name")
    public String name;

    /**
     * Contains information regarding the state of the notification, such as
     * a list of devices who have acknowledged the alert, a list of devices on which
     * the alert has been created, and more. See the NotificationState class for more details.
     */
    @SerializedName("notification_state")
    public NotificationState notificationState;
}

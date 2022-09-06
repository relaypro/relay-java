// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class NotificationEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("source_uri")
    public String sourceUri;

    @SerializedName("event")
    public String event;

    @SerializedName("name")
    public String name;

    @SerializedName("notification_state")
    public NotificationState notificationState;
}

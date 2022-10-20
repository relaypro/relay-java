// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * Inbox messages are being played on the device.
 */
public class PlayInboxMessagesEvent {
    /**
     * Either "error" or "complete".
     */
    @SerializedName("action")
    String action;
}

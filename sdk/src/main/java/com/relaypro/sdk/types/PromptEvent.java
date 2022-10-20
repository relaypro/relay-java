// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * When text-to-speech is being streamed to a Relay device, this event will mark
 * the beginning and end of that stream delivery.
 */
public class PromptEvent {
    /**
     * ID of the prompt event.
     */
    @SerializedName("id")
    String id;

    /**
     * Can be either "started", "stopped", or "failed".
     */
    @SerializedName("type")
    String type;

}

// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * You have spoken into the device by holding down the action button.  Typically
 * seen when the listen() function is happening on a device.
 */
public class SpeechEvent {
    /**
     * If a listen() is happening on the device, the request_id corresponds
     * to the ID of the listen request.
     */
    @SerializedName("request_id")
    String request_id;

    /**
     * The text that was spoken into the device.
     */
    @SerializedName("text")
    String text;

    @SerializedName("audio")
    String audio;

    /**
     * The language of the text that was spoken into the device.
     */
    @SerializedName("lang")
    String lang;
}

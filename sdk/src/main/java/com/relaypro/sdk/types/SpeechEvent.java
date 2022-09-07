// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class SpeechEvent {
    @SerializedName("request_id")
    String request_id;

    @SerializedName("text")
    String text;

    @SerializedName("audio")
    String audio;

    @SerializedName("lang")
    String lang;
}

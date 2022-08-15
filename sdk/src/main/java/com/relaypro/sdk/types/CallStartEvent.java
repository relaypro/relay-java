package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class CallStartEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("uri")
    public String uri;

}

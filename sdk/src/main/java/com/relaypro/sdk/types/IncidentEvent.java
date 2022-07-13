package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

public class IncidentEvent {
    @SerializedName("_type")
    String _type;

    @SerializedName("type")
    public String type;

    @SerializedName("incident_id")
    public String incidentId;

    @SerializedName("reason")
    public String reason;

}

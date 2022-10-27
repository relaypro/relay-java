// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * An incident has been resolved.
 */
public class IncidentEvent {
    @SerializedName("_type")
    String _type;

    /**
     * Can be either "resolved" or "cancelled".
     */
    @SerializedName("type")
    public String type;

    /**
     * The ID of the incident.
     */
    @SerializedName("incident_id")
    public String incidentId;

    /**
     * The reason for the incident's resolution.
     */
    @SerializedName("reason")
    public String reason;

}

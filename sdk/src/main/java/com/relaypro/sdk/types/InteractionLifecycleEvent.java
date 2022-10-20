// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * An interaction lifecycle event has occurred.  This could indicate that an
 * interaction has started, resumed, been suspended, ended, or failed.
 */
public class InteractionLifecycleEvent {
    @SerializedName("_type")
    String _type;

    /**
     * Can be either "started", "resumed", "suspended", "ended" or "failed".
     */
    @SerializedName("type")
    public String type;

    /**
     * Only set if type is "failed".  Reason for failure.
     */
    @SerializedName("reason")
    public String reason;

    /**
     * The interaction URN.
     */
    @SerializedName("source_uri")
    public String sourceUri;

    /**
     * Returns whether the InteractionLifecycleEvent has a type of "started".
     * @return true if the type is "started", false otherwise.
     */
    public boolean isTypeStarted() {
        return "started".equals(this.type);
    }

    /**
     * Returns whether the InteractionLifecycleEvent has a type of "ended".
     * @return true if the type is "ended", false otherwise.
     */
    public boolean isTypeEnded() {
        return "ended".equals(this.type);
    }
}

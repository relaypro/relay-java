// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

/**
 * Your workflow has been triggered.
 */
public class StartEvent {
    @SerializedName("_type")
    String _type;

    /**
     * The type of trigger that started your workflow.
     */
    @SerializedName("trigger")
    public Trigger trigger;
}

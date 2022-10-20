// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * The different types of triggers that can start a workflow.
 */
public class Trigger {
    @SerializedName("type")
    public String type;

    @SerializedName("args")
    public Map<String, Object> args;
}

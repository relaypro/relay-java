// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import com.google.gson.annotations.SerializedName;

 /** 
 * A button has been pressed on your device during a running workflow.  This event occurs on a single, double or triple
 * tap of the action button or a tap of the assistant button.  Note this is separate from a button
 * trigger.
 */
public class ButtonEvent {
    @SerializedName("_type")
    String _type;
    
    /**
     * The URN of the device whose button was pressed.
     */
    @SerializedName("source_uri")
    public String sourceUri;

    /**
     * Which button was pressed.
     */
    @SerializedName("button")
    public String button;

    /**
     * The number of times the button was tapped.
     */
    @SerializedName("taps")
    public String taps;
}

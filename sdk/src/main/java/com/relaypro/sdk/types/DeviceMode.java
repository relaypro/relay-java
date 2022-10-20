// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * The different modes the device can be in, including panic and alarm.
 */
public enum DeviceMode {
    
    Panic("panic"), 
    Alarm("alarm"), 
    None("none");

    private final String value;
    public String value() {
        return value;
    }

    DeviceMode(String value) {
        this.value = value;
    }
    
}

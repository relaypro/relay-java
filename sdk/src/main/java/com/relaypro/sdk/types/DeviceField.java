// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * Contains information on the device.
 */
public enum DeviceField {
    
    Label("label"), 
    LocationEnabled("location_enabled"),
    Channel("channel");
    
    private final String value;
    public String value() {
        return value;
    }

    DeviceField(String value) {
        this.value = value;
    }
}

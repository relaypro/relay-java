// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

public enum DeviceField {
    
    Label("label"), 
    LocationEnabled("location_enabled");
    
    private final String value;
    public String value() {
        return value;
    }

    DeviceField(String value) {
        this.value = value;
    }
}

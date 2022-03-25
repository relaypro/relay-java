package com.relaypro.sdk.types;

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

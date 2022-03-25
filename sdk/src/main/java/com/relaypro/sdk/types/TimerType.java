package com.relaypro.sdk.types;

public enum TimerType {
    TIMEOUT("timeout"),
    INTERVAL("interval");
    
    private final String value;
    public String value() {
        return value;
    }
    
    TimerType(String value) {
        this.value = value;
    }
}

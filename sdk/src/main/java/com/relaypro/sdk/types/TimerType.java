// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * Type of timer on the device.  Can be timeout or interval timer type.
 */
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

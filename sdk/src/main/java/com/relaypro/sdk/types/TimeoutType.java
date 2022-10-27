// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * The timeout type for a timer.  Can be either milliseconds, seconds, minutes, or hours.
 */
public enum TimeoutType {
    MS("ms"),
    SECS("secs"),
    MINS("mins"),
    HRS("hrs");
    
    private final String value;
    public String value() {
        return value;
    }
    
    TimeoutType(String value) {
        this.value = value;
    }
}

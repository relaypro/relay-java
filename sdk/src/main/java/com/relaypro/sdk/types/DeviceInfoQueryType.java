// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * Different information you can retrieve on the device through the Relay API calls.
 */
public enum DeviceInfoQueryType {

    Name("name"), 
    Id("id"), 
    Address("address"), 
    LatLong("latlong"), 
    IndoorLocation("indoor_location"), 
    Battery("battery"), 
    Type("type"), 
    Username("username"), 
    LocationEnabled("location_enabled"); 

    private final String value;
    public String value() {
        return value;
    }

    DeviceInfoQueryType(String value) {
        this.value = value;
    }
    
}

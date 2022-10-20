// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * Fields that are given values from the server after a 
 * device info query.
 */
public class DeviceInfoResponse {
    
    public String name;
    public String id;
    public String address;
    public double[] latlong;
    public String indoor_location;
    public Integer battery;
    public String type;
    public String username;
    public boolean location_enabled;
    
}

// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;

import java.util.Map;

public class DeviceInfoWorkflow extends Workflow {

    @Override
    public void onStart(Map<String, Object> startEvent) {
        super.onStart(startEvent);

        String sourceUri = Relay.getStartEventSourceUri(startEvent);

        Relay.startInteraction(this, sourceUri, "interaction name", null);
    }
    
    @Override
    public void onInteractionLifecycle(Map<String, Object> lifecycleEvent) {
        super.onInteractionLifecycle(lifecycleEvent);

        String type = (String)lifecycleEvent.get("type");
        String sourceUri = (String)lifecycleEvent.get("source_uri");

        if (type.equals("started")) {

            Relay.setDeviceName(this, sourceUri, "optimus prime");
            Relay.setLocationEnabled(this, sourceUri, true);
            
            
            String name  = Relay.getDeviceName(this, sourceUri, true);
            Relay.sayAndWait(this, sourceUri, "Device name is " + name);

            String id = Relay.getDeviceId(this, sourceUri, true);
            Relay.sayAndWait(this, sourceUri, "Device id is " + id);

            String address = Relay.getDeviceAddress(this, sourceUri, false);
            if (address != null){
                Relay.sayAndWait(this, sourceUri, "Device address is " + address);
            }
            else {
                Relay.sayAndWait(this, sourceUri, "Device address is unavailable");
            }

            double[] latlong = Relay.getDeviceLatLong(this, sourceUri, false);
            if (latlong != null){
                Relay.sayAndWait(this, sourceUri, "Device lat long is " + latlong[0] + " lat " + latlong[1] + " long");
            }
            else {
                Relay.sayAndWait(this, sourceUri, "Device lat long is unavailable");
            }

            String indoorLoc = Relay.getDeviceIndoorLocation(this, sourceUri, false);
            if (indoorLoc != null){
                Relay.sayAndWait(this, sourceUri, "Device indoor location is " + indoorLoc);
            }
            else {
                Relay.sayAndWait(this, sourceUri, "Device indoor location is unavailable");
            }
            
            Integer battery = Relay.getDeviceBattery(this, sourceUri, false);
            if (battery != null) {
                Relay.sayAndWait(this, sourceUri, "Device battery is " + battery + " percent");
            }
            else {
                Relay.sayAndWait(this, sourceUri, "Device battery is unknown");
            }

            String deviceType = Relay.getDeviceType(this, sourceUri, true);
            Relay.sayAndWait(this, sourceUri, "Device type is " + deviceType);
            
            String username = Relay.getDeviceUsername(this, sourceUri, true);
            Relay.sayAndWait(this, sourceUri, "Device username is " + username);
            
            Boolean locEnabled = Relay.getDeviceLocationEnabled(this, sourceUri, true);
            Relay.sayAndWait(this, sourceUri, "Device location enabled is " + locEnabled);
            
            Relay.terminate(this);
        }
    }
    
}

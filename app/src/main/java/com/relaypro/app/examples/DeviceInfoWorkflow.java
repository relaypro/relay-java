// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;

import java.util.Map;

public class DeviceInfoWorkflow extends Workflow {

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = (String)startEvent.trigger.args.get("source_uri");

        relay.startInteraction(sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        String type = (String) lifecycleEvent.type;
        String sourceUri = (String) lifecycleEvent.sourceUri;

        if (type.equals("started")) {
            relay.setDeviceName(sourceUri, "optimus prime");
            relay.setLocationEnabled(sourceUri, true);
            
            String name = relay.getDeviceName(sourceUri, true);
            relay.sayAndWait(sourceUri, "Device name is " + name);

            String id = relay.getDeviceId(sourceUri, true);
            relay.sayAndWait(sourceUri, "Device id is " + id);

            String address = relay.getDeviceAddress(sourceUri, false);
            if (address != null) {
                relay.sayAndWait(sourceUri, "Device address is " + address);
            } else {
                relay.sayAndWait(sourceUri, "Device address is unavailable");
            }

            double[] latlong = relay.getDeviceLatLong(sourceUri, false);
            if (latlong != null) {
                relay.sayAndWait(sourceUri, "Device lat long is " + latlong[0] + " lat " + latlong[1] + " long");
            } else {
                relay.sayAndWait(sourceUri, "Device lat long is unavailable");
            }

            String indoorLoc = relay.getDeviceIndoorLocation(sourceUri, false);
            if (indoorLoc != null) {
                relay.sayAndWait(sourceUri, "Device indoor location is " + indoorLoc);
            } else {
                relay.sayAndWait(sourceUri, "Device indoor location is unavailable");
            }

            Integer battery = relay.getDeviceBattery(sourceUri, false);
            if (battery != null) {
                relay.sayAndWait(sourceUri, "Device battery is " + battery + " percent");
            } else {
                relay.sayAndWait(sourceUri, "Device battery is unknown");
            }

            String deviceType = relay.getDeviceType(sourceUri, true);
            relay.sayAndWait(sourceUri, "Device type is " + deviceType);

            String username = relay.getDeviceUsername(sourceUri, true);
            relay.sayAndWait(sourceUri, "Device username is " + username);

            Boolean locEnabled = relay.getDeviceLocationEnabled(sourceUri, true);
            relay.sayAndWait(sourceUri, "Device location enabled is " + locEnabled);

            relay.terminate();
        }
    }

}

// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;

public class DeviceInfoWorkflow extends Workflow {

    private static final String INTERACTION_NAME = "device info interaction";

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = Relay.getSourceUri(startEvent);
        relay.startInteraction(sourceUri, INTERACTION_NAME, null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        String interactionUri = lifecycleEvent.sourceUri;

        if (lifecycleEvent.isTypeStarted()) {
            relay.setDeviceName(interactionUri, "optimus prime");

            String name = relay.getDeviceName(interactionUri, true);
            relay.sayAndWait(interactionUri, "Device name is " + name);

            String id = relay.getDeviceId(interactionUri, true);
            relay.sayAndWait(interactionUri, "Device id is " + id);

            String address = relay.getDeviceAddress(interactionUri, false);
            if (address != null) {
                relay.sayAndWait(interactionUri, "Device address is " + address);
            } else {
                relay.sayAndWait(interactionUri, "Device address is unavailable");
            }

            double[] latlong = relay.getDeviceLatLong(interactionUri, false);
            if (latlong != null) {
                relay.sayAndWait(interactionUri, "Device lat long is " + latlong[0] + " lat " + latlong[1] + " long");
            } else {
                relay.sayAndWait(interactionUri, "Device lat long is unavailable");
            }

            String indoorLoc = relay.getDeviceIndoorLocation(interactionUri, false);
            if (indoorLoc != null) {
                relay.sayAndWait(interactionUri, "Device indoor location is " + indoorLoc);
            } else {
                relay.sayAndWait(interactionUri, "Device indoor location is unavailable");
            }

            Integer battery = relay.getDeviceBattery(interactionUri, false);
            if (battery != null) {
                relay.sayAndWait(interactionUri, "Device battery is " + battery + " percent");
            } else {
                relay.sayAndWait(interactionUri, "Device battery is unknown");
            }

            String deviceType = relay.getDeviceType(interactionUri, true);
            relay.sayAndWait(interactionUri, "Device type is " + deviceType);

            String username = relay.getUserProfile(interactionUri, true);
            relay.sayAndWait(interactionUri, "Device username is " + username);

            Boolean locEnabled = relay.getDeviceLocationEnabled(interactionUri, true);
            relay.sayAndWait(interactionUri, "Device location enabled is " + locEnabled);

            relay.endInteraction(interactionUri, INTERACTION_NAME);
        }
        if (lifecycleEvent.isTypeEnded()) {
            relay.terminate();
        }
    }

}

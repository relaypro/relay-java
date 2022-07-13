// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Cycles through various led settings on timers
 */

public class LedsWorkflow extends Workflow {
    private static Logger logger = LoggerFactory.getLogger(LedsWorkflow.class);

    String sourceUri = null;

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = (String)startEvent.trigger.args.get("source_uri");

        relay.startInteraction(sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        String type = lifecycleEvent.type;
        this.sourceUri = lifecycleEvent.sourceUri;

        if (type.equals("started")) {
            relay.switchAllLedOn(sourceUri, "ff0000");
            relay.sayAndWait(sourceUri, "red");
            relay.setTimer(TimerType.TIMEOUT, "rainbow", 3, TimeoutType.SECS);
        }
    }

    @Override
    public void onTimerFired(Relay relay, TimerFiredEvent timerEvent) {
        super.onTimerFired(relay, timerEvent);
        logger.debug("Timer fired: " + timerEvent);

        String timerName = (String) timerEvent.name;
        if (timerName.equals("rainbow")) {
            relay.rainbow(sourceUri, -1);
            relay.sayAndWait(sourceUri, "rainbow");
            relay.setTimer(TimerType.TIMEOUT, "rotate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("rotate")) {
            relay.rotate(sourceUri, "00ff00");
            relay.sayAndWait(sourceUri, "rotate");
            relay.setTimer(TimerType.TIMEOUT, "flash", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("flash")) {
            relay.flash(sourceUri, "ff00ff", 5);
            relay.sayAndWait(sourceUri, "flash");
            relay.setTimer(TimerType.TIMEOUT, "breathe", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("breathe")) {
            relay.breathe(sourceUri, "ff00ff");
            relay.sayAndWait(sourceUri, "breathe");
            relay.setTimer(TimerType.TIMEOUT, "vibrate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("vibrate")) {
            relay.switchAllLedOff(sourceUri);
            relay.vibrate(sourceUri, new long[]{100, 500, 500, 500, 500, 500});
            relay.sayAndWait(sourceUri, "vibrate");
            relay.setTimer(TimerType.TIMEOUT, "finish", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("finish")) {
            relay.sayAndWait(sourceUri, "goodbye");
            relay.switchAllLedOff(sourceUri);
            relay.terminate();
        }
    }

}

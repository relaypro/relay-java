// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cycles through various led settings on timers
 */
public class LedsWorkflow extends Workflow {
    private static final Logger logger = LoggerFactory.getLogger(LedsWorkflow.class);
    private static final String INTERACTION_NAME = "LED interaction";
    private String interactionUri = null;

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = Relay.getSourceUri(startEvent);
        relay.startInteraction(sourceUri, INTERACTION_NAME, null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        this.interactionUri = lifecycleEvent.sourceUri;

        if (lifecycleEvent.isTypeStarted()) {
            relay.switchAllLedOn(this.interactionUri, "ff0000");
            relay.sayAndWait(this.interactionUri, "red");
            relay.setTimer(TimerType.TIMEOUT, "rainbow", 3, TimeoutType.SECS);
        } else if (lifecycleEvent.isTypeEnded()) {
            relay.terminate();
        }
    }

    @Override
    public void onTimerFired(Relay relay, TimerFiredEvent timerEvent) {
        super.onTimerFired(relay, timerEvent);
        logger.debug("Timer fired: " + timerEvent);

        String timerName = timerEvent.name;
        if (timerName.equals("rainbow")) {
            relay.rainbow(this.interactionUri, -1);
            relay.sayAndWait(this.interactionUri, "rainbow");
            relay.setTimer(TimerType.TIMEOUT, "rotate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("rotate")) {
            relay.rotate(this.interactionUri, "00ff00", 3);
            relay.sayAndWait(this.interactionUri, "rotate");
            relay.setTimer(TimerType.TIMEOUT, "flash", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("flash")) {
            relay.flash(this.interactionUri, "ff00ff", 5);
            relay.sayAndWait(this.interactionUri, "flash");
            relay.setTimer(TimerType.TIMEOUT, "breathe", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("breathe")) {
            relay.breathe(this.interactionUri, "ff00ff", 3);
            relay.sayAndWait(this.interactionUri, "breathe");
            relay.setTimer(TimerType.TIMEOUT, "vibrate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("vibrate")) {
            relay.switchAllLedOff(this.interactionUri);
            relay.vibrate(this.interactionUri, new int[]{100, 500, 500, 500, 500, 500});
            relay.sayAndWait(this.interactionUri, "vibrate");
            relay.setTimer(TimerType.TIMEOUT, "finish", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("finish")) {
            relay.sayAndWait(this.interactionUri, "goodbye");
            relay.switchAllLedOff(this.interactionUri);
            relay.endInteraction(this.interactionUri, INTERACTION_NAME);
        }
    }

}

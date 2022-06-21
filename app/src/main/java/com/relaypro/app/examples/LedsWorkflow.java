// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.TimeoutType;
import com.relaypro.sdk.types.TimerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Cycles through various led settings on timers */

public class LedsWorkflow extends Workflow {
    private static Logger logger = LoggerFactory.getLogger(LedsWorkflow.class);
    
    String sourceUri = null;
    
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
        this.sourceUri = (String)lifecycleEvent.get("source_uri");
        
        if (type.equals("started")) {
            Relay.switchAllLedOn(this, sourceUri, "ff0000");
            Relay.sayAndWait(this, sourceUri, "red");
            Relay.setTimer(this, TimerType.TIMEOUT, "rainbow", 3, TimeoutType.SECS);
        }
    }
    
    @Override
    public void onTimerFired(Map<String, Object> timerEvent) {
        super.onTimer(timerEvent);
        logger.debug("Timer fired: " + timerEvent);

        String timerName = (String)timerEvent.get("name");
        if (timerName.equals("rainbow")) {
            Relay.rainbow(this, sourceUri, -1);
            Relay.sayAndWait(this, sourceUri, "rainbow");
            Relay.setTimer(this, TimerType.TIMEOUT, "rotate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("rotate")) {
            Relay.rotate(this, sourceUri, "00ff00");
            Relay.sayAndWait(this, sourceUri, "rotate");
            Relay.setTimer(this, TimerType.TIMEOUT, "flash", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("flash")) {
            Relay.flash(this, sourceUri, "ff00ff", 5);
            Relay.sayAndWait(this, sourceUri, "flash");
            Relay.setTimer(this, TimerType.TIMEOUT, "breathe", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("breathe")) {
            Relay.breathe(this, sourceUri, "ff00ff");
            Relay.sayAndWait(this, sourceUri, "breathe");
            Relay.setTimer(this, TimerType.TIMEOUT, "vibrate", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("vibrate")) {
            Relay.switchAllLedOff(this, sourceUri);
            Relay.vibrate(this, sourceUri, new long[] {100, 500, 500, 500, 500, 500});
            Relay.sayAndWait(this, sourceUri, "vibrate");
            Relay.setTimer(this, TimerType.TIMEOUT, "finish", 3, TimeoutType.SECS);
            return;
        }
        if (timerName.equals("finish")) {
            Relay.sayAndWait(this, sourceUri, "goodbye");
            Relay.switchAllLedOff(this, sourceUri);
            Relay.terminate(this);
        }
    }
    
}

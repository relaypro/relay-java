// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.TimeoutType;
import com.relaypro.sdk.types.TimerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TimerWorkflow extends Workflow {
    
    private static Logger logger = LoggerFactory.getLogger(TimerWorkflow.class);
    
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
            Relay.sayAndWait(this, sourceUri, "setting timers");
            Relay.setTimer(this, TimerType.TIMEOUT, "first timer", 5, TimeoutType.SECS);
            Relay.setTimer(this, TimerType.TIMEOUT, "second timer", 10, TimeoutType.SECS);
        }
    }
    
    @Override
    public void onTimerFired(Map<String, Object> timerEvent) {
        super.onTimer(timerEvent);
        logger.debug("Rimer fired: " + timerEvent);
        
        Relay.sayAndWait(this, this.sourceUri, (String)timerEvent.get("name") + " fired");
        
        Relay.clearTimer(this, "second timer");
        
        Relay.terminate(this);
    }
    
}

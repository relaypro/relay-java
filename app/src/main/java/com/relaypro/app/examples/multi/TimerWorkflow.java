// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimerWorkflow extends Workflow {

    private static final Logger logger = LoggerFactory.getLogger(TimerWorkflow.class);

    private String interactionUri = null;

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = Relay.getSourceUriFromStartEvent(startEvent);
        relay.startInteraction(sourceUri, "timer interaction", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        this.interactionUri = lifecycleEvent.sourceUri;

        if (lifecycleEvent.isTypeStarted()) {
            relay.sayAndWait(this.interactionUri, "setting timers");
            relay.setTimer(TimerType.TIMEOUT, "first timer", 5, TimeoutType.SECS);
            // we'll cancel the 2nd timer before it fires, just to show how to do that
            relay.setTimer(TimerType.TIMEOUT, "second timer", 10, TimeoutType.SECS);
        }
        if (lifecycleEvent.isTypeEnded()) {
            relay.terminate();
        }
    }

    @Override
    public void onTimerFired(Relay relay, TimerFiredEvent timerEvent) {
        super.onTimerFired(relay, timerEvent);
        logger.debug("Timer fired: " + timerEvent);
        relay.sayAndWait(this.interactionUri, timerEvent.name + " fired");
        relay.clearTimer("second timer");
        relay.endInteraction(this.interactionUri);
    }

}

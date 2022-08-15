// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimerWorkflow extends Workflow {

    private static Logger logger = LoggerFactory.getLogger(TimerWorkflow.class);

    String sourceUri = null;

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = (String) startEvent.trigger.args.get("source_uri");

        relay.startInteraction(sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);

        String type = lifecycleEvent.type;
        this.sourceUri = lifecycleEvent.sourceUri;

        if (type.equals("started")) {
            relay.sayAndWait(sourceUri, "setting timers");
            relay.setTimer(TimerType.TIMEOUT, "first timer", 5, TimeoutType.SECS);
            relay.setTimer(TimerType.TIMEOUT, "second timer", 10, TimeoutType.SECS);
        }
    }

    @Override
    public void onTimerFired(Relay relay, TimerFiredEvent timerEvent) {
        super.onTimerFired(relay, timerEvent);
        logger.debug("Timer fired: " + timerEvent);

        relay.sayAndWait(this.sourceUri, (String) timerEvent.name + " fired");

        relay.clearTimer("second timer");

        relay.terminate();
    }

}

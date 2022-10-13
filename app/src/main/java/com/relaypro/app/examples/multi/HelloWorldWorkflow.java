// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorldWorkflow extends Workflow {

    private static final Logger logger = LoggerFactory.getLogger(HelloWorldWorkflow.class);

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = Relay.getSourceUriFromStartEvent(startEvent);
        logger.debug("Started hello wf from sourceUri: " + sourceUri + " trigger: " + startEvent.trigger);

        relay.startInteraction( sourceUri, "hello interaction", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);
        
        logger.debug("User workflow got interaction lifecycle: " + lifecycleEvent);
        
        String interactionUri = lifecycleEvent.sourceUri;
        
        if (lifecycleEvent.isTypeStarted()) {
            String deviceName = relay.getDeviceName(interactionUri, false);
            relay.say(interactionUri, "What is your name?");
            String returned = relay.listen(interactionUri, "request_1");
            relay.say(interactionUri, "Hello " + returned + ". You are currently using " + deviceName);
            relay.endInteraction(interactionUri);
        }
        if (lifecycleEvent.isTypeEnded()) {
            relay.terminate();
        }
    }
}

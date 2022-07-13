// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class HelloWorldWorkflow extends Workflow {

    private static Logger logger = LoggerFactory.getLogger(HelloWorldWorkflow.class);
    
    String sourceUri = null;
    
    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);

        String sourceUri = (String)startEvent.trigger.args.get("source_uri");
        logger.debug("Started hello wf from sourceUri: " + sourceUri + " trigger: " + startEvent.trigger);

        relay.startInteraction( sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle(relay, lifecycleEvent);
        
        logger.debug("User workflow got interaction lifecycle: " + lifecycleEvent);
        
        String type = (String)lifecycleEvent.type;
        this.sourceUri = (String)lifecycleEvent.sourceUri;
        
        if (type.equals("started")) {
            relay.sayAndWait( sourceUri, "hello world");
            relay.endInteraction(sourceUri, "interaction name");
        }
        if (type.equals("ended")) {
            relay.terminate();
        }
    }
}

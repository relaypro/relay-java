package com.relaypro.app.examples;

import com.relaypro.app.Setup;
import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class HelloWorldWorkflow extends Workflow {

    private static Logger logger = LoggerFactory.getLogger(HelloWorldWorkflow.class);
    
    String sourceUri = null;
    
    @Override
    public void onStart(Map<String, Object> startEvent) {
        super.onStart(startEvent);

        logger.debug("Started hello wf " + startEvent);

        String sourceUri = Relay.getStartEventSourceUri(startEvent);

        Relay.startInteraction(this, sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Map<String, Object> lifecycleEvent) {
        super.onInteractionLifecycle(lifecycleEvent);
        
        logger.debug("User workflow got interaction lifecycle: " + lifecycleEvent);
        
        String type = (String)lifecycleEvent.get("type");
        this.sourceUri = (String)lifecycleEvent.get("source_uri");
        
        if (type.equals("started")) {
            Relay.sayAndWait(this, sourceUri, "hello world");
            Relay.terminate(this);
        }
    }
}

// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.standalone;

import com.relaypro.app.examples.util.JettyWebsocketServer;
import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class HelloWorld {

    public static void main(String... args) {
        int port = 8080;
        Map<String, String> env = System.getenv();
        try {
            // this is required for Heroku, optional elsewhere
            if (env.containsKey("PORT") && (Integer.parseInt(env.get("PORT")) > 0)) {
                port = Integer.parseInt(env.get("PORT"));
            }
        } catch (NumberFormatException e) {
            System.err.println("Unable to parse PORT env value as an integer, ignoring: " + env.get("PORT"));
        }
        Relay.addWorkflow("hellopath", new MyWorkflow());

        // Note that this uses the Jetty websocket server implementation in the app's util package.
        JettyWebsocketServer.startServer(port);
    }

    private static class MyWorkflow extends Workflow {
        private final Logger logger = LoggerFactory.getLogger(MyWorkflow.class);

        @Override
        public void onStart(Relay relay, StartEvent startEvent) {
            super.onStart(relay, startEvent);

            String sourceUri = Relay.getSourceUri(startEvent);
            logger.debug("Started hello wf from sourceUri: " + sourceUri + " trigger: " + startEvent.trigger);
            relay.startInteraction( sourceUri, "hello interaction", null);
        }

        @Override
        public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
            super.onInteractionLifecycle(relay, lifecycleEvent);

            logger.debug("User workflow got interaction lifecycle: " + lifecycleEvent);
            String interactionUri = (String)lifecycleEvent.sourceUri;
            if (lifecycleEvent.isTypeStarted()) {
                relay.say(interactionUri, "Hello world");
                relay.endInteraction(interactionUri);
            }
            if (lifecycleEvent.isTypeEnded()) {
                relay.terminate();
            }
        }
    }
}

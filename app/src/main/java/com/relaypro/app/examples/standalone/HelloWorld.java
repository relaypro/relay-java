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
        if (env.containsKey("PORT") && (Integer.parseInt(env.get("PORT")) > 0)) {
            port = Integer.parseInt("PORT");
        }
        if ((args != null) && (args.length > 0) && (Integer.parseInt(args[0]) > 0)) {
            port = Integer.parseInt(args[0]);
        }

        Relay.addWorkflow("hellopath", new MyWorkflow());

        // Note that this uses the Jetty websocket server implementation in the app's util package.
        JettyWebsocketServer.startServer(port);
    }

    private static class MyWorkflow extends Workflow {
        private final Logger logger = LoggerFactory.getLogger(MyWorkflow.class);
        private final String INTERACTION_NAME = "hello interaction";

        @Override
        public void onStart(Relay relay, StartEvent startEvent) {
            super.onStart(relay, startEvent);

            String sourceUri = Relay.getSourceUri(startEvent);
            logger.debug("Started hello wf from sourceUri: " + sourceUri + " trigger: " + startEvent.trigger);
            relay.startInteraction( sourceUri, INTERACTION_NAME, null);
        }

        @Override
        public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
            super.onInteractionLifecycle(relay, lifecycleEvent);

            logger.debug("User workflow got interaction lifecycle: " + lifecycleEvent);
            String interactionUri = (String)lifecycleEvent.sourceUri;
            if (lifecycleEvent.isTypeStarted()) {
                relay.say(interactionUri, "Hello world");
                relay.endInteraction(interactionUri, INTERACTION_NAME);
            }
            if (lifecycleEvent.isTypeEnded()) {
                relay.terminate();
            }
        }
    }
}

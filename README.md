# relay-java

A Java SDK for [Relay Workflows](https://developer.relaypro.com).

# Guides Documentation

The higher-level guides are available at https://developer.relaypro.com/docs

# API Reference Documentation

The generated javadoc documentation is available at https://relaypro.github.io/relay-java/

## Usage

- The following demonstrates a simple Hello World program, located in app/src/main/java/com/relaypro/app/examples/HelloWorldWorkflow.java
<pre>
// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            String deviceName = relay.getDeviceName(sourceUri, false);
            relay.say( sourceUri, "What is your name?");
            String returned = relay.listen(sourceUri, "request-1");
            relay.say(sourceUri, "Hello " + returned + " you are currently using " + deviceName);
            relay.endInteraction(sourceUri, "interaction name");
        }
        if (type.equals("ended")) {
            relay.terminate();
        }
    }
}
</pre>

## Development

    bash
    git clone git@github.com:relaypro/relay-java.git
    cd relay-java
    make build

Start the demo workflow server:

    bash
    make run

Code is split into two applications: `sdk` and `app`.

`sdk` contains Relay SDK functionality, `app` contains sample code for creating and running workflows, including several example workflows.

Main entry point is the `com.relaypro.sdk.Relay` class. To create a workflow, extend `com.relaypro.sdk.Workflow` and then add that workflow with a name by calling `Relay.addWorkflow(name, workflow)`. See app/src/main/java/com/relaypro/app/examples for example implementations.

The class `Workflow` defines the event callbacks that your workflow can respond to. `Relay` defines requests that can be made inside those callbacks.

A sample websocket listener `WebsocketReceiver` is provided to receive connections and start workflows when requested. 

## Additional Instructions for Development on Heroku

See the [Guide](https://developer.relaypro.com/docs/heroku).

## TLS Capability

Your workflow server must be exposed to the Relay server with TLS so
that the `wss` (WebSocket Secure) protocol is used, this is enforced by
the Relay server when registering workflows. See the
[Guide](https://developer.relaypro.com/docs/requirements) on this topic.


## License
[MIT](https://choosealicense.com/licenses/mit/)


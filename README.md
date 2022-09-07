# relay-java

A Java SDK for [Relay Workflows](https://developer.relaypro.com).

## Guides Documentation

The higher-level guides are available at https://developer.relaypro.com/docs

## API Reference Documentation

The generated javadoc documentation is available at https://relaypro.github.io/relay-java/

## Usage

The following demonstrates a simple Hello World program, located in
app/src/main/java/com/relaypro/app/examples/standalone/HelloWorld.java.
It also uses JettyWebsocketServer as a websocket implementation, which
is in the `util` package.
<pre>
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
        Relay.addWorkflow("hellopath", new MyWorkflow());
        // Note that this uses the Jetty websocket server implementation in the util package.
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
</pre>

# Running the Sample

Use git to make a local copy of the SDK and the included sample application:

    git clone git@github.com:relaypro/relay-java.git
    cd relay-java
    make run

However, until you register a trigger with the server via the Relay CLI, the
workflow won't get invoked via an inbound websocket connection.
[Run a built-in workflow](https://developer.relaypro.com/docs/run-a-built-in-workflow)
first to verify your Relay CLI installation. Then follow the
[documentation for a custom workflow](https://developer.relaypro.com/docs/getting-started).

There are two sample apps included: one is a standalone with one workflow, and the
other is a multiple that has about 6 workflows. The app's `build.gradle` file
via `application.mainClass`. For the multi one, you can see the URL paths in
`Setup.java` where it calls `Relay.addWorkflow`.

The code is split into two gradle projects: `sdk` and `app`.

The `sdk` contains Relay SDK functionality, `app` contains sample code for creating and
running workflows using the SDK. The `app` includes several example workflows.

The main entry point is the `com.relaypro.sdk.Relay` class. To create a workflow, extend
`com.relaypro.sdk.Workflow` and then add that workflow with a URL path name by calling
`Relay.addWorkflow(name, workflow)`. See app/src/main/java/com/relaypro/app/examples
for example implementations.

The class `Workflow` defines the event callbacks that your workflow can respond to.
`Relay` defines requests that can be made inside those callbacks.

A sample websocket server implementation `JettyWebsocketServer` is provided in the
com.relaypro.app.examples.util package to receive websocket client connections
and start workflows when requested by the Relay server.

## TLS Capability

Your workflow server must be exposed to the Relay server with TLS so
that the `wss` (WebSocket Secure) protocol is used, this is enforced by
the Relay server when registering workflows. See the
[Guide](https://developer.relaypro.com/docs/requirements) on this topic.

The [Eclipse Jetty](https://www.eclipse.org/jetty/) module used for an http server here does support TLS. See
the [Jetty instructions](https://www.eclipse.org/jetty/documentation/jetty-11/operations-guide/index.html#og-keystore)
on configuring TLS.

## Verbose Mode Logging

The SDK is using slf4j and the sample apps are providing a binding to log4j, so
you can use the `log4j.properties` file in the app's `resources` folder to configure
logging levels. The sample application by default will show log messages from itself
and the SDK at INFO level and above. If you wish to see more logging detail from
the SDK, and you continue to use the log4j binding, then modify the `log4j.properties`
file to use the DEBUG log level for the com.relaypro.sdk package:

    log4j.logger.com.relaypro.sdk=DEBUG

## Development of the SDK

Here are some basic commands to get started if you want to make changes to the SDK.

    bash
    git clone git@github.com:relaypro/relay-java.git
    cd relay-java
    make jar (for a standalone jar)
    make build (to include the sample apps)

## License
[MIT](https://choosealicense.com/licenses/mit/)


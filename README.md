Relay-java SDK

Code is split into two applications: `sdk` and `app`.

`sdk` contains Relay SDK functionality, `app` contains sample code for creating and running workflows, including several example workflows.

Main entry point is the `com.relaypro.sdk.Relay` class. To create a workflow, extend `com.relaypro.sdk.Workflow` and then add that workflow with a name by calling `Relay.addWorkflow(name, workflow)`. See app/src/main/java/com/relaypro/app/examples for example implementations.

The class `Workflow` defines the event callbacks that your workflow can respond to. `Relay` defines requests that can be made inside those callbacks.

A sample websocket listener `WebsocketReceiver` is provided to receive connections and start workflows when requested. 


// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.app.examples.util.JettyWebsocketServer;
import com.relaypro.sdk.Relay;

import java.util.Map;

public class Setup {
    public static void main(String... args) {

        int port = 8080;
        Map<String, String> env = System.getenv();
        if (env.containsKey("PORT") && (Integer.parseInt(env.get("PORT")) > 0)) {
            port = Integer.parseInt("PORT");
        }
        if ((args != null) && (args.length > 0) && (Integer.parseInt(args[0]) > 0)) {
            port = Integer.parseInt(args[0]);
        }

        Relay.addWorkflow("helloworld", new HelloWorldWorkflow());
        Relay.addWorkflow("javatest", new HelloWorldWorkflow());
        Relay.addWorkflow("timers", new TimerWorkflow());
        Relay.addWorkflow("leds", new LedsWorkflow());
        Relay.addWorkflow("info", new DeviceInfoWorkflow());
        Relay.addWorkflow("buttons", new ButtonCountWorkflow());

        // This uses the Jetty websocket server implementation in the app's util package.
        JettyWebsocketServer.startServer(port);
    }
}

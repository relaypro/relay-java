// Copyright © 2022 Relay Inc.

package com.relaypro.app;

import com.relaypro.app.examples.*;
import com.relaypro.sdk.Relay;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Setup {
    
    private static final int PORT = 3000;

    private static Logger logger = LoggerFactory.getLogger(Setup.class);
    
    public static void main(String... args) {
        
        Relay.addWorkflow("helloworld", new HelloWorldWorkflow());
        Relay.addWorkflow("javatest", new HelloWorldWorkflow());
        Relay.addWorkflow("timers", new TimerWorkflow());
        Relay.addWorkflow("leds", new LedsWorkflow());
        Relay.addWorkflow("info", new DeviceInfoWorkflow());
        Relay.addWorkflow("buttons", new ButtonCountWorkflow());
        
         startServer();
    }
    
    private static void startServer() {
        Server server = new Server();
        // create connector
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(PORT);

        // create context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        server.addConnector(connector);
        server.setHandler(context);

        JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // This lambda will be called at the appropriate place in the
            // ServletContext initialization phase where you can initialize
            // and configure  your websocket container.

            // Configure defaults for container
            wsContainer.setDefaultMaxTextMessageBufferSize(65535);

            // Add WebSocket endpoint to javax.websocket layer
            wsContainer.addEndpoint(WebsocketReceiver.class);
        });

        logger.debug("Starting server on port " + PORT);
        try {
            server.start();
        } catch (Exception e) {
            logger.error("Error starting server", e);
        }
        try {
            server.join();
        } catch (InterruptedException e) {
            logger.error("Error joining server", e);
        }
    }
    
}

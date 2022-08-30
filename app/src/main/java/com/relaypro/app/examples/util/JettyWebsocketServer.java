package com.relaypro.app.examples.util;

import com.relaypro.sdk.JakartaWebsocketReceiver;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Jetty provides an embeddable web server, servlet container, and support for a web socket.
// But we don't want to require it be embedded in the SDK.
public class JettyWebsocketServer {
    private static final Logger logger = LoggerFactory.getLogger(JettyWebsocketServer.class);

    public static void startServer(int port) {
        Server jettyServer = new Server();
        // create connector
        ServerConnector jettyServerConnector = new ServerConnector(jettyServer);
        jettyServerConnector.setPort(port);

        // create context
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setContextPath("/");

        jettyServer.addConnector(jettyServerConnector);
        jettyServer.setHandler(servletContextHandler);

        JakartaWebSocketServletContainerInitializer.configure(servletContextHandler, (servletContext, wsContainer) -> {
            // This lambda will be called at the appropriate place in the
            // ServletContext initialization phase where you can initialize
            // and configure  your websocket container.

            // Configure defaults for container
            wsContainer.setDefaultMaxTextMessageBufferSize(65535);

            // Add WebSocket endpoint to javax.websocket layer
            wsContainer.addEndpoint(JakartaWebsocketReceiver.class);
        });

        logger.info("Starting server on port " + port);
        try {
            jettyServer.start();
        } catch (Exception e) {
            logger.error("Error starting server", e);
        }
        try {
            jettyServer.join();
        } catch (InterruptedException e) {
            logger.error("Error joining server", e);
        }

    }

}

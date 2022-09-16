// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Map the websocket API (onOpen, handleTextMessage, onError, onClose) to
 * the Relay methods. You may replace this with your own mapping if you wish
 * to use a different websocket implementation. It is provided here for reuse.
 */
@ServerEndpoint(value="/{workflowname}")
public class JakartaWebsocketReceiver {

    private static final Logger logger = LoggerFactory.getLogger(JakartaWebsocketReceiver.class);

    @OnOpen
    public void onOpen(Session session, @PathParam("workflowname") String workflowName) {
        logger.debug("Starting workflow " + workflowName);

        session.setMaxIdleTimeout(Long.MAX_VALUE);

        // notify relay sdk to start workflow
        Relay.startWorkflow(session, workflowName);
    }
    
    @OnMessage
    public void handleTextMessage(Session session, String message) {
        logger.debug("<-- Message Received: " + message);

        // pass message to relay sdk
        Relay.receiveMessage(session, message);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // ibot closes the connection on terminate(); this is expected
        if(!throwable.getClass().getName().equals("java.nio.channels.ClosedChannelException")){
            logger.error("Error occurred: ", throwable);
        }
    }
    
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.debug("Websocket receiver closed: " + closeReason);
        
        // notify relay sdk of close
        Relay.stopWorkflow(session, closeReason.getReasonPhrase());
    }

}

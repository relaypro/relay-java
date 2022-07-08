// Copyright © 2022 Relay Inc.

package com.relaypro.app;

import com.relaypro.sdk.Relay;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint(value="/ws/{workflowname}")
public class WebsocketReceiver {

    private static Logger logger = LoggerFactory.getLogger(WebsocketReceiver.class);
    
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
        logger.error("Error occurred: ", throwable);
    }
    
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.debug("Websocket receiver closed: " + closeReason);
        
        // notify relay sdk of close
        Relay.stopWorkflow(session, closeReason.getReasonPhrase());
    }
    

}

package com.relaypro.app;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;

// https://jakarta.ee/specifications/websocket/2.0/apidocs/

@ServerEndpoint(value="/ws/{workflowname}")
public class WebsocketReceiver {

    static {
        Relay.addWorkflow("hello", new Workflow() {
            @Override
            protected void onStart(Object startEvent) {
                super.onStart(startEvent);

                System.out.println("started hello wf");
            }
        });
    }
    
    @OnOpen
    public void onOpen(Session session, @PathParam("workflowname") String workflowName) {
        System.out.println("Starting workflow " + workflowName);
        
        // TODO notify relay sdk to start workflow
        Relay.startWorkflow(session, workflowName);
    }
    
    @OnMessage
    public void handleTextMessage(Session session, String message) {
        System.out.println("New Text Message Received: " + message);
        
        // TODO pass message to relay sdk
        Relay.receiveMessage(session, message);
                
//        return "{'_type': 'wf_api_terminate_request', '_id': 'asdf'}";
    }

    // we don't want to handle binary messages
//    @OnMessage(maxMessageSize = 1024000)
//    public byte[] handleBinaryMessage(byte[] buffer) {
//        System.out.println("New Binary Message Received");
//        return buffer;
//    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable);
        
        // TODO notify relay sdk of error
    }
    
    @OnClose
    public void onClose(Session session) {
        System.out.println("on close");
        
        // TODO notify relay sdk of close
    }
    

}

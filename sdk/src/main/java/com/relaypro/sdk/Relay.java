package com.relaypro.sdk;

import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Relay {

//    public static void initializeRelaySdk(int port) {
//        
//    }
    
    private static final Map<Session, Workflow> runningWorkflows = new HashMap<>();
    private static final Map<String, Workflow> WORKFLOWS = new HashMap<String, Workflow>();

    
    public static void addWorkflow(String name, Workflow wf) {
        System.out.println("adding wf name " + name);
        WORKFLOWS.put(name, wf);
    }
 
    public static void startWorkflow(Session session, String workflowName) {
        Workflow wf = WORKFLOWS.get(workflowName);
        System.out.println("ws connected to wf " + workflowName);
        
        runningWorkflows.put(session, wf);
    }

    public static void receiveMessage(Session session, String message) {
        // decode what message type it is, event/response, get the running wf, call the appropriate callback
        
        Workflow wf = runningWorkflows.get(session);
        
        
        MessageWrapper wrapper = MessageWrapper.parseMessage(message);
        // if an event, send to event callback in workflow
        if (wrapper.eventOrResponse == "event") {
            
        }
        // if response, match to request
        else if (wrapper.eventOrResponse == "response") {
            
        }
    }
    
    private void invokeEventCallback(MessageWrapper wrapper) {
        
        
    }
    
    public void writeMessage(String message, Session session) {
        try {
            session.getBasicRemote().sendObject(message);
        } catch (IOException | EncodeException e) {
            e.printStackTrace();
        }
    }
    
}

package com.relaypro;

import jakarta.websocket.OnOpen;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnError;
import jakarta.websocket.OnClose;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value="/ws/{workflowname}")
public class WebsocketReceiver {

    @OnOpen
    public void onOpen(Session session, @PathParam("workflowname") String workflowName) {
        System.out.println("Starting workflow " + workflowName);
    }
    
    @OnMessage
    public String handleTextMessage(Session session, String message) {
        System.out.println("New Text Message Received: " + message);
        return message;
    }

    @OnMessage(maxMessageSize = 1024000)
    public byte[] handleBinaryMessage(byte[] buffer) {
        System.out.println("New Binary Message Received");
        return buffer;
    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error occurred: " + throwable);
    }
    
    @OnClose
    public void onClose(Session session) {
        System.out.println("on close");
    }
}

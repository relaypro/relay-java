// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import jakarta.websocket.Session;

import java.util.Map;
import java.util.concurrent.*;

class WorkflowWrapper {
    
    // holds the Workflow clone, and the session
    Workflow workflow;
    Session session;

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> workflowFuture;      // long running future that runs the workflow logic
    BlockingQueue<MessageWrapper> messageQueue = new LinkedBlockingDeque<>();

    Map<String, Call> pendingRequests = new ConcurrentHashMap<>();
    
}

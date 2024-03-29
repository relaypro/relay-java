// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

class Worker implements Callable<String> {

    private final Relay wrapper;
    
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    
    public Worker(Relay wrapper) {
        this.wrapper = wrapper;
    }
    
    public String call() {
        // long-running process of listening for events, making associated callbacks, and
        // when those callbacks make requests, listening for associated responses/prompts/progress events
        for (;;) {
            try {
                MessageWrapper msgWrapper = this.wrapper.messageQueue.take();           // blocks waiting for message
                if (msgWrapper == null) {
                    // timed out 
                    return null;
                }
                else if (msgWrapper.stopped) {
                    // we've been commanded to shut down, stop looping
                    return null;
                }
                RelayUtils.invokeEventCallback(msgWrapper, this.wrapper);
            } catch (InterruptedException e) {
                logger.error("Interrupted waiting for message", e);
                break;
            }
        }

        return null;
    }
}

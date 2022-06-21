// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class Call {

    BlockingQueue<MessageWrapper> responseQueue = new LinkedBlockingDeque<>();
    
}

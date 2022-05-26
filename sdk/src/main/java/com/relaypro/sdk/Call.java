package com.relaypro.sdk;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

class Call {

    BlockingQueue<MessageWrapper> responseQueue = new LinkedBlockingDeque<>();
    
}

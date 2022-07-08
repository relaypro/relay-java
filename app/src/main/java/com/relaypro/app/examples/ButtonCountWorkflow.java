// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ButtonCountWorkflow extends Workflow {
    private static Logger logger = LoggerFactory.getLogger(ButtonCountWorkflow.class);
    
    String sourceUri = null;
    
    Map<String, Map<String, Integer>> counts = new HashMap<>();     // track button clicks by button type, then taps type

    @Override
    public void onStart(Map<String, Object> startEvent) {
        super.onStart(startEvent);
        String sourceUri = Relay.getStartEventSourceUri(startEvent);
        Relay.startInteraction(this, sourceUri, "interaction name", null);
    }

    @Override
    public void onInteractionLifecycle(Map<String, Object> lifecycleEvent) {
        super.onInteractionLifecycle(lifecycleEvent);
        this.sourceUri = (String)lifecycleEvent.get("source_uri");
    }

    @Override
    public void onButton(Map<String, Object> buttonEvent) {
        String button = Relay.getButtonEventButton(buttonEvent);
        String taps = Relay.getButtonEventTaps(buttonEvent);

        if (!counts.containsKey(button)) {
            counts.put(button, new HashMap<>());
        }
        Map<String, Integer> buttonMap = counts.get(button);

        if (!buttonMap.containsKey(taps)) {
            buttonMap.put(taps, 0);
        }
        Integer count = buttonMap.get(taps) + 1;
        buttonMap.put(taps, count);

        logger.debug(taps + " clicked " + button + " " + count + " times");
        Relay.sayAndWait(this, sourceUri, taps + " clicked " + button + " " + count + " times");
    }
}

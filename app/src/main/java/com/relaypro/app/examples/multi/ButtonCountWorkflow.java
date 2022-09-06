// Copyright © 2022 Relay Inc.

package com.relaypro.app.examples.multi;

import com.relaypro.sdk.Relay;
import com.relaypro.sdk.Workflow;
import com.relaypro.sdk.types.ButtonEvent;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.StartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ButtonCountWorkflow extends Workflow {
    private static final Logger logger = LoggerFactory.getLogger(ButtonCountWorkflow.class);

    private final String INTERACTION_NAME = "button interaction";

    private String interactionUri = null;

    Map<String, Map<String, Integer>> counts = new HashMap<>();     // track button clicks by button type, then taps type

    @Override
    public void onStart(Relay relay, StartEvent startEvent) {
        super.onStart(relay, startEvent);
        String sourceUri = Relay.getSourceUri(startEvent);
        relay.startInteraction(sourceUri, INTERACTION_NAME, null);
    }

    @Override
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
        super.onInteractionLifecycle( relay, lifecycleEvent);
        this.interactionUri = lifecycleEvent.sourceUri;
    }

    @Override
    public void onButton(Relay relay, ButtonEvent buttonEvent) {
        String button = buttonEvent.button;
        String taps = buttonEvent.taps;

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
        relay.sayAndWait(this.interactionUri, taps + " clicked " + button + " " + count + " times");
    }
}

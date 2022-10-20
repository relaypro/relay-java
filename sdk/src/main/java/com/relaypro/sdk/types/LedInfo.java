// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Set information regarding the LED effects, such as how many rotations the 
 * LEDs should move, the number of times to perform the "flash" effects, and so on.
 */
public class LedInfo {

    public Map<String, Object> ledMap = new LinkedHashMap<>();

    public void setRotations(int rotations) {
        ledMap.put("rotations", rotations);
    }

    public void setCount(int count) {
        ledMap.put("count", count);
    }

    public void setDuration(int duration) {
        ledMap.put("duration", duration);
    }

    public void setRepeatDelay(int repaeatDelay) {
        ledMap.put("repeat_delay", repaeatDelay);
    }

    public void setPatterRepeats(int patternRepeats) {
        ledMap.put("pattern_repeats", patternRepeats);
    }

    public void setColor(String index, String color) {
        Map<String, Object> colors = new LinkedHashMap<String, Object>();
        colors.put(index, color);
        ledMap.put("colors", colors); 
    }
}

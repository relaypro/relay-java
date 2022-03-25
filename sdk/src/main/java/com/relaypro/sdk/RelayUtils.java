package com.relaypro.sdk;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.util.Map.entry;

class RelayUtils {
    static Random random = new Random();
    
    static void invokeEventCallback(MessageWrapper wrapper, WorkflowWrapper wfWrapper) {
        // invoke the callback using reflection
        try {
            String methodName = "on" + toCamelCase(wrapper.type);
            Method method = Workflow.class.getMethod(methodName, Map.class);
            method.invoke(wfWrapper.workflow, wrapper.parsedJson);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
    
    static String makeId() {
        String s = "";
        for (int i=0; i<16; i++) {
            int num = random.nextInt(16);
            s += Integer.toHexString(num);
        }
        return s;
    }

    static Map<String, Object> makeTarget(String sourceUri) {
        return Map.ofEntries(
                new AbstractMap.SimpleEntry<>("uris", new String[]{sourceUri})
        );
    }
    
    static String toCamelCase(String snakeCase) {
        String[] matches = snakeCase.split("_");
        String out = "";
        for(String match : matches) {
            out += Character.toUpperCase(match.charAt(0)) + match.substring(1);
        }
        return out;
    }

    static Map<String, Object> buildRequest(RequestType type, Map.Entry<String, Object> ...params) {
        Map<String, Object> map = Map.ofEntries(
                entry("_id", RelayUtils.makeId()),
                entry("_type", type.value())
        );

        Map<String, Object> paramsMap = Map.ofEntries(params);
        Map<String, Object> ret = new HashMap<>(paramsMap);
        ret.putAll(map);
        ret.putAll(paramsMap);
        return ret;
    }

    static Map<String, Object> buildRequest(RequestType type, String sourceUri, Map.Entry<String, Object> ...params) {
        Map map = buildRequest(type, params);
        map.put("_target", RelayUtils.makeTarget(sourceUri));
        return map;
    }

    
}

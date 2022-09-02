// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.Map.entry;

class RelayUtils {
    static Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(RelayUtils.class);
    
    static void invokeEventCallback(MessageWrapper messageWrapper, Relay relay) {
        // invoke the callback using reflection
        String methodName = "on" + toCamelCase(messageWrapper.type);
        try {
            Method method = Workflow.class.getMethod(methodName, Relay.class, messageWrapper.eventObject.getClass());
            method.invoke(relay.workflow, relay, messageWrapper.eventObject);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("Error invoking event callback for " + methodName, e);
        }
    }
    
    static String makeId() {
        StringBuilder s = new StringBuilder();
        for (int i=0; i<16; i++) {
            int num = random.nextInt(16);
            s.append(Integer.toHexString(num));
        }
        return s.toString();
    }

    static Map<String, Object> makeTarget(String sourceUri) {
        return Map.ofEntries(
                new AbstractMap.SimpleEntry<>("uris", new String[]{sourceUri})
        );
    }
    
    static String toCamelCase(String snakeCase) {
        String[] matches = snakeCase.split("_");
        StringBuilder out = new StringBuilder();
        for(String match : matches) {
            out.append(Character.toUpperCase(match.charAt(0))).append(match.substring(1));
        }
        return out.toString();
    }

    @SafeVarargs
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

    @SafeVarargs
    static Map<String, Object> buildRequest(RequestType type, String sourceUri, Map.Entry<String, Object> ...params) {
        Map<String, Object> map = buildRequest(type, params);
        map.put("_target", RelayUtils.makeTarget(sourceUri));
        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, Object> sanitize(Map<String, Object> map) {
        map.keySet().forEach(k -> {
            Object v = map.get(k);
            if (v instanceof Map) {
                map.put(k, sanitize((Map)v));
            }
            else if (v instanceof ArrayList && ((ArrayList<Double>) v).get(0) != null) {
                StringBuilder s = new StringBuilder();
                for (Double d : (ArrayList<Double>)v) {
                    s.append(Character.valueOf((char) d.byteValue()));
                }
                map.put(k, s.toString());
            }
        });
        return map;
    }


}

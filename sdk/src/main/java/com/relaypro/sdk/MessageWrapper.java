package com.relaypro.sdk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MessageWrapper {
    
    public String messageJson;
    public Map<String, Object> parsedJson;
    public String eventOrResponse;      // event or response
    public String _type;            // the _type field from the message: wf_api_<type>_[event|response]
    public String type;             // the parsed message type from the _type field ie say, start, etc

    public boolean stopped = false;         // used to notify threads that the workflow has been stopped

    private static final Pattern eventPattern = Pattern.compile("^wf_api_(\\w+)_event$");
    private static final Pattern responsePattern = Pattern.compile("^wf_api_(\\w+)_response$");

    public static MessageWrapper parseMessage(String message) {
        MessageWrapper wrapper = new MessageWrapper();
        wrapper.messageJson = message;
        parseMessage(wrapper);
        return wrapper;
    }

    private static void parseMessage(MessageWrapper wrapper) {
        // pass through both regexes, see which matches
        getMessageType(wrapper);

        Matcher m = eventPattern.matcher(wrapper._type);
        if (m.matches()) {
            String messageType = m.group(1);
            wrapper.eventOrResponse = "event";
            wrapper.type = messageType;
        }
        else {
            m = responsePattern.matcher(wrapper._type);
            if (m.matches()) {
                String messageType = m.group(1);
                wrapper.eventOrResponse = "response";
                wrapper.type = messageType;
            }
        }
    }
    
    private static void getMessageType(MessageWrapper wrapper) {
        Gson gson = new Gson();
        Map<String, Object> msgJson = gson.fromJson(wrapper.messageJson, new TypeToken<Map<String, Object>>() {}.getType());
        wrapper.parsedJson = sanitize(msgJson);
        String type = (String)msgJson.get("_type");
        wrapper._type = type;
    }

    private static Map<String, Object> sanitize(Map<String, Object> map) {
        map.keySet().forEach(k -> {
            Object v = map.get(k);
            if (v instanceof Map) {
                map.put(k, sanitize((Map)v));
            }
            else if (v instanceof ArrayList && ((ArrayList<Double>)v).get(0) instanceof Double) {
                String s = "";
                for (Double d : (ArrayList<Double>)v) {
                    s += String.valueOf(Character.valueOf((char)d.byteValue()));
                }
                map.put(k, s);
            }
        });
        return map;
    }
    
    static MessageWrapper stopMessage() {
        MessageWrapper mw = new MessageWrapper();
        mw.stopped = true;
        return mw;
    }
    
}

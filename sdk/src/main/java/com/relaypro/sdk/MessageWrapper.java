// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MessageWrapper {
    
    public String messageJson;
    public Map<String, Object> parsedJson;
    public Object eventObject;          // if this is an event, the parsed class representing the event
    public String eventOrResponse;      // event or response
    public String _type;            // the _type field from the message: wf_api_<type>_[event|response]
    public String type;             // the parsed message type from the _type field ie say, start, etc

    public boolean stopped = false;         // used to notify threads that the workflow has been stopped

    private static final Pattern eventPattern = Pattern.compile("^wf_api_(\\w+)_event$");
    private static final Pattern responsePattern = Pattern.compile("^wf_api_(\\w+)_response$");
    
    private static Logger logger = LoggerFactory.getLogger(MessageWrapper.class);
    
    public static MessageWrapper parseMessage(String message) {
        MessageWrapper wrapper = new MessageWrapper();
        wrapper.messageJson = message;
        parseMessage(wrapper);
        return wrapper;
    }

    private static void parseMessage(MessageWrapper wrapper) {
        // pass through both regexes, see which matches
        Gson gson = new Gson();
        Map<String, Object> msgJson = gson.fromJson(wrapper.messageJson, new TypeToken<Map<String, Object>>() {}.getType());
        wrapper.parsedJson = RelayUtils.sanitize(msgJson);
        String type = (String)msgJson.get("_type");
        wrapper._type = type;

        Matcher m = eventPattern.matcher(type);
        if (m.matches()) {
            String messageType = m.group(1);
            wrapper.eventOrResponse = "event";
            wrapper.type = messageType;
            EventType eventType = EventType.getByType(type);
            if (eventType != null) {
                wrapper.eventObject = gson.fromJson(wrapper.messageJson, eventType.eventClass());
            }
            else {
                logger.error("Unknown event type: " + type);
            }
        }
        else {
            m = responsePattern.matcher(type);
            if (m.matches()) {
                String messageType = m.group(1);
                wrapper.eventOrResponse = "response";
                wrapper.type = messageType;
            }
        }
    }
    
    
    static MessageWrapper stopMessage() {
        MessageWrapper mw = new MessageWrapper();
        mw.stopped = true;
        return mw;
    }
    
}

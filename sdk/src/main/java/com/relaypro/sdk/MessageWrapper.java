package com.relaypro.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MessageWrapper {
    
    public String messageJson;
    public JsonObject parsedJson;
    public String eventOrResponse;      // event or response
    public String _type;            // the _type field from the message: wf_api_<type>_[event|response]
    public String type;             // the parsed message type from the _type field ie say, start, etc


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
            System.out.println("event type: " + messageType);
            wrapper.eventOrResponse = "event";
            wrapper.type = messageType;
        }
        else {
            m = responsePattern.matcher(wrapper._type);
            if (m.matches()) {
                String messageType = m.group(1);
                System.out.println("response type: " + messageType);
                wrapper.eventOrResponse = "response";
                wrapper.type = messageType;
            }
        }
    }
    
    private static void getMessageType(MessageWrapper wrapper) {
        JsonElement element = JsonParser.parseString(wrapper.messageJson);
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            wrapper.parsedJson = obj;
            String type = obj.get("_type").getAsString();
            wrapper._type = type;
            System.out.println("message has _type "+type);
        }
    }
    
}

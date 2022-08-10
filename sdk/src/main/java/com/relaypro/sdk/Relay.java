// Copyright © 2022 Relay Inc.
package com.relaypro.sdk;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.relaypro.sdk.types.*;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
// import com.google.gson.reflect.TypeToken;
// import java.net.URI;
// import java.net.http.HttpClient;
// import java.net.http.HttpRequest;
// import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.spec.EncodedKeySpec;
import java.time.Duration;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;


import com.google.gson.Gson;
// import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Map.entry;

public class Relay {

    private static final Map<String, Workflow> WORKFLOWS = new HashMap<>();
    private static final Map<Session, Relay> runningWorkflowsBySession = new ConcurrentHashMap<>();

    static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static Logger logger = LoggerFactory.getLogger(Relay.class);

    private static final int RESPONSE_TIMEOUT_SECS = 10;


    // holds the Workflow clone, and the session
    Workflow workflow;
    private Session session;
    private Future<String> workflowFuture;      // long running future that runs the workflow logic
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    BlockingQueue<MessageWrapper> messageQueue = new LinkedBlockingDeque<>();
    private Map<String, Call> pendingRequests = new ConcurrentHashMap<>();

    private Relay(Workflow workflow, Session session) {
        this.workflow = workflow;
        this.session = session;
        // start a thread that handles running the workflow code
        this.workflowFuture = executor.submit(new Worker(this));
        runningWorkflowsBySession.put(session, this);
//        runningWorkflowsByWorkflow.put(workflow, this);
    }


    public static void addWorkflow(String name, Workflow wf) {
        logger.debug("Adding workflow with name: " + name);
        WORKFLOWS.put(name, wf);
    }

    public static void startWorkflow(Session session, String workflowName) {
        // TODO make a clone of the workflow object, and create a container for that clone and the websocket session 

        Workflow wf = WORKFLOWS.get(workflowName);
        if (wf == null) {
            logger.error("No workflow registered with name " + workflowName);
            // TODO stop ws connection
            stopWorkflow(session, "invalid_workflow_name");
            return;
        }

        // create a clone of the workflow object
        Workflow wfClone = null;
        try {
            wfClone = (Workflow) wf.clone();
        } catch (CloneNotSupportedException e) {
            logger.error("Error cloning workflow", e);
            // stop ws connection
            stopWorkflow(session, "worflow_instantiation_error");
            return;
        }

        Relay wrapper = new Relay(wfClone, session);

        logger.debug("Websocket connected to workflow " + workflowName + " " + wrapper);
    }

    public static void stopWorkflow(Session session, String reason) {
        logger.debug("Shutting down workflow with reason: " + reason);
        try {
            session.close();
        } catch (IOException e) {
            logger.error("Error when shutting down workflow", e);
        }

        // shut down worker, if running, by sending poison pill to it's message queue and call queues
        Relay wfWrapper = runningWorkflowsBySession.get(session);
        if (wfWrapper != null) {
            wfWrapper.pendingRequests.forEach((id, call) -> {
                call.responseQueue.add(MessageWrapper.stopMessage());
            });
            wfWrapper.messageQueue.add(MessageWrapper.stopMessage());
        }
    }

    // Called on websocket thread, 
    public static void receiveMessage(Session session, String message) {
        // decode what message type it is, event/response, get the running wf, call the appropriate callback
        Relay wfWrapper = runningWorkflowsBySession.get(session);
        MessageWrapper msgWrapper = MessageWrapper.parseMessage(message);

        if (msgWrapper.eventOrResponse == "event") {
            // prompt, progress, and speech events need to be sent to the matching calls as well as to event callbacks
            if (msgWrapper.type.equals("prompt") || msgWrapper.type.equals("progress") || msgWrapper.type.equals("speech")) {
                handleResponse(msgWrapper, wfWrapper);
            }

            // send this through the message queue to the worker thread
            wfWrapper.messageQueue.add(msgWrapper);

            // if this is a stop event, need to shut everything down after we just called the onStop callback
            if (msgWrapper.type.equals("stop")) {
                StopEvent stopEvent = (StopEvent)msgWrapper.eventObject;
                stopWorkflow(session, stopEvent.reason);
            }
        }
        // if response, match to request
        else if (msgWrapper.eventOrResponse == "response") {
            handleResponse(msgWrapper, wfWrapper);
        }

    }
    private static void handleResponse(MessageWrapper msgWrapper, Relay wfWrapper) {
        String id = null;
        if (msgWrapper.parsedJson.containsKey("_id")) {
            id = (String) msgWrapper.parsedJson.get("_id");
        } else if (msgWrapper.parsedJson.containsKey("id")) {
            id = (String) msgWrapper.parsedJson.get("id");
        } else if (msgWrapper.parsedJson.containsKey("request_id")) {
            id = (String) msgWrapper.parsedJson.get("request_id");
        }
        if (id == null) {
            return;
        }
        Call matchingCall = wfWrapper.pendingRequests.get(id);
        matchingCall.responseQueue.add(msgWrapper);
    }

    private MessageWrapper sendRequest(Map<String, Object> message) throws EncodeException, IOException, InterruptedException {
        return sendRequest(message, false);
    }

    private MessageWrapper sendRequest(Map<String, Object> message, boolean waitForPromptEnd) throws EncodeException, IOException, InterruptedException {
//        Relay wrapper = runningWorkflowsByWorkflow.get(workflow);
    
        String id = (String) message.get("_id");
        String msgJson = gson.toJson(message);
        // gson.toJson encodes "=" to a unicharacter, encode it back to "="
        if(msgJson.contains("\\u003d")) {
            msgJson = msgJson.replace("\\u003d", "=");
        }

        // store a call for this request that all incoming response/prompt/progress messages can be passed back to us through
        Call call = new Call();
        this.pendingRequests.put(id, call);

        // send the request
        this.session.getBasicRemote().sendObject(msgJson);

        logger.debug("--> Message sent: " + msgJson);

        // block to listen for matching response
        // if waitForPromptEnd is true, return the matching response as soon as it is received
        // else store the response, and wait until a prompt end is seen, then return the response message
        // in either case, progress events reset the listen timeout
        MessageWrapper resp = null;
        boolean receivedSpeechEvent = false;
        while (true) {
            
            // TODO set timeout for listen
            MessageWrapper response = call.responseQueue.poll(RESPONSE_TIMEOUT_SECS, TimeUnit.SECONDS);
            if(receivedSpeechEvent) {
                return response;
            }
            if (response == null) {
                logger.error("Timed out waiting for response");
                return null;
            } else if (response.stopped) {
                logger.debug("Halting listening for responses, received stop command");
                return null;
            }

            if (response.type.equals("prompt")) {
                if (response.parsedJson.get("type").equals("stopped") && waitForPromptEnd) {
                    return resp;
                }
                // otherwise do nothing with it
            } else if (response.type.equals("progress")) {
                // nothing to do here, will continue listening with a fresh timeout 
            } else if (response.type.equals("error")) {
                // if an error was returned, then no response will be, so return null
                logger.error("Error returned for call: " + response.messageJson);
                return null;
            } else if (response.eventOrResponse.equals("response") || response.eventOrResponse.equals("event")) {
                // matching response
                if (!waitForPromptEnd) {
                    return response;
                }
                // need to wait for prompt end, save the response to return then
                resp = response;
                // for listen, notify that we received a speech event
                receivedSpeechEvent = true;
            }
        }
    }


    // ##### API ################################################

    // Returns error if any, null otherwise
    public String startInteraction(String sourceUri, String name, Object options) {
        logger.debug("Starting Interaction for source uri " + sourceUri);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StartInteraction, sourceUri,
                entry("name", name),
                entry("options", options == null ? new Object() : options)
        );

        try {
            MessageWrapper resp = sendRequest(req);
            return (String) resp.parsedJson.get("error");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error starting interaction", e);
        }
        return null;
    }

    public String endInteraction(String sourceUri, String name) {
        logger.debug("Ending Interaction for source uri " + sourceUri);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.EndInteraction, sourceUri,
            entry("name", name)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            return (String) resp.parsedJson.get("error");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error ending interaction", e);
        }
        return null;
    }

    public String say(String sourceUri, String text) {
        return say(sourceUri, text, LanguageType.English);
    }

    public String say(String sourceUri, String text, LanguageType lang) {
        return say(sourceUri, text, lang, false);
    }

    public void sayAndWait(String sourceUri, String text) {
        sayAndWait(sourceUri, text, LanguageType.English);
    }

    public void sayAndWait(String sourceUri, String text, LanguageType lang) {
        say(sourceUri, text, lang, true);
    }

    private String say(String sourceUri, String text, LanguageType lang, boolean wait) {
        logger.debug("Saying " + text + " in " + lang.value() + " to " + sourceUri);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Say, sourceUri,
                entry("text", text),
                entry("lang", lang.value())
        );

        try {
            MessageWrapper resp = sendRequest(req, wait);
            return resp != null ? (String) resp.parsedJson.get("id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error saying text", e);
        }
        return null;
    }

    public String listen(String sourceUri, String requestId) {
        String[] phrases = {};
        return listen(sourceUri, requestId, phrases, false, LanguageType.English, 30);
    }

    public String listen(String sourceUri, String requestId, String[] phrases, boolean transcribe, LanguageType lang, int timeout) {
        logger.debug("Listening to " + sourceUri);
        
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Listen, sourceUri,
            entry("request_id", requestId),
            entry("phrases", phrases),
            entry("transcribe", transcribe),
            entry("timeout", timeout),
            entry("alt_lang", lang.value())
        );
        try {
            MessageWrapper resp = sendRequest(req, true);
            return resp != null ? (String) resp.parsedJson.get("text") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error listening", e);
        }
        return null;
    }

    public String play(String sourceUri, String filename) {
        return play(sourceUri, filename, false);
    }

    public void playAndWait(String sourceUri, String filename) {
        play(sourceUri, filename, true);
    }

    private String play(String sourceUri, String filename, boolean wait) {
        logger.debug("Playing file: " + filename);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Play, sourceUri,
                entry("filename", filename)
        );

        try {
            MessageWrapper resp = sendRequest(req,  wait);
            return resp != null ? (String) resp.parsedJson.get("id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error playing file", e);
        }
        return null;
    }

    public void stopPlayback( String sourceUri, String[] ids) {
        logger.debug("Stopping playback for: " + ids);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopPlayback, sourceUri,
                entry("ids", ids)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping playback", e);
        }
    }

    public void playUnreadInboxMessages(String sourceUri) {
        logger.debug("Playing unread messages" );
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PlayInboxMessages, sourceUri);

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error playing unread inbox messages", e);
        }
    }

    public int getUnreadInboxSize(String sourceUri) {
        logger.debug("Getting unread inbox size");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.InboxCount, sourceUri);

        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? Integer.parseInt(resp.parsedJson.get("count").toString()) : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error retrieving inbox count", e);
        }
        return -1;
    }

    public  void setTimer( TimerType timerType, String name, long timeout, TimeoutType timeoutType) {
        logger.debug("Setting timer " + timerType.value() + " named " + name + " for " + timeout + " " + timeoutType.value());
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetTimer,
                entry("type", timerType.value()),
                entry("name", name),
                entry("timeout", timeout),
                entry("timeout_type", timeoutType.value())
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting timer", e);
        }
    }

    public void clearTimer(String name) {
        logger.debug("Clearing timer named " + name);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.ClearTimer,
                entry("name", name)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error ", e);
        }
    }

    public void startTimer(int timeout) {
        logger.debug("Starting timer unnamed ");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StartTimer,
                entry("timeout", timeout)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error starting timer ", e);
        }
    }

    public void stopTimer() {
        logger.debug("Stopping timer unnamed ");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopTimer);
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping timer ", e);
        }
    }

    public String translate(String text, LanguageType from, LanguageType to) {
        logger.debug("Translating text");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Translate,
            entry("text", text),
            entry("from_lang", from.value()),
            entry("to_lang", to.value())
        );
        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? (String) resp.parsedJson.get("text") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error translating text", e);
        }
        return null;
    }

    public void createIncident( String originator, String itype) {
        logger.debug("Creating incident");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.CreateIncident, 
            entry("type", itype),
            entry("originator_uri", originator)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error creating incident", e);
        }
    }

    public void resolveIncident( String incidentId, String reason) {
        logger.debug("Resolving incident");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.ResolveIncident, 
            entry("incident_id", incidentId),
            entry("reason", reason)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error resolving incident", e);
        }
    }

    public void logUserMessage( String message, String deviceUri, String category) {
        logger.debug("Logging user message");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.LogAnalytics,
            entry("content", message),
            entry("content_type", "text/plain"),
            entry("category", category),
            entry("device_uri", deviceUri)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error logging user message", e);
        }
    }

    public void logMessage( String message, String category) {
        logger.debug("Logging message");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.LogAnalytics,
            entry("content", message),
            entry("content_type", "text/plain"),
            entry("category", category)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error logging message", e);
        }
    }

    public void switchLedOn( String sourceUri, int index, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor(Integer.toString(index), color);
        setLeds( sourceUri, LedEffect.STATIC, ledInfo.ledMap);
    }

    public void switchAllLedOn( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.STATIC, ledInfo.ledMap);
    }

    public  void switchAllLedOff( String sourceUri) {
        LedInfo ledInfo = new LedInfo();
        setLeds( sourceUri, LedEffect.OFF, ledInfo.ledMap);
    }

    public  void rainbow( String sourceUri, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(rotations);
        setLeds( sourceUri, LedEffect.RAINBOW, ledInfo.ledMap);
    }

    public  void rotate( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(-1);
        ledInfo.setColor("1", color);
        setLeds( sourceUri, LedEffect.ROTATE, ledInfo.ledMap);
    }

    public  void flash( String sourceUri, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setCount(count);
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.FLASH, ledInfo.ledMap);
    }

    public  void breathe( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.BREATHE, ledInfo.ledMap);
    }

    private  void setLeds( String sourceUri, LedEffect effect, Map<String, Object> args) {
        logger.debug("Setting leds: " + effect.value() + " " + args);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetLeds, sourceUri,
                entry("effect", effect.value()),
                entry("args", args)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting leds", e);
        }
    }

    public  void vibrate( String sourceUri, long[] pattern) {
        logger.debug("Vibrating: " + pattern);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Vibrate, sourceUri,
                entry("pattern", pattern)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error vibrating", e);
        }
    }

    public void setVar(String name, String value) {
        logger.debug("Setting variable: " + name + " with value " +  value);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetVar,
                entry("name", name),
                entry("value", value)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting variable", e);
        }
    }

    public String getVar(String name, String defaultValue) {
        logger.debug("Getting variable: " + name + " with default value " +  defaultValue);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GetVar,
                entry("name", name),
                entry("value", defaultValue)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            if((String) resp.parsedJson.get("value") == null){
                return defaultValue;
            }
            return (String) resp.parsedJson.get("value");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error getting variable", e);
        }
        return null;
    }

    public int getNumberVar(String name, int defaultValue) {
        return Integer.parseInt(this.getVar(name, Integer.toString(defaultValue)));
    }

    public void unsetVar(String name) {
        logger.debug("Unsetting variable: " + name);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.UnsetVar,
                entry("name", name)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error unsetting variable", e);
        }
    }

    private void sendNotification(String target, String originator, String type, String text, String name) {
        logger.debug("Sending notification with name: " + name);
        Map<String, Object> dict = new HashMap<String, Object>();

        // Create a String array that contains the group URNs
        String[] targets = {target};

        // Make a TargetUri object that contains the group URNs array as a field
        TargetUri targetUri = new TargetUri(targets);

        // Fill out the request, using the new targetUri object
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SendNotification,
                entry("_target", targetUri),
                entry("originator", originator),
                entry("type", type),
                entry("name", name),
                entry("text", text),
                entry("target", targetUri),
                entry("push_opts", dict)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error sending notification", e);
        }
    }

    public boolean isGroupMember(String groupNameUri, String potentialMemberNameUri) {
        String groupName = RelayUri.parseGroupName(groupNameUri);
        String deviceName = RelayUri.parseDeviceName(potentialMemberNameUri);
        String groupUri = RelayUri.groupMember(groupName, deviceName);
        logger.debug("Checking if  " + potentialMemberNameUri + " is a group member.");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GroupQuery,
                entry("query", "is_member"),
                entry("group_uri", groupUri)
        );

        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? (Boolean) resp.parsedJson.get("is_member") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error checking if group member", e);
        }
        return false;
    }

    public void alert(String target, String originator, String name, String text) {
        sendNotification(target, originator, "alert", text, name);
    }

    public void cancelAlert(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    public void broadcast (String target, String originator, String name, String text) {
        sendNotification(target, originator, "broadcast", text, name);
    }

    public void cancelBroadcast(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    public void notify(String target, String originator, String name, String text) {
        sendNotification(target, originator, "notify", text, name);
    }

    public void cancelNotify(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    public  String getDeviceName( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Name, refresh);
        return resp != null ? resp.name : null;
    }

    public  String getDeviceId( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Id, refresh);
        return resp != null ? resp.id : null;
    }

    public  String getDeviceLocation( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Address, refresh);
        return resp != null ? resp.address : null;
    }

    public String getDeviceAddress( String sourceUri, boolean refresh) {
        return this.getDeviceLocation(sourceUri, refresh);
    }

    public  double[] getDeviceCoordinates( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.LatLong, refresh);
        return resp != null ? resp.latlong : null;
    }

    public double[] getDeviceLatLong( String sourceUri, boolean refresh) {
        return this.getDeviceCoordinates(sourceUri, refresh);
    }

    public  String getDeviceIndoorLocation( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.IndoorLocation, refresh);
        return resp != null ? resp.indoor_location : null;
    }

    public  Integer getDeviceBattery( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Battery, refresh);
        return resp != null ? resp.battery : null;
    }

    public  String getDeviceType( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Type, refresh);
        return resp != null ? resp.type : null;
    }

    public  String getDeviceUsername( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Username, refresh);
        return resp != null ? resp.username : null;
    }

    public  Boolean getDeviceLocationEnabled( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.LocationEnabled, refresh);
        return resp != null ? resp.location_enabled : null;
    }

    public  DeviceInfoResponse getDeviceInfo( String sourceUri, DeviceInfoQueryType query, boolean refresh) {
        logger.debug("Getting device info: " + query + " refresh: " + refresh);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GetDeviceInfo, sourceUri,
                entry("query", query.value()),
                entry("refresh", refresh)
        );

        try {
            MessageWrapper resp = sendRequest(req);
            if (resp != null) {
                return gson.fromJson(resp.messageJson, DeviceInfoResponse.class);
            }
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error getting device info", e);
        }
        return null;
    }

    // setDeviceMode is currently not supported

    // public  void setDeviceMode( String sourceUri, DeviceMode mode) {
    //     logger.debug("Setting device mode: " + mode.value());
    //     Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceMode, sourceUri,
    //             entry("mode", mode.value())
    //     );

    //     try {
    //         sendRequest(req);
    //     } catch (EncodeException | IOException | InterruptedException e) {
    //         logger.error("Error setting device mode", e);
    //     }
    // }

    public  void setDeviceName( String sourceUri, String name) {
        setDeviceInfo( sourceUri, DeviceField.Label, name);
    }

    public void enableLocation( String sourceUri) {
        setLocationEnabled(sourceUri, true);
    }

    public void disableLocation( String sourceUri) {
        setLocationEnabled(sourceUri, false);
    }

    public void setLocationEnabled( String sourceUri, boolean enabled) {
        setDeviceInfo( sourceUri, DeviceField.LocationEnabled, String.valueOf(enabled));
    }

    // setDeviceChannel is currently not supported

    // public void setDeviceChannel(String sourceUri, String channel) {
    //     setDeviceInfo( sourceUri, DeviceField.Channel, channel);
    // }

    public  void setChannel( String sourceUri, String channelName, boolean suppressTTS, boolean disableHomeChannel) {
        logger.debug("Setting channel: " + channelName + ": supresstts:" + suppressTTS + " disableHomeChannel:" + disableHomeChannel);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetChannel, sourceUri,
                entry("channel_name", channelName),
                entry("suppress_tts", suppressTTS),
                entry("disable_home_channel", disableHomeChannel)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting channel", e);
        }
    }

    private  void setDeviceInfo( String sourceUri, DeviceField field, String value) {
        logger.debug("Setting device info: " + field + ": " + value);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceInfo, sourceUri,
                entry("field", field.value()),
                entry("value", value)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting device info", e);
        }
    }

    public  void setUserProfile( String sourceUri, String username, boolean force) {
        logger.debug("Setting user profile: " + username + ": " + force);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetUserProfile, sourceUri,
                entry("username", username),
                entry("force", force)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting user profile", e);
        }
    }

    public void enableHomeChannel(String target) {
        setHomeChannelState(target, false);
    }

    public void disableHomeChannel(String target) {
        setHomeChannelState(target, false);
    }

    private void setHomeChannelState(String sourceUri, boolean enabled) {
        logger.debug("Setting home channel state.");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetHomeChannelState, sourceUri,
            entry("enabled", enabled)               
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting home channel state", e);
        }
    }

    // restart/powering down device is currently not supported

    // public  void restartDevice( String sourceUri) {
    //     logger.debug("Restarting device: " + sourceUri);
    //     powerDownDevice( sourceUri, true);
    // }

    // public  void powerDownDevice( String sourceUri) {
    //     logger.debug("Powering down device: " + sourceUri);
    //     powerDownDevice( sourceUri, true);
    // }

    // private  void powerDownDevice( String sourceUri, boolean restart) {
    //     Map<String, Object> req = RelayUtils.buildRequest(RequestType.PowerOff, sourceUri,
    //             entry("restart", restart)
    //     );

    //     try {
    //         sendRequest(req);
    //     } catch (EncodeException | IOException | InterruptedException e) {
    //         logger.error("Error powering down device", e);
    //     }
    // }

    public  void terminate() {
        logger.debug("Terminating workflow");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Terminate);
//        WorkflowWrapper wrapper = runningWorkflowsByWorkflow.get(workflow);
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error terminating workflow", e);
        }

    }

    // HELPER FUNCTIONS ##############

    public static String getStartEventSourceUri(Map<String, Object> startEvent) {
        Map trigger = (Map) startEvent.get("trigger");
        Map args = (Map) trigger.get("args");
        return (String) args.get("source_uri");
    }


    String serverHostname = "all-main-qa-ibot.nocell.io";
    String version = "relay-sdk-java/2.0.0";
    String auth_hostname = "auth.relaygo.info";

    private String updateAccessToken(String refreshToken, String clientId) {
        String grantUrl = "https://" + auth_hostname + "/oauth2/token";
        // Map<String, String> grantHeaders = new LinkedHashMap<String, String>();
        // grantHeaders.put("User-Agent", version);

        Map<String, String> grantPayload = new LinkedHashMap<String, String>();
        grantPayload.put("client_id", clientId);
        grantPayload.put("grant_type", "refresh_token");
        grantPayload.put("refresh_token", refreshToken);
        Gson gson = new Gson();
        // Type gsonType = new TypeToken<LinkedHashMap>(){}.getType();
        // String gsonString = gson.toJson(payload.gsonType);
        String jsonStr = gson.toJson(grantPayload);
        logger.debug("JSON STRING: " + jsonStr);
        logger.debug("HTTPBODY: " + HttpRequest.BodyPublishers.ofString(jsonStr));
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(formEncodedContent(grantPayload))
                    .uri(URI.create(grantUrl))
                    .setHeader("User-Agent", version)
                    .setHeader("Content-Type", "application/json")
                    .build();
        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            logger.debug("RESPONSE BODY: " + response.body());
            String[] responseArray = response.body().split("\":\"|\",\"");
            logger.debug("THIS: " + responseArray[1]);
            return responseArray[1];
        } catch (IOException | InterruptedException e) {
            logger.debug("Received exception when trying to update access token: ", e);
        } 
        return null;
    }

    private static HttpRequest.BodyPublisher formEncodedContent(Map<String, String> data) {
        var encodeData = new StringBuilder();
        for(Map.Entry<String, String> entry : data.entrySet()) {
            if(encodeData.length() > 0) {
                encodeData.append("&");
            }

            encodeData.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            encodeData.append("=");
            encodeData.append(URLEncoder.encode((entry.getValue().toString()), StandardCharsets.UTF_8));
        }

        return HttpRequest.BodyPublishers.ofString(encodeData.toString());
    }

    public Map<String, String> triggerWorkflow(String accessToken, String refreshToken, String clientId, String workflowId, String subscriberId, String userId, String[] targets, Map<String, String> actionArgs) {
        String url = "https://" + serverHostname + "/ibot/workflow/" + workflowId + "?subscriber_id=" + subscriberId
                        + "&user_id=" + userId;
        // var queryParams = new LinkedHashMap<String, String>(); 
        // queryParams.put("subscriber_id", subscriberId);
        // queryParams.put("user_id", userId);

        Map<String, String> payload = new LinkedHashMap<String, String>();
        // var json = new JSON
        
        payload.put("action", "invoke");

        if (actionArgs != null) {
            payload.put("action_args", actionArgs.toString());
        }

        if (targets != null) {
            payload.put("target_device_ids", targets.toString());
        }

        Gson gson = new Gson();
        // Type gsonType = new TypeToken<LinkedHashMap>(){}.getType();
        // String gsonString = gson.toJson(payload.gsonType);
        String jsonStr = gson.toJson(payload);
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(jsonStr))
                    .uri(URI.create(url))
                    .setHeader("User-Agent", version)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
        
        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                logger.debug("Got 401, retrieving a new access token");
                accessToken = updateAccessToken(refreshToken, clientId);
                logger.debug("NEW ACCESS TOKEN: " + accessToken);
                httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();

                httpRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(jsonStr))
                    .uri(URI.create(url))
                    .setHeader("User-Agent", version)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
                
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }
            Map<String, String> returnVal = new LinkedHashMap<String, String>();
            returnVal.put("response", response.body());
            returnVal.put("accessToken", accessToken);
            return returnVal;
        } catch (IOException | InterruptedException e) {
            logger.error("Received error when sending request: ", e);
        } 
        return null;
    }

    public Map<String, String> fetchDevice(String accessToken, String refreshToken, String clientId, String subscriberId, String userId) {
        String url = "https://" + serverHostname + "/relaypro/api/v1/device/" + userId + "?subscriber_id=" + subscriberId;
        // String url = "https://" + serverHostname + "/relaypro/api/v1/device/" + userId;
        // var queryParams = new LinkedHashMap<String, String>();
        // queryParams.put("subscriber_id", subscriberId);
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(url))
                        .setHeader("User-Agent", version)
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                logger.debug("Got 401, retrieving a new access token");
                accessToken = updateAccessToken(refreshToken, clientId);
                logger.debug("new access token: " + accessToken);
                httpClient = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                httpRequest = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(url))
                        .setHeader("User-Agent", version)
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .build();
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }
            Map<String, String> returnVal = new LinkedHashMap<>();
            returnVal.put("response", response.body());
            returnVal.put("accessToken", accessToken);
            return returnVal;
        } catch (IOException | InterruptedException e) {
            logger.error("Received exception when retrieving device information", e);
        } 
        return null;
    }

}

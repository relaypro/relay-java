// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.relaypro.sdk.types.DeviceField;
import com.relaypro.sdk.types.DeviceInfoQueryType;
import com.relaypro.sdk.types.DeviceInfoResponse;
import com.relaypro.sdk.types.LanguageType;
import com.relaypro.sdk.types.LedEffect;
import com.relaypro.sdk.types.LedInfo;
import com.relaypro.sdk.types.StartEvent;
import com.relaypro.sdk.types.StopEvent;
import com.relaypro.sdk.types.TargetUri;
import com.relaypro.sdk.types.TimeoutType;
import com.relaypro.sdk.types.TimerType;
import com.relaypro.sdk.types.Trigger;

import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.HttpRetryException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static java.util.Map.entry;

public class Relay {

    private static final Map<String, Workflow> WORKFLOWS = new HashMap<>();
    private static final Map<Session, Relay> runningWorkflowsBySession = new ConcurrentHashMap<>();

    static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static final Logger logger = LoggerFactory.getLogger(Relay.class);

    private static final int RESPONSE_TIMEOUT_SECS = 10;

    // holds the Workflow clone, and the session
    Workflow workflow;
    private final Session session;
    BlockingQueue<MessageWrapper> messageQueue = new LinkedBlockingDeque<>();
    private final Map<String, Call> pendingRequests = new ConcurrentHashMap<>();

    private Relay(Workflow workflow, Session session) {
        this.workflow = workflow;
        this.session = session;
        // start a thread that handles running the workflow code
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Worker(this));
        runningWorkflowsBySession.put(session, this);
    }

    public static void addWorkflow(String name, Workflow wf) {
        logger.debug("Adding workflow with name: " + name);
        WORKFLOWS.put(name, wf);
    }

    public static void startWorkflow(Session session, String workflowName) {
        Workflow wf = WORKFLOWS.get(workflowName);
        if (wf == null) {
            logger.error("No workflow registered with name " + workflowName);
            stopWorkflow(session, "invalid_workflow_name");
            return;
        }

        // create a clone of the workflow object
        Workflow wfClone;
        try {
            wfClone = (Workflow) wf.clone();
        } catch (CloneNotSupportedException e) {
            logger.error("Error cloning workflow", e);
            // stop ws connection
            stopWorkflow(session, "workflow_instantiation_error");
            return;
        }

        new Relay(wfClone, session);

        logger.info("Workflow instance started for {}", workflowName);
    }

    public static void stopWorkflow(Session session, String reason) {
        logger.info("Workflow instance terminating, reason: {}", reason);
        try {
            session.close();
        } catch (IOException e) {
            logger.error("Error when shutting down workflow", e);
        }

        // shut down worker, if running, by sending poison pill to its message queue and call queues
        Relay wfWrapper = runningWorkflowsBySession.get(session);
        if (wfWrapper != null) {
            wfWrapper.pendingRequests.forEach((id, call) -> call.responseQueue.add(MessageWrapper.stopMessage()));
            wfWrapper.messageQueue.add(MessageWrapper.stopMessage());
        }
    }

    // Called on websocket thread, 
    public static void receiveMessage(Session session, String message) {
        // decode what message type it is, event/response, get the running wf, call the appropriate callback
        Relay wfWrapper = runningWorkflowsBySession.get(session);
        MessageWrapper msgWrapper = MessageWrapper.parseMessage(message);

        if (msgWrapper.eventOrResponse.equals("event")) {
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
        else if (msgWrapper.eventOrResponse.equals("response")) {
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
        String id = (String) message.get("_id");
        String msgJson = gson.toJson(message);
        
        // gson.toJson encodes "=" to unicode, encode it back to "="
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

    // Not every public method in the SDK will get used by an application,
    // so we will mark the optional public methods as @SuppressWarnings("unused").
    // Similarly, if the SDK method returns an information value that doesn't have
    // to get used, then mark those @SuppressWarnings("UnusedReturnValue").

    // Returns error if any, null otherwise
    @SuppressWarnings("UnusedReturnValue")
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

    @SuppressWarnings("UnusedReturnValue")
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

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String say(String sourceUri, String text) {
        return say(sourceUri, text, LanguageType.English);
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String say(String sourceUri, String text, LanguageType lang) {
        return say(sourceUri, text, lang, false);
    }

    @SuppressWarnings("unused")
    public void sayAndWait(String sourceUri, String text) {
        sayAndWait(sourceUri, text, LanguageType.English);
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public String listen(String sourceUri, String requestId) {
        String[] phrases = {};
        return listen(sourceUri, requestId, phrases, false, LanguageType.English, 30);
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String play(String sourceUri, String filename) {
        return play(sourceUri, filename, false);
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void stopPlayback( String sourceUri, String[] ids) {
        logger.debug("Stopping playback for: " + Arrays.toString(ids));
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopPlayback, sourceUri,
                entry("ids", ids)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping playback", e);
        }
    }

    @SuppressWarnings("unused")
    public void playUnreadInboxMessages(String sourceUri) {
        logger.debug("Playing unread messages" );
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PlayInboxMessages, sourceUri);

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error playing unread inbox messages", e);
        }
    }

    @SuppressWarnings("unused")
    public int getUnreadInboxSize(String sourceUri) {
        logger.debug("Getting unread inbox size");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.InboxCount, sourceUri);

        try {
            MessageWrapper resp = sendRequest(req);
            if (resp != null) {
                return Integer.parseInt(resp.parsedJson.get("count").toString());
            } else {
                logger.error("Error retrieving inbox count: null response");
                return -1;
            }
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error retrieving inbox count", e);
        }
        return -1;
    }

    @SuppressWarnings("unused")
    public void setTimer(TimerType timerType, String name, long timeout, TimeoutType timeoutType) {
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void stopTimer() {
        logger.debug("Stopping timer unnamed ");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopTimer);
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping timer ", e);
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public String placeCall(String target, String uri) {
        logger.debug("Placing call");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PlaceCall, target,
                entry("uri", uri)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? (String) resp.parsedJson.get("call_id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error placing call", e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public void answerCall(String target, String call_id) {
        logger.debug("Answering call");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.AnswerCall, target,
                entry("call_id", call_id)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error answering call", e);
        }
    }

    @SuppressWarnings("unused")
    public void hangupCall(String target, String call_id) {
        logger.debug("Hanging up call");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.HangupCall, target,
                entry("call_id", call_id)
        );
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error hanging up call", e);
        }
    }
    
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void switchLedOn( String sourceUri, int index, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor(Integer.toString(index), color);
        setLeds( sourceUri, LedEffect.STATIC, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void switchAllLedOn( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.STATIC, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void switchAllLedOff( String sourceUri) {
        LedInfo ledInfo = new LedInfo();
        setLeds( sourceUri, LedEffect.OFF, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void rainbow( String sourceUri, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(rotations);
        setLeds( sourceUri, LedEffect.RAINBOW, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void rotate( String sourceUri, String color, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(rotations);
        ledInfo.setColor("1", color);
        setLeds( sourceUri, LedEffect.ROTATE, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void flash( String sourceUri, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setCount(count);
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.FLASH, ledInfo.ledMap);
    }

    @SuppressWarnings("unused")
    public void breathe( String sourceUri, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setCount(count);
        ledInfo.setColor("ring", color);
        setLeds( sourceUri, LedEffect.BREATHE, ledInfo.ledMap);
    }

    private void setLeds( String sourceUri, LedEffect effect, Map<String, Object> args) {
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

    @SuppressWarnings("unused")
    public void vibrate( String sourceUri, int[] pattern) {
        logger.debug("Vibrating: " + Arrays.toString(pattern));
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Vibrate, sourceUri,
                entry("pattern", pattern)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error vibrating", e);
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public String getVar(String name, String defaultValue) {
        logger.debug("Getting variable: " + name + " with default value " +  defaultValue);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GetVar,
                entry("name", name),
                entry("value", defaultValue)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            if( resp.parsedJson.get("value") == null){
                return defaultValue;
            }
            return (String) resp.parsedJson.get("value");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error getting variable", e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public int getNumberVar(String name, int defaultValue) {
        return Integer.parseInt(this.getVar(name, Integer.toString(defaultValue)));
    }

    @SuppressWarnings("unused")
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

        // set up empty pushOpts
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        Map<String, Object> pushOpts = new HashMap<>();

        // Create a String array that contains the group URNs
        String[] targets = {target};

        // Make a TargetUri object that contains the group URNs array as a field
        TargetUri targetUri = new TargetUri(targets);

        // Fill out the request, using the new targetUri object
        Map<String, Object> req;
        if ((originator != null) && (text != null)) {
            req = RelayUtils.buildRequest(RequestType.SendNotification,
                    entry("_target", targetUri),
                    entry("originator", originator),
                    entry("type", type),
                    entry("name", name),
                    entry("text", text),
                    entry("target", targetUri),
                    entry("push_opts", pushOpts));
        } else {
            // Map.Entry will throw an NPE on a null value, so fence for that
            req = RelayUtils.buildRequest(RequestType.SendNotification,
                    entry("_target", targetUri),
                    entry("type", type),
                    entry("name", name),
                    entry("target", targetUri),
                    entry("push_opts", pushOpts));
        }

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error sending notification", e);
        }
    }

    @SuppressWarnings("unused")
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
            if (resp != null) {
                return (Boolean) resp.parsedJson.get("is_member");
            } else {
                logger.error("Error checking if group member: null answer");
            }
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error checking if group member", e);
        }
        return false;
    }

    @SuppressWarnings("unused")
    public void alert(String target, String originator, String name, String text) {
        sendNotification(target, originator, "alert", text, name);
    }

    @SuppressWarnings("unused")
    public void cancelAlert(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    @SuppressWarnings("unused")
    public void broadcast (String target, String originator, String name, String text) {
        sendNotification(target, originator, "broadcast", text, name);
    }

    @SuppressWarnings("unused")
    public void cancelBroadcast(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    @SuppressWarnings("unused")
    public String getDeviceName( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Name, refresh);
        return resp != null ? resp.name : null;
    }

    @SuppressWarnings("unused")
    public String getDeviceId( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Id, refresh);
        return resp != null ? resp.id : null;
    }

    @SuppressWarnings("unused")
    public String getDeviceLocation( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Address, refresh);
        return resp != null ? resp.address : null;
    }

    @SuppressWarnings("unused")
    public String getDeviceAddress( String sourceUri, boolean refresh) {
        return this.getDeviceLocation(sourceUri, refresh);
    }

    @SuppressWarnings("unused")
    public double[] getDeviceCoordinates( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.LatLong, refresh);
        return resp != null ? resp.latlong : null;
    }

    @SuppressWarnings("unused")
    public double[] getDeviceLatLong( String sourceUri, boolean refresh) {
        return this.getDeviceCoordinates(sourceUri, refresh);
    }

    @SuppressWarnings("unused")
    public String getDeviceIndoorLocation( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.IndoorLocation, refresh);
        return resp != null ? resp.indoor_location : null;
    }

    @SuppressWarnings("unused")
    public Integer getDeviceBattery( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Battery, refresh);
        return resp != null ? resp.battery : null;
    }

    @SuppressWarnings("unused")
    public String getDeviceType( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Type, refresh);
        return resp != null ? resp.type : null;
    }

    @SuppressWarnings("unused")
    public String getDeviceUsername( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Username, refresh);
        return resp != null ? resp.username : null;
    }

    @SuppressWarnings("unused")
    public Boolean getDeviceLocationEnabled( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.LocationEnabled, refresh);
        return resp != null ? resp.location_enabled : null;
    }

    @SuppressWarnings("unused")
    public DeviceInfoResponse getDeviceInfo( String sourceUri, DeviceInfoQueryType query, boolean refresh) {
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

    @SuppressWarnings("unused")
    public void setDeviceName( String sourceUri, String name) {
        setDeviceInfo( sourceUri, DeviceField.Label, name);
    }

    @SuppressWarnings("unused")
    public void enableLocation( String sourceUri) {
        setLocationEnabled(sourceUri, true);
    }

    @SuppressWarnings("unused")
    public void disableLocation( String sourceUri) {
        setLocationEnabled(sourceUri, false);
    }

    @SuppressWarnings("unused")
    public void setLocationEnabled( String sourceUri, boolean enabled) {
        setDeviceInfo( sourceUri, DeviceField.LocationEnabled, String.valueOf(enabled));
    }

    // setDeviceChannel is currently not supported

    // public void setDeviceChannel(String sourceUri, String channel) {
    //     setDeviceInfo( sourceUri, DeviceField.Channel, channel);
    // }

    @SuppressWarnings("unused")
    public void setChannel( String sourceUri, String channelName, boolean suppressTTS, boolean disableHomeChannel) {
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

    private void setDeviceInfo( String sourceUri, DeviceField field, String value) {
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

    @SuppressWarnings("unused")
    public void setUserProfile( String sourceUri, String username, boolean force) {
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

    @SuppressWarnings("unused")
    public void enableHomeChannel(String target) {
        setHomeChannelState(target, true);
    }

    @SuppressWarnings("unused")
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

    public void terminate() {
        logger.debug("Terminating workflow");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Terminate);
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error terminating workflow", e);
        }

    }

    // HELPER FUNCTIONS ##############

    @SuppressWarnings("unused")
    public static String getSourceUri(StartEvent startEvent) {
        Trigger trigger = null;
        Map<String, Object> args = null;
        Object object = null;
        String sourceUri = null;
        if (startEvent != null) {
            trigger = startEvent.trigger;
        }
        if (trigger != null) {
            args = trigger.args;
        }
        if (args != null) {
            object = args.get("source_uri");
        }
        if (object instanceof String) {
            sourceUri = (String) object;
        }
        return sourceUri;
    }

    String serverHostname = "all-main-pro-ibot.relaysvr.com";
    String version = "relay-sdk-java/2.0.0";
    String auth_hostname = "auth.relaygo.com";

    private static String encodeQueryParams(Map<String, String> queryParams) {
        // Create a new string builder and for each query parameter in queryParams, 
        // add it to encodeData.  When all query parameters are added, return the encoded parameters.
        var encodeData = new StringBuilder();
        encodeData.append("?");
        for(Map.Entry<String, String> param : queryParams.entrySet()) {
            if(encodeData.length() > 0) {
                encodeData.append("&");
            }
            encodeData.append(param.getKey());
            encodeData.append("=");
            encodeData.append(param.getValue());
        }
        return encodeData.toString();
    }

    private String updateAccessToken(String refreshToken, String clientId) {
        // Create the URL String
        String grantUrl = "https://" + auth_hostname + "/oauth2/token";
        
        // Create a Map that contains the payload to be sent with the request
        Map<String, String> grantPayload = new LinkedHashMap<>();
        grantPayload.put("client_id", clientId);
        grantPayload.put("grant_type", "refresh_token");
        grantPayload.put("refresh_token", refreshToken);
        
        // Create a new HttpClient and HttpRequest, and add the headers and URL to the request
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(encodeQueryParams(grantPayload)))
                    .uri(URI.create(grantUrl))
                    .setHeader("User-Agent", version)
                    .setHeader("Content-Type", "application/json")
                    .build();
        
        // Try to send the request, if you don't receive a status code of 200, throw an exception back to the client
        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new HttpRetryException("Failed to retrieve access token with status code ", response.statusCode());
            }

            // Create a Map so that we can easily retrieve the access token from the response body, and return the access token 
            // back to the caller.
            Gson gson = new Gson();
            @SuppressWarnings("rawtypes") LinkedHashMap uncheckedMap = gson.fromJson(response.body(), LinkedHashMap.class);
            return (String) uncheckedMap.get("access_token");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Received exception when trying to update access token: ", e);
        } 
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public Map<String, String> triggerWorkflow(String accessToken, String refreshToken,
                                               String clientId, String workflowId,
                                               String subscriberId, String userId,
                                               String[] targets, Map<String, String> actionArgs) {
        // Create a Map containing the query parameters
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("subscriber_id", subscriberId);
        queryParams.put("user_id", userId);

        // Create the URL and append the encoded query paremeters to the URL
        String url = "https://" + serverHostname + "/ibot/workflow/" + workflowId + encodeQueryParams(queryParams);

        // Create a map containing the payload you would like to send with the request
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("action", "invoke");

        // If the actionArgs or targets parameters are not null, add them to the payload
        if (actionArgs != null) {
            payload.put("action_args", actionArgs.toString());
        }

        if (targets != null) {
            payload.put("target_device_ids", Arrays.toString(targets));
        }

        // Create a new HttpClient and HttpRequest, and add the headers and URL to the request
        Gson gson = new Gson();
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .uri(URI.create(url))
                    .setHeader("User-Agent", version)
                    .setHeader("Authorization", "Bearer " + accessToken)
                    .build();
        
        // Try to send the request.  If you receive a status code of 401, try to retrieve a new access token and retry the request
        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                logger.debug("Got 401, retrieving a new access token");
                accessToken = updateAccessToken(refreshToken, clientId);

                httpRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .uri(URI.create(url))
                    .setHeader("User-Agent", version)
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
                
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }

            // Create a Map that will hold the response body and access token, then return the Map to the client
            Map<String, String> returnVal = new LinkedHashMap<>();
            returnVal.put("response", response.body());
            returnVal.put("access_token", accessToken);
            return returnVal;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Received exception when attempting to trigger workflow: ", e);
        } 
    }

    @SuppressWarnings("unused")
    public Map<String, String> fetchDevice(String accessToken, String refreshToken, String clientId, String subscriberId, String userId) {
        // Create a Map containgin the query parameters
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("subscriber_id", subscriberId);

        // Create a URL and append the encoded query parameters to the URL
        String url = "https://" + serverHostname + "/relaypro/api/v1/device/" + userId + encodeQueryParams(queryParams);

        // Create a new HttpClient and HttpRequest, and add the headers and URL to the request
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
        
        // Try to send the request.  If you receive a status code of 401, try to retrieve a new access token and retry the request
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                logger.debug("Got 401, retrieving a new access token");
                accessToken = updateAccessToken(refreshToken, clientId);

                httpRequest = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(url))
                        .setHeader("User-Agent", version)
                        .setHeader("Authorization", "Bearer " + accessToken)
                        .build();

                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }

            // Create a Map that will hold the response body and access token, then return the Map to the client
            Map<String, String> returnVal = new LinkedHashMap<>();
            returnVal.put("response", response.body());
            returnVal.put("access_token", accessToken);
            return returnVal;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Received exception when retrieving device information", e);
        } 
    }
}

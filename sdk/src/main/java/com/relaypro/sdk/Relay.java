// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.relaypro.sdk.types.*;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

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
            // prompt and progress events need to be sent to the matching calls as well as to event callbacks
            if (msgWrapper.type.equals("prompt") || msgWrapper.type.equals("progress")) {
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
        while (true) {
            // TODO set timeout for listen
            MessageWrapper response = call.responseQueue.poll(RESPONSE_TIMEOUT_SECS, TimeUnit.SECONDS);
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
            } else if (response.eventOrResponse.equals("response")) {
                // matching response
                if (!waitForPromptEnd) {
                    return response;
                }
                // need to wait for prompt end, save the response to return then
                resp = response;
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

    public void switchAllLedOn( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.colors.ring = color;
        setLeds( sourceUri, LedEffect.STATIC, ledInfo);
    }

    public  void switchAllLedOff( String sourceUri) {
        LedInfo ledInfo = new LedInfo();
        setLeds( sourceUri, LedEffect.OFF, ledInfo);
    }

    public  void rainbow( String sourceUri, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.rotations = rotations;
        setLeds( sourceUri, LedEffect.RAINBOW, ledInfo);
    }

    public  void rotate( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.rotations = -1;
        ledInfo.colors.led1 = color;
        setLeds( sourceUri, LedEffect.ROTATE, ledInfo);
    }

    public  void flash( String sourceUri, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.count = count;
        ledInfo.colors.ring = color;
        setLeds( sourceUri, LedEffect.FLASH, ledInfo);
    }

    public  void breathe( String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.count = -1;
        ledInfo.colors.ring = color;
        setLeds( sourceUri, LedEffect.BREATHE, ledInfo);
    }

    public  void setLeds( String sourceUri, LedEffect effect, LedInfo args) {
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

    public  String getDeviceName( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Name, refresh);
        return resp != null ? resp.name : null;
    }

    public  String getDeviceId( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Id, refresh);
        return resp != null ? resp.id : null;
    }

    public  String getDeviceAddress( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.Address, refresh);
        return resp != null ? resp.address : null;
    }

    public  double[] getDeviceLatLong( String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( sourceUri, DeviceInfoQueryType.LatLong, refresh);
        return resp != null ? resp.latlong : null;
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

    private  DeviceInfoResponse getDeviceInfo( String sourceUri, DeviceInfoQueryType query, boolean refresh) {
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

    public  void setDeviceMode( String sourceUri, DeviceMode mode) {
        logger.debug("Setting device mode: " + mode.value());
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceMode, sourceUri,
                entry("mode", mode.value())
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting device mode", e);
        }
    }

    public  void setDeviceName( String sourceUri, String name) {
        setDeviceInfo( sourceUri, DeviceField.Label, name);
    }

    public  void setLocationEnabled( String sourceUri, boolean enabled) {
        setDeviceInfo( sourceUri, DeviceField.LocationEnabled, String.valueOf(enabled));
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

    public  void restartDevice( String sourceUri) {
        logger.debug("Restarting device: " + sourceUri);
        powerDownDevice( sourceUri, true);
    }

    public  void powerDownDevice( String sourceUri) {
        logger.debug("Powering down device: " + sourceUri);
        powerDownDevice( sourceUri, true);
    }

    private  void powerDownDevice( String sourceUri, boolean restart) {
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PowerOff, sourceUri,
                entry("restart", restart)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error powering down device", e);
        }
    }

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

}

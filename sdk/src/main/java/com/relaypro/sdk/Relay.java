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
    private static final Map<Session, WorkflowWrapper> runningWorkflowsBySession = new ConcurrentHashMap<>();
    private static final Map<Workflow, WorkflowWrapper> runningWorkflowsByWorkflow = new ConcurrentHashMap<>();

    static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static Logger logger = LoggerFactory.getLogger(Relay.class);
    
    private static final int RESPONSE_TIMEOUT_SECS = 10;
    
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
            wfClone = (Workflow)wf.clone();
        } catch (CloneNotSupportedException e) {
            logger.error("Error cloning workflow", e);
            // stop ws connection
            stopWorkflow(session, "worflow_instantiation_error");
            return;
        }

        WorkflowWrapper wrapper = new WorkflowWrapper();
        wrapper.workflow = wfClone;
        wrapper.session = session;
        
        logger.debug("Websocket connected to workflow " + workflowName + " " + wrapper);
        
        // here, start a thread that handles running the workflow code
        wrapper.workflowFuture = wrapper.executor.submit(new Worker(wrapper));
        
        runningWorkflowsBySession.put(session, wrapper);
        runningWorkflowsByWorkflow.put(wfClone, wrapper);
    }

    public static void stopWorkflow(Session session, String reason) {
        logger.debug("Shutting down workflow with reason: " + reason);
        try {
            session.close();
        } catch (IOException e) {
            logger.error("Error when shutting down workflow", e);
        }
        
        // shut down worker, if running, by sending poison pill to it's message queue and call queues
        WorkflowWrapper wfWrapper = runningWorkflowsBySession.get(session);
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
        WorkflowWrapper wfWrapper = runningWorkflowsBySession.get(session);
        MessageWrapper msgWrapper = MessageWrapper.parseMessage(message);

        if (msgWrapper.eventOrResponse == "event") {
            // prompt, progress, and error events need to be sent to the matching calls as well as to event callbacks
            if (msgWrapper.type.equals("prompt") || msgWrapper.type.equals("progress") || msgWrapper.type.equals("error")) {
                handleResponse(msgWrapper, wfWrapper);    
            }
            
            // send this through the message queue to the worker thread
            wfWrapper.messageQueue.add(msgWrapper);
            
            // if this is a stop event, need to shut everything down after we just called the onStop callback
            if (msgWrapper.type.equals("stop")) {
                stopWorkflow(session, (String)msgWrapper.parsedJson.get("reason"));
            }
        }
        // if response, match to request
        else if (msgWrapper.eventOrResponse == "response") {
            handleResponse(msgWrapper, wfWrapper);
        }
        
    }
    
    private static void handleResponse(MessageWrapper msgWrapper, WorkflowWrapper wfWrapper) {
        String id = null;
        if (msgWrapper.parsedJson.containsKey("_id")) {
            id = (String)msgWrapper.parsedJson.get("_id");
        }
        else if (msgWrapper.parsedJson.containsKey("id")) {
            id = (String)msgWrapper.parsedJson.get("id");
        }
        if (id == null) {
            return;
        }
        Call matchingCall = wfWrapper.pendingRequests.get(id);
        matchingCall.responseQueue.add(msgWrapper);
    }
    
    private static MessageWrapper sendRequest(Map<String, Object> message, Workflow workflow) throws EncodeException, IOException, InterruptedException {
        return sendRequest(message, workflow, false);
    }
    
    private static MessageWrapper sendRequest(Map<String, Object> message, Workflow workflow, boolean waitForPromptEnd) throws EncodeException, IOException, InterruptedException {
        WorkflowWrapper wrapper = runningWorkflowsByWorkflow.get(workflow);
        
        String id = (String)message.get("_id");
        String msgJson = gson.toJson(message);
        
        // store a call for this request that all incoming response/prompt/progress messages can be passed back to us through
        Call call = new Call();
        wrapper.pendingRequests.put(id, call);
        
        // send the request
        wrapper.session.getBasicRemote().sendObject(msgJson);

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
    public static String startInteraction(Workflow workflow, String sourceUri, String name, Object options) {
        logger.debug("Starting Interaction for source uri " + sourceUri);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StartInteraction, sourceUri, 
                entry("name", name),
                entry("options", options == null ? new Object() : options)
        );
        
        try {
            MessageWrapper resp = sendRequest(req, workflow);
            return (String)resp.parsedJson.get("error");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error starting interaction", e);
        }
        return null;
    }

    public static String say(Workflow workflow, String sourceUri, String text) {
        return say(workflow, sourceUri, text, LanguageType.English);
    }
    
    public static String say(Workflow workflow, String sourceUri, String text, LanguageType lang) {
        return say(workflow, sourceUri, text, lang, false);
    }

    public static void sayAndWait(Workflow workflow, String sourceUri, String text) {
        sayAndWait(workflow, sourceUri, text, LanguageType.English);
    }
    
    public static void sayAndWait(Workflow workflow, String sourceUri, String text, LanguageType lang) {
        say(workflow, sourceUri, text, lang, true);
    }

    private static String say(Workflow workflow, String sourceUri, String text, LanguageType lang, boolean wait) {
        logger.debug("Saying " + text + " in " + lang.value() + " to " + sourceUri);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Say, sourceUri,
                entry("text", text),
                entry("lang", lang.value())
        );

        try {
            MessageWrapper resp = sendRequest(req, workflow, wait);
            return resp != null ? (String)resp.parsedJson.get("id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error saying text", e);
        }
        return null;
    }
    
    public static String play(Workflow workflow, String sourceUri, String filename) {
        return play(workflow, sourceUri, filename, false);
    }

    public static void playAndWait(Workflow workflow, String sourceUri, String filename) {
        play(workflow, sourceUri, filename, true);
    }

    private static String play(Workflow workflow, String sourceUri, String filename, boolean wait) {
        logger.debug("Playing file: " + filename);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Play, sourceUri,
                entry("filename", filename)
        );

        try {
            MessageWrapper resp = sendRequest(req, workflow, wait);
            return resp != null ? (String)resp.parsedJson.get("id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error playing file", e);
        }
        return null;
    }
    
    public static void stopPlayback(Workflow workflow, String  sourceUri , String[] ids) {
        logger.debug("Stopping playback for: " + ids);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopPlayback, sourceUri,
                entry("ids", ids)
        );
        
        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping playback", e);
        }
    }

    public static void setTimer(Workflow workflow, TimerType timerType, String name, long timeout, TimeoutType timeoutType) {
        logger.debug("Setting timer " + timerType.value() + " named " + name + " for " + timeout + " " + timeoutType.value());
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetTimer,
            entry("type", timerType.value()),
            entry("name", name),
            entry("timeout", timeout),
            entry("timeout_type", timeoutType.value())    
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting timer", e);
        }
    }
    
    public static void clearTimer(Workflow workflow, String name) {
        logger.debug("Clearing timer named " + name);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.ClearTimer,
                entry("name", name)
        );
        
        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error ", e);
        }
    }

    public static void switchAllLedOn(Workflow workflow, String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.colors.ring = color;
        setLeds(workflow, sourceUri, LedEffect.STATIC, ledInfo);
    }

    public static void switchAllLedOff(Workflow workflow, String sourceUri) {
        LedInfo ledInfo = new LedInfo();
        setLeds(workflow, sourceUri, LedEffect.OFF, ledInfo);
    }

    public static void rainbow(Workflow workflow, String sourceUri, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.rotations = rotations;
        setLeds(workflow, sourceUri, LedEffect.RAINBOW, ledInfo);
    }

    public static void rotate(Workflow workflow, String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.rotations = -1;
        ledInfo.colors.led1 = color;
        setLeds(workflow, sourceUri, LedEffect.ROTATE, ledInfo);
    }

    public static void flash(Workflow workflow, String sourceUri, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.count = count;
        ledInfo.colors.ring = color;
        setLeds(workflow, sourceUri, LedEffect.FLASH, ledInfo);
    }

    public static void breathe(Workflow workflow, String sourceUri, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.count = -1;
        ledInfo.colors.ring = color;
        setLeds(workflow, sourceUri, LedEffect.BREATHE, ledInfo);
    }

    public static void setLeds(Workflow workflow, String sourceUri, LedEffect effect, LedInfo args) {
        logger.debug("Setting leds: " + effect.value() + " " + args);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetLeds, sourceUri,
                entry("effect", effect.value()),
                entry("args", args)
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting leds", e);
        }
    }

    public static void vibrate(Workflow workflow, String sourceUri, long[] pattern) {
        logger.debug("Vibrating: " + pattern);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Vibrate, sourceUri,
                entry("pattern", pattern)
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error vibrating", e);
        }
    }

    public static String getDeviceName(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Name, refresh);
        return resp != null ? resp.name : null;
    }

    public static String getDeviceId(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Id, refresh);
        return resp != null ? resp.id : null;
    }

    public static String getDeviceAddress(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Address, refresh);
        return resp != null ? resp.address : null;
    }

    public static double[] getDeviceLatLong(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.LatLong, refresh);
        return resp != null ? resp.latlong : null;
    }

    public static String getDeviceIndoorLocation(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.IndoorLocation, refresh);
        return resp != null ? resp.indoor_location : null;
    }

    public static Integer getDeviceBattery(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Battery, refresh);
        return resp != null ? resp.battery : null;
    }

    public static String getDeviceType(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Type, refresh);
        return resp != null ? resp.type : null;
    }

    public static String getDeviceUsername(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.Username, refresh);
        return resp != null ? resp.username : null;
    }

    public static Boolean getDeviceLocationEnabled(Workflow workflow, String sourceUri, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo(workflow, sourceUri, DeviceInfoQueryType.LocationEnabled, refresh);
        return resp != null ? resp.location_enabled : null;
    }

    private static DeviceInfoResponse getDeviceInfo(Workflow workflow, String sourceUri, DeviceInfoQueryType query, boolean refresh) {
        logger.debug("Getting device info: " + query + " refresh: " + refresh);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GetDeviceInfo, sourceUri,
                entry("query", query.value()),
                entry("refresh", refresh)
        );

        try {
            MessageWrapper resp = sendRequest(req, workflow);
            if (resp != null) {
                return gson.fromJson(resp.messageJson, DeviceInfoResponse.class);
            }
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error getting device info", e);
        }
        return null;
    }

    public static void setDeviceMode(Workflow workflow, String sourceUri, DeviceMode mode) {
        logger.debug("Setting device mode: " + mode.value());
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceMode, sourceUri,
                entry("mode", mode.value())
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting device mode", e);
        }
    }

    public static void setDeviceName(Workflow workflow, String sourceUri, String name) {
        setDeviceInfo(workflow, sourceUri, DeviceField.Label, name);
    }

    public static void setLocationEnabled(Workflow workflow, String sourceUri, boolean enabled) {
        setDeviceInfo(workflow, sourceUri, DeviceField.LocationEnabled, String.valueOf(enabled));
    }

    private static void setDeviceInfo(Workflow workflow, String sourceUri, DeviceField field, String value) {
        logger.debug("Setting device info: " + field + ": " + value);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceInfo, sourceUri,
                entry("field", field.value()),
                entry("value", value)
        );
        
        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting device info", e);
        }
    }

    public static void setUserProfile(Workflow workflow, String sourceUri, String username, boolean force) {
        logger.debug("Setting user profile: " + username + ": " + force);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetUserProfile, sourceUri,
                entry("username", username),
                entry("force", force)
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting user profile", e);
        }
    }

    public static void setChannel(Workflow workflow, String sourceUri, String channelName, boolean suppressTTS, boolean disableHomeChannel) {
        logger.debug("Setting channel: " + channelName + ": supresstts:" + suppressTTS + " disableHomeChannel:" + disableHomeChannel);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetChannel, sourceUri,
                entry("channel_name", channelName),
                entry("suppress_tts", suppressTTS),
                entry("disable_home_channel", disableHomeChannel)
        );

        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting channel", e);
        }
    }

    public static void restartDevice(Workflow workflow, String sourceUri) {
        logger.debug("Restarting device: " + sourceUri);
        powerDownDevice(workflow, sourceUri, true);
    }

    public static void powerDownDevice(Workflow workflow, String sourceUri) {
        logger.debug("Powering down device: " + sourceUri);
        powerDownDevice(workflow, sourceUri, true);
    }

    private static void powerDownDevice(Workflow workflow, String sourceUri, boolean restart) {
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PowerOff, sourceUri,
                entry("restart", restart)
        );
        
        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error powering down device", e);
        }
    }
    
    public static void terminate(Workflow workflow) {
        logger.debug("Terminating workflow");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Terminate);
        
        WorkflowWrapper wrapper = runningWorkflowsByWorkflow.get(workflow);
        try {
            sendRequest(req, workflow);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error terminating workflow", e);
        }
    }

    // HELPER FUNCTIONS ##############
    
    public static String getStartEventSourceUri(Map<String, Object> startEvent) {
        Map trigger = (Map)startEvent.get("trigger");
        Map args = (Map)trigger.get("args");
        return (String)args.get("source_uri");
    }
    
    public static String getButtonEventButton(Map<String, Object> buttonEvent) {
        return (String)buttonEvent.get("button");
    }
    
    public static String getButtonEventTaps(Map<String, Object> buttonEvent) {
        return (String)buttonEvent.get("taps");
    }
    
}

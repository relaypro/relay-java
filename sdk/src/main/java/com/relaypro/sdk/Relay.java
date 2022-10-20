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
import java.util.ArrayList;
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

/**
 * Actions and utilities that can be performed on a Relay device.
 */
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

    /**
     * Adds a workflow to the path. Maps the specified name of the workflow 
     * to the new instance of the workflow class created.
     * @param name a name for your workflow.
     * @param wf a new instance of a class that contains your workflow.
     */
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

    /**
     * Starts an interaction with the user.  Triggers an INTERACTION_STARTED event
     * and allows the user to interact with the device via functions that require an
     * interaction URN. Uses a default set of options.
     * @param target the device that you would like to start an interaction with.
     * @param name a name for your interaction
     * @return any errors received from the server.
     * @see #startInteraction(String, String, Object)
     */
    @SuppressWarnings("UnusedReturnValue")
    public String startInteraction(String target, String name) {
        return startInteraction(target, name, null);
    }

    /**
     * Starts an interaction with the user.  Triggers an INTERACTION_STARTED event
     * and allows the user to interact with the device via functions that require an
     * interaction URN.
     * @param target the device that you would like to start an interaction with.
     * @param name a name for your interaction
     * @param options can be color, home channel, or input types.
     * @return any errors received from the server.
     * @see #startInteraction(String, String)
     */
    @SuppressWarnings("UnusedReturnValue")
    public String startInteraction(String target, String name, Object options) {
        logger.debug("Starting Interaction for source uri " + target);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StartInteraction, target,
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

    /**
     * Ends an interaction with the user.  Triggers an INTERACTION_ENDED event to signify
     * that the user is done interacting with the device.
     * @param target the interaction that you would like to end.
     * @return any errors received from the server.
     */
    @SuppressWarnings("UnusedReturnValue")
    public String endInteraction(String target) {
        logger.debug("Ending Interaction for source uri " + target);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.EndInteraction, target);
        try {
            MessageWrapper resp = sendRequest(req);
            return (String) resp.parsedJson.get("error");
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error ending interaction", e);
        }
        return null;
    }

    /**
     * Utilizes text to speech capabilities to make the device 'speak' to the user.
     * @param target the interaction URN.
     * @param text what you would like the device to say.
     * @return the response ID after the device speaks to the user.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String say(String target, String text) {
        return say(target, text, LanguageType.English);
    }

    /**
     * Utilizes text to speech capabilities to make the device 'speak' to the user.
     * @param target the interaction URN.
     * @param text what you would like te device to say.
     * @param lang the language of the text that is being spoken.
     * @return the response ID after the device speaks to the user.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String say(String target, String text, LanguageType lang) {
        return say(target, text, lang, false);
    }

    /**
     * Utilizes text to speech capabilities to make the device 'speak' to the user.
     * Waits until the text is fully played out on the device before continuing.
     * @param target the interaction URN.
     * @param text what you would like the device to say.
     */
    @SuppressWarnings("unused")
    public void sayAndWait(String target, String text) {
        sayAndWait(target, text, LanguageType.English);
    }

    /**
     * Utilizes text to speech capabilities to make the device 'speak' to the user.
     * Waits until the text is fully played out on the device before continuing.
     * @param target the interaction URN.
     * @param text what you would like the device to say.
     * @param lang the language of the text that is being spoken.
     */
    @SuppressWarnings("unused")
    public void sayAndWait(String target, String text, LanguageType lang) {
        say(target, text, lang, true);
    }

    private String say(String target, String text, LanguageType lang, boolean wait) {
        logger.debug("Saying " + text + " in " + lang.value() + " to " + target);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Say, target,
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

    /**
     * Listens for the user to speak into the device.  Utilizes speech to text functionality to interact with
     * the user.
     * @param target the interaction URN.
     * @param requestId the request ID.
     * @return the text that the device parsed from what was spoken.
     */
    @SuppressWarnings("unused")
    public String listen(String target, String requestId) {
        String[] phrases = {};
        return listen(target, requestId, phrases, false, LanguageType.English, 30);
    }

    /**
     * Listens for the user to speak into the device.  Utilizes speech to text functionality to interact with
     * the user.
     * @param target the interaction URN.
     * @param requestId the request ID.
     * @param phrases phrases that you would like to limit the user's response to.
     * @param transcribe whether you would like to transcribe the user's response.
     * @param lang if you would like the device to listen for a response in a specific language.
     * @param timeout timeout for how long the device will wait for user's response.
     * @return the text that the device parsed from what was spoken.
     */
    @SuppressWarnings("unused")
    public String listen(String target, String requestId, String[] phrases, boolean transcribe, LanguageType lang, int timeout) {
        logger.debug("Listening to " + target);
        
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Listen, target,
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

    /**
     * Plays a custom audio file that was uploaded by the user.
     * @param target the interaction URN.
     * @param filename the name of the audio file.
     * @return the response ID after the audio file has been played on the device.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public String play(String target, String filename) {
        return play(target, filename, false);
    }

    /**
     * Plays a custom audio file that was uploaded by the user.
     * Waits until the audio file has finished playing before continuing through
     * the workflow.
     * @param target the interaction URN.
     * @param filename the name of the audio file.
     * @return the response ID after the audio file has been played on the device.
     */
    @SuppressWarnings("unused")
    public String playAndWait(String target, String filename) {
        return play(target, filename, true);
    }

    private String play(String target, String filename, boolean wait) {
        logger.debug("Playing file: " + filename);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Play, target,
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

    /**
     * Stops a playback request on the device.
     * @param target the device URN.
     * @param ids the IDs of the devices who you would like to stop the playback message for.
     */
    @SuppressWarnings("unused")
    public void stopPlayback( String target, String[] ids) {
        logger.debug("Stopping playback for: " + Arrays.toString(ids));
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.StopPlayback, target,
                entry("ids", ids)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error stopping playback", e);
        }
    }

    /**
     * Play a targeted device's inbox messages.
     * @param target the device or interaction URN whose inbox you would like to check.
     */
    @SuppressWarnings("unused")
    public void playUnreadInboxMessages(String target) {
        logger.debug("Playing unread messages" );
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PlayInboxMessages, target);

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error playing unread inbox messages", e);
        }
    }

    /**
     * Retrieves the number of messages in a device's inbox.
     * @param target the device or interaction URN whose inbox you would like to check.
     * @return the number of messages in the specified device's inbox.
     */
    @SuppressWarnings("unused")
    public int getUnreadInboxSize(String target) {
        logger.debug("Getting unread inbox size");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.InboxCount, target);

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

    /**
     * Serves as a named timer that can be either interval or timeout.  Allows you to specify
     * the unit of time.
     * @param timerType can be 'timeout' or 'interval'. Defaults to 'timeout'.
     * @param name a name for your timer.
     * @param timeout an integer representing when you would like your timer to fire.
     * @param timeoutType can be 'ms', 'secs', 'mins' or 'hrs'. Defaults to 'secs'.
     */
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

    /**
     * Clears the specified timer.
     * @param name the name of the timer that you would like to clear.
     */
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

    /**
     * Starts an unnamed timer, meaning this will be the only timer on your device.
     * The timer will fire when it reaches the value of the 'timeout' parameter.
     * @param timeout the number of seconds you would like to wait until the timer fires.
     */
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

    /**
     * Stops an unnamed timer.
     */
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

    /**
     * Translates text from one language to another.
     * @param text the text that you would like to translate.
     * @param from the language that you would like to translate from.
     * @param to the language that you would like to translate to.
     * @return the translated text.
     */
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

    /**
     * Places a call to another device.
     * @param target the device or interaction URN that will place the call.
     * @param calleeUri the URN of the device you would like to call.
     * @return the call ID.
     */
    @SuppressWarnings("unused")
    public String placeCall(String target, String calleeUri) {
        logger.debug("Placing call");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.PlaceCall, target,
                entry("uri", calleeUri)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? (String) resp.parsedJson.get("call_id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error placing call", e);
        }
        return null;
    }

    /**
     * Answers a call on your device.
     * @param target the device or interaction URN that will answer the call.
     * @param call_id the ID of the call to answer.
     */
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

    /**
     * Ends a call on your device.
     * @param target the device or interaction URN that will hang up the call.
     * @param call_id the ID of the call to hang up.
     */
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

    /**
     * Creates an incident that will alert the Relay Dash.
     * @param originator the device URN that triggered the incident.
     * @param itype the type of incident that occurred.
     */
    @SuppressWarnings("unused")
    public String createIncident( String originator, String itype) {
        logger.debug("Creating incident");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.CreateIncident, 
            entry("type", itype),
            entry("originator_uri", originator)
        );
        try {
            MessageWrapper resp = sendRequest(req);
            return resp != null ? (String) resp.parsedJson.get("incident_id") : null;
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error creating incident", e);
        }
        return null;
    }

    /**
     * Resolves an incident that was created.
     * @param incidentId the ID of the incident that you would like to resolve.
     * @param reason the reason for resolving the incident.
     */
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

    /**
     * Log an analytic event from a workflow with the specified content and
     * under a specified category.  This includes the device who triggered the workflow
     * that called this function.
     * @param message a description for your analytical event.
     * @param deviceUri the URN of the device that triggered this function.
     * @param category a category for your analytical event.
     */
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

    /**
     * Log an analytics event from a workflow with the specified content and
     * under a specified category. This does not log the device who
     * triggered the workflow that called this function.
     * @param message a description for your analytical event.
     * @param category a category for your analytical event
     */
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

    /**
     * Switches on an LED at a particular index to a specified color.
     * @param target the interaction URN.
     * @param index the index of the LED, numbered 1-12.
     * @param color the hex color code you would like to turn the LED to.
     */
    @SuppressWarnings("unused")
    public void switchLedOn( String target, int index, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor(Integer.toString(index), color);
        setLeds( target, LedEffect.STATIC, ledInfo.ledMap);
    }

    /**
     * Switches all the LEDs on a device on to a specified color.
     * @param target the interaction URN.
     * @param color the hex color code you would like the LEDs to be.
     */
    @SuppressWarnings("unused")
    public void switchAllLedOn( String target, String color) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setColor("ring", color);
        setLeds( target, LedEffect.STATIC, ledInfo.ledMap);
    }

    /**
     * Switches all of the LEDs on a device off.
     * @param target the interaction URN.
     */
    @SuppressWarnings("unused")
    public void switchAllLedOff( String target) {
        LedInfo ledInfo = new LedInfo();
        setLeds( target, LedEffect.OFF, ledInfo.ledMap);
    }

    /**
     * Switches all the LEDs on to a configured rainbow pattern and rotates the rainbow
     * a specified number of times.
     * @param target the interaction URN.
     * @param rotations the number of times you would like the rainbow to rotate.
     */
    @SuppressWarnings("unused")
    public void rainbow( String target, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(rotations);
        setLeds( target, LedEffect.RAINBOW, ledInfo.ledMap);
    }

    /**
     * Switches all the LEDs on a device to a certain color and rotates them a specified number
     * of times.
     * @param target the interaction URN.
     * @param color the hex color code you would like to turn the LEDs to.
     * @param rotations the number of times you would like the LEDs to rotate.
     */
    @SuppressWarnings("unused")
    public void rotate( String target, String color, int rotations) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setRotations(rotations);
        ledInfo.setColor("1", color);
        setLeds( target, LedEffect.ROTATE, ledInfo.ledMap);
    }

    /**
     * Switches all the LEDs on a device to a certain color and flashes them
     * a specified number of times.
     * @param target the interaction URN.
     * @param color the hex color code you would like to turn the LEDs to.
     * @param count the number of times you would like the LEDs to flash.
     */
    @SuppressWarnings("unused")
    public void flash( String target, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setCount(count);
        ledInfo.setColor("ring", color);
        setLeds( target, LedEffect.FLASH, ledInfo.ledMap);
    }

    /**
     * Switches all the LEDs on a device to a certain color and creates a 'breathing' effect,
     * where the LEDs will slowly light up a specified number of times.
     * @param target the interaction URN.
     * @param color the hex color code you would like to turn the LEDs to.
     * @param count the number of times you would like the LEDs to flash.
     */
    @SuppressWarnings("unused")
    public void breathe( String target, String color, int count) {
        LedInfo ledInfo = new LedInfo();
        ledInfo.setCount(count);
        ledInfo.setColor("ring", color);
        setLeds( target, LedEffect.BREATHE, ledInfo.ledMap);
    }

    private void setLeds( String target, LedEffect effect, Map<String, Object> args) {
        logger.debug("Setting leds: " + effect.value() + " " + args);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetLeds, target,
                entry("effect", effect.value()),
                entry("args", args)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting leds", e);
        }
    }

    /**
     * Makes the device vibrate in a particular pattern.  You can specify
     * how many vibrations you would like, the duration of each vibration in
     * milliseconds, and how long you would like the pauses between each vibration to last
     * in milliseconds.
     * @param target the interaction URN.
     * @param pattern an array representing the pattern of your vibration.
     */
    @SuppressWarnings("unused")
    public void vibrate( String target, int[] pattern) {
        logger.debug("Vibrating: " + Arrays.toString(pattern));
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Vibrate, target,
                entry("pattern", pattern)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error vibrating", e);
        }
    }

    /**
     * Sets a variable with the corresponding name and value. Scope of
     * the variable is from start to end of a workflow.  Note that you
     * can only set values of type string.
     * @param name name of the variable to be created.
     * @param value value that the variable will hold.
     */
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

    /**
     * Retrieves a variable that was set either during workflow registration
     * or through the set_var() function.  The variable can be retrieved anywhere
     * within the workflow, but is erased after the workflow terminates.
     * @param name name of the variable to be retrieved.
     * @param defaultValue default value of the variable if it does not exist.
     * @return the variable requested as a String.
     */
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

    /**
     * Retrieves a variable that was set either during workflow registration
     * or through the set_var() function of type integer.  The variable can be retrieved anywhere
     * within the workflow, but is erased after the workflow terminates.
     * @param name name of the variable to be retrieved.
     * @param defaultValue default value of the variable if it does not exist.
     * @return the variable requested as an Integer.
     */
    @SuppressWarnings("unused")
    public int getNumberVar(String name, int defaultValue) {
        return Integer.parseInt(this.getVar(name, Integer.toString(defaultValue)));
    }

    /**
     * Unsets the value of a variable.
     * @param name the name of the variable whose value you would like to unset.
     */
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

    /**
     * Checks whether a device is a member of a particular group.
     * @param groupNameUri the URN of a group.
     * @param potentialMemberNameUri the URN of the device name.
     * @return true if the device is a member of the specified group, false otherwise.
     */
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

    /**
     * Sends out an alert to the specified group of devices and the Relay Dash.
     * @param target the group URN that you would like to send an alert to.
     * @param originator the URN of the device that triggered the alert.
     * @param name a name for your alert.
     * @param text the text that you would like to be spoken to the group as your alert.
     */
    @SuppressWarnings("unused")
    public void alert(String target, String originator, String name, String text) {
        sendNotification(target, originator, "alert", text, name);
    }

    /**
     * Cancels an alert that was sent to a group of devices.  Particularly useful if you would like to cancel the alert
     * on all devices after one device has acknowledged the alert.
     * @param target the device URN that has acknowledged the alert.
     * @param name the name of the alert.
     */
    @SuppressWarnings("unused")
    public void cancelAlert(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    /**
     * Broadcasts a message to a group of devices. The message is played out on all devices, as well
     * as sent to the Relay Dash.
     * @param target the group URN that you would like to broadcast you message to.
     * @param originator the device URN that triggered the broadcast.
     * @param name a name for your broadcast.
     * @param text the text that you would like to broadcast to your group.
     */
    @SuppressWarnings("unused")
    public void broadcast (String target, String originator, String name, String text) {
        sendNotification(target, originator, "broadcast", text, name);
    }

    /**
     * Cancels the broadcast that was sent to a group of devices.
     * @param target the device URN that is cancelling the broadcast.
     * @param name the name of the broadcast that you would like to cancel.
     */
    @SuppressWarnings("unused")
    public void cancelBroadcast(String target, String name) {
        sendNotification(target, null, "cancel", null, name);
    }

    /**
     * Returns the name of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the name of the device.
     * @return the name of the device.
     */
    @SuppressWarnings("unused")
    public String getDeviceName( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Name, refresh);
        return resp != null ? resp.name : null;
    }

    /**
     * Returns the ID of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the name of the device.
     * @return the device ID.
     */
    @SuppressWarnings("unused")
    public String getDeviceId( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Id, refresh);
        return resp != null ? resp.id : null;
    }

    /**
     * Returns the location of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the location.
     * @return the location of the device.
     */
    @SuppressWarnings("unused")
    public String getDeviceLocation( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Address, refresh);
        return resp != null ? resp.address : null;
    }

    /**
     * Returns the address of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the address.
     * @return the address of the device.
     */
    @SuppressWarnings("unused")
    public String getDeviceAddress( String target, boolean refresh) {
        return this.getDeviceLocation(target, refresh);
    }

    /**
     * Retrieves the coordinates of the device's location.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the coordinates.
     * @return a double array containing the latitude and longitude of the device.
     */
    @SuppressWarnings("unused")
    public double[] getDeviceCoordinates( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.LatLong, refresh);
        return resp != null ? resp.latlong : null;
    }

    /**
     * Returns the latitude and longitude coordinates of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the coordinates.
     * @return a double array containing the latitude and longitude of the device.
     */
    @SuppressWarnings("unused")
    public double[] getDeviceLatLong( String target, boolean refresh) {
        return this.getDeviceCoordinates(target, refresh);
    }

    /**
     * Returns the indoor location of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the location.
     * @return the indoor location of the device.
     */
    @SuppressWarnings("unused")
    public String getDeviceIndoorLocation( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.IndoorLocation, refresh);
        return resp != null ? resp.indoor_location : null;
    }

    /**
     * Returns the battery of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the battery.
     * @return the batter of the device as an integer.
     */
    @SuppressWarnings("unused")
    public int getDeviceBattery( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Battery, refresh);
        return resp != null ? resp.battery : null;
    }

    /**
     * Returns the device type of a targeted device, i.e. gen2, gen 3, etc.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the device type.
     * @return the device type.
     */
    @SuppressWarnings("unused")
    public String getDeviceType( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Type, refresh);
        return resp != null ? resp.type : null;
    }

    /**
     * Returns the user profile of a targeted device.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving the device user profile.
     * @return the user profile registered to the device.
     */
    @SuppressWarnings("unused")
    public String getUserProfile( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.Username, refresh);
        return resp != null ? resp.username : null;
    }

    /**
     * Returns whether the location services on a device are enabled.
     * @param target the device or interaction URN.
     * @param refresh whether you would like to refresh before retrieving whether the device's location services are
     *                enabled.
     * @return true if the device's location services are enabled, false otherwise.
     */
    @SuppressWarnings("unused")
    public Boolean getDeviceLocationEnabled( String target, boolean refresh) {
        DeviceInfoResponse resp = getDeviceInfo( target, DeviceInfoQueryType.LocationEnabled, refresh);
        return resp != null ? resp.location_enabled : null;
    }

    @SuppressWarnings("unused")
    private DeviceInfoResponse getDeviceInfo( String target, DeviceInfoQueryType query, boolean refresh) {
        logger.debug("Getting device info: " + query + " refresh: " + refresh);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.GetDeviceInfo, target,
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

    // public  void setDeviceMode( String target, DeviceMode mode) {
    //     logger.debug("Setting device mode: " + mode.value());
    //     Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceMode, target,
    //             entry("mode", mode.value())
    //     );

    //     try {
    //         sendRequest(req);
    //     } catch (EncodeException | IOException | InterruptedException e) {
    //         logger.error("Error setting device mode", e);
    //     }
    // }

    /**
     * Sets the name of a targeted device and updates it on the Relay Dash.
     * The name remains updated until it is set again via a workflow or updated manually
     * on the Relay Dash.
     * @param target the device or interaction URN.
     * @param name a new name for your device.
     */
    @SuppressWarnings("unused")
    public void setDeviceName( String target, String name) {
        setDeviceInfo( target, DeviceField.Label, name);
    }

    /**
     * Enables location services on a device.  Location services will remain
     * enabled until they are disabled on the Relay Dash or through a workflow.
     * @param target the device or interaction URN.
     */
    @SuppressWarnings("unused")
    public void enableLocation( String target) {
        setLocationEnabled(target, true);
    }

    /**
     * Disables location services on a device.  Location services will remain
     * disabled until they are enabled on the Relay Dash or through a workflow.
     * @param target the device or interaction URN.
     */
    @SuppressWarnings("unused")
    public void disableLocation( String target) {
        setLocationEnabled(target, false);
    }

    @SuppressWarnings("unused")
    private void setLocationEnabled( String target, boolean enabled) {
        setDeviceInfo( target, DeviceField.LocationEnabled, String.valueOf(enabled));
    }

    // setDeviceChannel is currently not supported

    // public void setDeviceChannel(String target, String channel) {
    //     setDeviceInfo( target, DeviceField.Channel, channel);
    // }

    /**
     * Sets the channel that a device is on.  This can be used to chang the channel of a device during a workflow,
     * where the channel will also be updated on the Relay Dash.
     * @param target the device or interaction URN.
     * @param channelName the name of the channel you would like to set your device to.
     * @param suppressTTS whether you would like to surpress text to speech.
     * @param disableHomeChannel whether you would like to disable the home channel.
     */
    @SuppressWarnings("unused")
    public void setChannel( String target, String channelName, boolean suppressTTS, boolean disableHomeChannel) {
        logger.debug("Setting channel: " + channelName + ": supresstts:" + suppressTTS + " disableHomeChannel:" + disableHomeChannel);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetChannel, target,
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

    private void setDeviceInfo( String target, DeviceField field, String value) {
        logger.debug("Setting device info: " + field + ": " + value);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetDeviceInfo, target,
                entry("field", field.value()),
                entry("value", value)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting device info", e);
        }
    }

    /**
     * Sets the profile of a user by updating the username.
     * @param target the device URN whose profile you would like to update.
     * @param username the updated username for the device.
     * @param force whether you would like to force this update.
     */
    @SuppressWarnings("unused")
    public void setUserProfile( String target, String username, boolean force) {
        logger.debug("Setting user profile: " + username + ": " + force);
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetUserProfile, target,
                entry("username", username),
                entry("force", force)
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting user profile", e);
        }
    }

    /**
     * Enables the home channel on the device.
     * @param target the device URN.
     */
    @SuppressWarnings("unused")
    public void enableHomeChannel(String target) {
        setHomeChannelState(target, true);
    }

    /**
     * Disables the home channel on the device.
     * @param target the device URN.
     */
    @SuppressWarnings("unused")
    public void disableHomeChannel(String target) {
        setHomeChannelState(target, false);
    }

    private void setHomeChannelState(String target, boolean enabled) {
        logger.debug("Setting home channel state.");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.SetHomeChannelState, target,
            entry("enabled", enabled)               
        );

        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            logger.error("Error setting home channel state", e);
        }
    }

    // restart/powering down device is currently not supported

    // public  void restartDevice( String target) {
    //     logger.debug("Restarting device: " + target);
    //     powerDownDevice( target, true);
    // }

    // public  void powerDownDevice( String target) {
    //     logger.debug("Powering down device: " + target);
    //     powerDownDevice( target, true);
    // }

    // private  void powerDownDevice( String target, boolean restart) {
    //     Map<String, Object> req = RelayUtils.buildRequest(RequestType.PowerOff, target,
    //             entry("restart", restart)
    //     );

    //     try {
    //         sendRequest(req);
    //     } catch (EncodeException | IOException | InterruptedException e) {
    //         logger.error("Error powering down device", e);
    //     }
    // }

    /**
     * Terminates a workflow.  This method is usually called
     * after your workflow has completed and you would like to end the
     * workflow by calling end_interaction(), where you can then terminate
     * the workflow.
     */
    public void terminate() {
        logger.debug("Terminating workflow");
        Map<String, Object> req = RelayUtils.buildRequest(RequestType.Terminate);
        try {
            sendRequest(req);
        } catch (EncodeException | IOException | InterruptedException e) {
            if (e.getCause() instanceof java.nio.channels.ClosedChannelException) {
                // looks like the websocket is already closed. If we are terminating, eat it.
                logger.debug("websocket is already closed on terminate");
            } else {
                logger.error("Error terminating workflow", e);
            }
        }

    }

    // HELPER FUNCTIONS ##############

    /**
     * Parses out and retrieves the source URN from a Start Event.
     * @param startEvent the start event.
     * @return the source URN.
     */
    @SuppressWarnings("unused")
    public static String getSourceUriFromStartEvent(StartEvent startEvent) {
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
        // Remove else if statement after ticket PE-19596 is complete
        else if (object instanceof ArrayList){
            StringBuilder s = new StringBuilder();
            for (Double d : (ArrayList<Double>)object) {
                s.append(Character.valueOf((char) d.byteValue()));
            }
            return s.toString();
        }
        return sourceUri;
    }

    // The server host name.  Used for updating an access token, starting a workflow through an HTTP trigger,
    // or retrieving information on a device through the server.
    static final String SERVER_HOSTNAME = "all-main-pro-ibot.relaysvr.com";

    // The version of the Java SDK.
    static final String SDK_VERSION = "relay-sdk-java/2.0.0-pre";

    // The auth hostname used in URL when granting a new access token.
    static final String AUTH_HOSTNAME = "auth.relaygo.com";

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
        String grantUrl = "https://" + AUTH_HOSTNAME + "/oauth2/token";
        
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
                    .setHeader("User-Agent", SDK_VERSION)
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

    /**
     * A convenience method for sending an HTTP trigger to the Relay server.
     * This generally would be used in a third-party system to start a Relay
     * workflow via an HTTP trigger and optionally pass data to it with
     * action_args. 
     * If the accessToken has expired and the request gets a 401 response,
     * a new access_token will be automatically generated via the refreshToken,
     * and the request will be resubmitted with the new accessToken. Otherwise
     * the refresh token won't be used.
     * This method will return a tuple of (requests.Response, access_token)
     * where you can inspect the http response, and get the updated accessToken
     * if it was updated (otherwise the original access_token will be returned).
     * @param accessToken the current access token. Can be a placeholder value
     *         and this method will generate a new one and return it. If the
     *         original value of the access token passed in here has expired,
     *         this method will also generate a new one and return it.
     * @param refreshToken the permanent refresh_token that can be used to
     *         obtain a new accessToken. The caller should treat the refresh
     *         token as very sensitive data, and secure it appropriately.
     * @param clientId the auth_sdk_id as returned from "relay env".
     * @param workflowId the workflow_id as returned from "relay workflow list".
     *         Usually starts with "wf_".
     * @param subscriberId the subscriber UUID as returned from "relay whoami".
     * @param userId the IMEI of the target device, such as 990007560023456.
     * @param targets the device URN on which you would like to trigger the workflow.
     * @param actionArgs  a Map of any key/value arguments you want
     *         to pass in to the workflow that gets started by this trigger.
     * @return
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public Map<String, String> triggerWorkflow(String accessToken, String refreshToken,
                                               String clientId, String workflowId,
                                               String subscriberId, String userId,
                                               String[] targets, Map<String, String> actionArgs) {
        // Create a Map containing the query parameters
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("subscriber_id", subscriberId);
        queryParams.put("user_id", userId);

        // Create the URL and append the encoded query parameters to the URL
        String url = "https://" + SERVER_HOSTNAME + "/ibot/workflow/" + workflowId + encodeQueryParams(queryParams);

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
                    .setHeader("User-Agent", SDK_VERSION)
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
                    .setHeader("User-Agent", SDK_VERSION)
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

    /**
     * A convenience method for getting all the details of a device.
     * This will return quite a bit of data regarding device configuration and
     * state. The result, if the query was successful, should have a large JSON
     * dictionary.
     * @param accessToken the current access token. Can be a placeholder value
     *         and this method will generate a new one and return it. If the
     *         original value of the access token passed in here has expired,
     *         this method will also generate a new one and return it.
     * @param refreshToken the permanent refresh_token that can be used to
     *         obtain a new accessToken. The caller should treat the refresh
     *         token as very sensitive data, and secure it appropriately.
     * @param clientId the auth_sdk_id as returned from "relay env".
     * @param subscriberId the subscriber UUID as returned from "relay whoami".
     * @param userId the IMEI of the target device, such as 990007560023456.
     * @return a Map containing the response and the access token.
     */
    @SuppressWarnings("unused")
    public Map<String, String> fetchDevice(String accessToken, String refreshToken, String clientId, String subscriberId, String userId) {
        // Create a Map containing the query parameters
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("subscriber_id", subscriberId);

        // Create a URL and append the encoded query parameters to the URL
        String url = "https://" + SERVER_HOSTNAME + "/relaypro/api/v1/device/" + userId + encodeQueryParams(queryParams);

        // Create a new HttpClient and HttpRequest, and add the headers and URL to the request
        HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .setHeader("User-Agent", SDK_VERSION)
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
                        .setHeader("User-Agent", SDK_VERSION)
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

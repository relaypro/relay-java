// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import com.relaypro.sdk.types.ButtonEvent;
import com.relaypro.sdk.types.CallConnectedEvent;
import com.relaypro.sdk.types.CallDisconnectedEvent;
import com.relaypro.sdk.types.CallFailedEvent;
import com.relaypro.sdk.types.CallProgressingEvent;
import com.relaypro.sdk.types.CallReceivedEvent;
import com.relaypro.sdk.types.CallRingingEvent;
import com.relaypro.sdk.types.CallStartEvent;
import com.relaypro.sdk.types.IncidentEvent;
import com.relaypro.sdk.types.InteractionLifecycleEvent;
import com.relaypro.sdk.types.NotificationEvent;
import com.relaypro.sdk.types.PlayInboxMessagesEvent;
import com.relaypro.sdk.types.PromptEvent;
import com.relaypro.sdk.types.SmsEvent;
import com.relaypro.sdk.types.SpeechEvent;
import com.relaypro.sdk.types.StartEvent;
import com.relaypro.sdk.types.StopEvent;
import com.relaypro.sdk.types.TimerEvent;
import com.relaypro.sdk.types.TimerFiredEvent;

/**
 * A representation of a Relay workflow instance that can receive events from the
 * Relay server, and is where you implement your workflow logic. Override these
 * empty event callbacks with the logic you want to perform upon each event type.
 * The {@link Relay} object provides the context for invoking actions. The Event
 * objects passed in to you here provides information about the event and what
 * triggered it.  For more information on each of these methods and their events,
 * see the classes under the "types" directory in the src folder.
 */
public abstract class Workflow implements Cloneable {

    /**
     * Your workflow is starting.
     * @param relay a relay device context.
     * @param startEvent your workflow has been triggered. Contains information on the type of trigger that started the workflow.
     */
    public void onStart(Relay relay, StartEvent startEvent) {
    }

    /**
     * Your workflow is stopping.
     * @param relay a relay device context.
     * @param stopEvent information on why your workflow has stopped.
     */
    public void onStop(Relay relay, StopEvent stopEvent) {
    }

    /**
     * An interaction is starting, resuming, or ending.
     * @param relay a relay device context.
     * @param lifecycleEvent an interaction has started, ended, resumed, been suspended or failed.
     */
    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
    }

    /**
     * Text-to-speech stream has started or stopped on the device.
     * @param relay a relay device context.
     * @param promptEvent marks the beginning and end of text-to-speech delivery.
     */
    public void onPrompt(Relay relay, PromptEvent promptEvent) {
    }

    /**
     * An unnamed timer has fired.
     * @param relay a relay device context.
     * @param timerEvent an unnamed timer has fired.
     */
    public void onTimer(Relay relay, TimerEvent timerEvent) {
    }

    /**
     * A named timer has fired.
     * @param relay a relay device context.
     * @param timerFiredEvent a named timer has fired.
     */
    public void onTimerFired(Relay relay, TimerFiredEvent timerFiredEvent) {
    }

    /**
     * The talk or assistant button has been pressed.
     * @param relay a relay device context.
     * @param buttonEvent a button has been pressed during a running workflow.
     */
    public void onButton(Relay relay, ButtonEvent buttonEvent) {
    }

    /**
     * A broadcast or alert has been sent.
     * @param relay a relay device context.
     * @param notificationEvent a device has acknowledged an alert.
     */
    public void onNotification(Relay relay, NotificationEvent notificationEvent) {
    }

    /**
     * A callback for the SmsEvent (TBD).
     * @param relay a relay device context.
     * @param smsEvent an SMS event.
     */
    public void onSms(Relay relay, SmsEvent smsEvent) {
    }

    /**
     * You have spoken into the device, typically when listen() is happening.
     * @param relay a relay device context.
     * @param speechEvent you have spoken into the device.
     */
    public void onSpeech(Relay relay, SpeechEvent speechEvent) {
    }

    /**
     * An incident has been created.
     * @param relay a relay device context.
     * @param incidentEvent an incident has been resolved.
     */
    public void onIncident(Relay relay, IncidentEvent incidentEvent) {
    }

    /**
     * There is a request to make an outbound call. This event can occur on
     * the caller after using the "Call X" voice command on the Assistant.
     * @param relay a Relay device context.
     * @param callStartEvent information about the outbound call request
     */
    public void onCallStartRequest(Relay relay, CallStartEvent callStartEvent) {
    }

    /**
     * The device is receiving an inbound call request. This event can occur
     * on the callee.
     * @param relay a Relay device context.
     * @param callReceivedEvent information about the inbound call request
     */
    public void onCallReceived(Relay relay, CallReceivedEvent callReceivedEvent) {
    }

    /**
     * The device we called is ringing. We are waiting for them to answer.
     * This event can occur on the caller.
     * @param relay a Relay device context.
     * @param callRingingEvent information about the ringing party.
     */
    public void onCallRinging(Relay relay, CallRingingEvent callRingingEvent) {
    }

    /**
     * The device we called is making progress on getting connected. This may
     * be interspersed with {@link #onCallRinging(Relay, CallRingingEvent)}.
     * This event can occur on the caller.
     * @param relay a Relay device context.
     * @param callProgressingEvent information about the called party.
     */
    public void onCallProgressing(Relay relay, CallProgressingEvent callProgressingEvent) {
    }

    /**
     * A call attempt that was ringing, progressing, or incoming is now fully
     * connected. This event can occur on both the caller and the callee.
     * @param relay a Relay device context.
     * @param callConnectedEvent information about the other party.
     */
    public void onCallConnected(Relay relay, CallConnectedEvent callConnectedEvent) {
    }

    /**
     * A call that was once connected has become disconnected. This event can
     * occur on both the caller and the callee.
     * @param relay a Relay device context.
     * @param callDisconnectedEvent information about the other party.
     */
    public void onCallDisconnected(Relay relay, CallDisconnectedEvent callDisconnectedEvent) {
    }

    /**
     * A call failed to get connected. This event can occur on both the caller
     * and the callee.
     * @param relay a Relay device context.
     * @param callFailedEvent information about the call attempt.
     */
    public void onCallFailed(Relay relay, CallFailedEvent callFailedEvent) {
    }

    /**
     * Audio is streaming in regarding a missed message.
     * @param relay a Relay device context.
     * @param playInboxMessagesEvent information about the missed message.
     */
    public void onPlayInboxMessage(Relay relay, PlayInboxMessagesEvent playInboxMessagesEvent) {
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


}

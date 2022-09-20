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
 * triggered it.
 */
public abstract class Workflow implements Cloneable {

    public void onStart(Relay relay, StartEvent startEvent) {
    }

    public void onStop(Relay relay, StopEvent stopEvent) {
    }

    public void onInteractionLifecycle(Relay relay, InteractionLifecycleEvent lifecycleEvent) {
    }

    public void onPrompt(Relay relay, PromptEvent promptEvent) {
    }

    public void onTimer(Relay relay, TimerEvent timerEvent) {
    }

    public void onTimerFired(Relay relay, TimerFiredEvent timerFiredEvent) {
    }

    public void onButton(Relay relay, ButtonEvent buttonEvent) {
    }

    public void onNotification(Relay relay, NotificationEvent notificationEvent) {
    }

    public void onSms(Relay relay, SmsEvent smsEvent) {
    }

    public void onSpeech(Relay relay, SpeechEvent speechEvent) {
    }

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

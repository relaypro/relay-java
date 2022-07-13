// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import com.relaypro.sdk.types.*;

import java.util.Map;

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

    public void onCallStartRequest(Relay relay, CallStartEvent callStartEvent) {

    }

    public void onCallReceived(Relay relay, CallReceivedEvent callReceivedEvent) {

    }

    public void onCallRinging(Relay relay, CallRingingEvent callRingingEvent) {

    }

    public void onCallProgressing(Relay relay, CallProgressingEvent callProgressingEvent) {

    }

    public void onCallConnected(Relay relay, CallConnectedEvent callConnectedEvent) {

    }

    public void onCallDisconnected(Relay relay, CallDisconnectedEvent callDisconnectedEvent) {

    }

    public void onCallFailed(Relay relay, CallFailedEvent callFailedEvent) {

    }

    public void onPlayInboxMessage(Relay relay, PlayInboxMessagesEvent playInboxMessagesEvent) {

    }


    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


}

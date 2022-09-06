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

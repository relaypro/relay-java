// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import java.util.Map;

public abstract class Workflow implements Cloneable {

    public void onStart(Map<String, Object> startEvent) {
    }

    public void onStop(Map<String, Object> stopEvent) {
    }

    public void onInteractionLifecycle(Map<String, Object> lifecycleEvent) {
    }

    public void onPrompt(Map<String, Object> promptEvent) {
    }

    public void onTimer(Map<String, Object> timerEvent) {
    }

    public void onTimerFired(Map<String, Object> timerFiredEvent) {
    }

    public void onButton(Map<String, Object> buttonEvent) {
    }

    public void onNotification() {
    }

    public void onSms() {

    }

    public void onAudio() {

    }

    public void onIncident() {

    }

    public void onCallStartRequest() {

    }

    public void onCallReceived() {

    }

    public void onCallRinging() {

    }

    public void onCallProgressing() {

    }

    public void onCallConnected() {

    }

    public void onCallDisconnected() {

    }

    public void onCallFailed() {

    }

    public void onPlayInboxMessage() {

    }


    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


}

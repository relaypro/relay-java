package com.relaypro.sdk;

import com.relaypro.sdk.types.*;

enum EventType {
    Start("wf_api_start_event", StartEvent.class),
    Stop("wf_api_stop_event", StopEvent.class),
    InteractionLifecycle("wf_api_interaction_lifecycle_event", InteractionLifecycleEvent.class),
    Prompt("wf_api_prompt_event", PromptEvent.class),
    Timer("wf_api_timer_event", TimerEvent.class),
    TimerFired("wf_api_timer_fired_event", TimerFiredEvent.class),
    Button("wf_api_button_event", ButtonEvent.class),
    Notification("wf_api_notification_event", NotificationEvent.class),
    Sms("wf_api_sms_event", SmsEvent.class),
    Speech("wf_api_speech_event", SpeechEvent.class),
    Incident("wf_api_incident_event", IncidentEvent.class),
    CallStart("wf_api_call_start_request_event", CallStartEvent.class),
    CallReceived("wf_api_call_received_event", CallReceivedEvent.class),
    CallRinging("wf_api_call_ringing_event", CallRingingEvent.class),
    CallProgressing("wf_api_call_progressing_event", CallProgressingEvent.class),
    CallConnected("wf_api_call_connected_event", CallConnectedEvent.class),
    CallDisconnected("wf_api_call_disconnected_event", CallDisconnectedEvent.class),
    CallFailed("wf_api_call_failed_event", CallFailedEvent.class),
    PlayInboxMessages("wf_api_play_inbox_messages_event", PlayInboxMessagesEvent.class);
    
    private final String value;
    private final Class eventClass;
    public String value() {
        return value;
    }
    public Class eventClass() { 
        return eventClass; 
    }

    EventType(String value, Class eventClass) {
        this.value = value;
        this.eventClass = eventClass;
    }

    public static EventType getByType(String type) {
        for (EventType et : EventType.values()) {
            if (et.value.equals(type)) {
                return et;
            }
        }
        return null;
    }
        
}

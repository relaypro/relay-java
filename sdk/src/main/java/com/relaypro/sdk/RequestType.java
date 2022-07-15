// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

enum RequestType {

    StartInteraction("wf_api_start_interaction_request"),
    EndInteraction("wf_api_end_interaction_request"),
    Translate("wf_api_translate_request"),
    Say("wf_api_say_request"),
    Listen("wf_api_listen_request"),
    SetTimer("wf_api_set_timer_request"),
    ClearTimer("wf_api_clear_timer_request"),
    StartTimer("wf_api_start_timer_request"),
    StopTimer("wf_api_stop_timer_request"),
    SetLeds("wf_api_set_led_request"),
    Play("wf_api_play_request"),
    PlayInboxMessages("wf_api_play_inbox_messages_request"),
    InboxCount("wf_api_inbox_count_request"),
    StopPlayback("wf_api_stop_playback_request"),
    Vibrate("wf_api_vibrate_request"),
    GroupQuery("wf_api_group_query_request"),
    GetDeviceInfo("wf_api_get_device_info_request"),
    SetVar("wf_api_set_var_request"),
    GetVar("wf_api_get_var_request"),
    UnsetVar("wf_api_unset_var_request"),
    SendNotification("wf_api_notification_request"),
    SetDeviceInfo("wf_api_set_device_info_request"),
    SetDeviceMode("wf_api_set_device_mode_request"),
    SetUserProfile("wf_api_set_user_profile_request"),
    SetChannel("wf_api_set_channel_request"),
    SetHomeChannelState("wf_api_set_home_channel_state_request"),
    PowerOff("wf_api_device_power_off_request"),
    Terminate("wf_api_terminate_request"), 
    LogAnalytics("wf_api_log_analytics_event_request"),
    ResolveIncident("wf_api_resolve_incident_request"),
    CreateIncident("wf_api_create_incident_request");
    
    private final String value;
    public String value() {
        return value;
    }

    RequestType(String value) {
        this.value = value;
    }
}

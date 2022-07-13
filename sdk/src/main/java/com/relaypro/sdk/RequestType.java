// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

enum RequestType {

    StartInteraction("wf_api_start_interaction_request"),
    EndInteraction("wf_api_end_interaction_request"),
    Say("wf_api_say_request"),
    SetTimer("wf_api_set_timer_request"),
    ClearTimer("wf_api_clear_timer_request"),
    SetLeds("wf_api_set_led_request"),
    Play("wf_api_play_request"),
    StopPlayback("wf_api_stop_playback_request"),
    Vibrate("wf_api_vibrate_request"),
    GetDeviceInfo("wf_api_get_device_info_request"),
    SetDeviceInfo("wf_api_set_device_info_request"),
    SetDeviceMode("wf_api_set_device_mode_request"),
    SetUserProfile("wf_api_set_user_profile_request"),
    SetChannel("wf_api_set_channel_request"),
    PowerOff("wf_api_device_power_off_request"),
    Terminate("wf_api_terminate_request"); 
    
    private final String value;
    public String value() {
        return value;
    }

    RequestType(String value) {
        this.value = value;
    }
}

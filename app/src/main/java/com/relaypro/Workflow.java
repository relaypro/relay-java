package com.relaypro;

public abstract class Workflow {

    protected void onStart(Object startEvent) {
        
    }
    
    protected void onInteractionLifecycle(Object lifecycleEvent) {
        
    }
    
    protected void OnPrompt(func(promptEvent PromptEvent))         // seperate into start and stop?
    protected void OnTimerFired(func(timerFiredEvent TimerFiredEvent))
    protected void OnButton(func(buttonEvent ButtonEvent))

//     OnStop(func(stopEvent StopEvent))
//     OnTimer(func(timerEvent TimerEvent))
//     OnNotification(func())
//     OnSms(func())
//     OnAudio(func())
//     OnIncident(func())
//     OnCallStartRequest(func())
//     OnCallReceived(func())
//     OnCallRinging(func())
//     OnCallProgressing(func())
//     OnCallConnected(func())
//     OnCallDisconnected(func())
//     OnCallFailed(func())
//     OnPlayInboxMessage(func())

    // api
    protected void StartInteraction(sourceUri string) StartInteractionResponse
    protected void SetTimer(timerType TimerType, name string, timeout uint64, timeoutType TimeoutType) SetTimerResponse
    protected void ClearTimer(name string) ClearTimerResponse
    protected void Say(sourceUri string, text string, lang string) SayResponse
    protected void Play(sourceUri string, filename string) string
    protected void StopPlayback(sourceUri string, ids []string) StopPlaybackResponse
    protected void SwitchAllLedOn(sourceUri string, color string) SetLedResponse
    protected void SwitchAllLedOff(sourceUri string) SetLedResponse
    protected void Rainbow(sourceUri string, rotations int64) SetLedResponse
    protected void Rotate(sourceUri string, color string) SetLedResponse
    protected void Flash(sourceUri string, color string) SetLedResponse
    protected void Breathe(sourceUri string, color string) SetLedResponse
    protected void SetLeds(sourceUri string, effect LedEffect, args LedInfo) SetLedResponse
    protected void Vibrate(sourceUri string, pattern []uint64) VibrateResponse
    protected void GetDeviceName(sourceUri string, refresh bool) string
    protected void GetDeviceId(sourceUri string, refresh bool) string
    protected void GetDeviceAddress(sourceUri string, refresh bool) string
    protected void GetDeviceLatLong(sourceUri string, refresh bool) []float64
    protected void GetDeviceIndoorLocation(sourceUri string, refresh bool) string
    protected void GetDeviceBattery(sourceUri string, refresh bool) uint64
    protected void GetDeviceType(sourceUri string, refresh bool) string
    protected void GetDeviceUsername(sourceUri string, refresh bool) string
    protected void GetDeviceLocationEnabled(sourceUri string, refresh bool) bool
    protected void SetDeviceName(sourceUri string, name string) SetDeviceInfoResponse
    protected void EnableLocation(sourceUri string) SetDeviceInfoResponse
    protected void DisableLocation(sourceUri string) SetDeviceInfoResponse
    protected void SetUserProfile(sourceUri string, username string, force bool) SetUserProfileResponse
    protected void SetChannel(sourceUri string, channelName string, suppressTTS bool, disableHomeChannel bool) SetChannelResponse

    protected void SetDeviceMode(sourceUri string, mode DeviceMode) SetDeviceModeResponse

    protected void RestartDevice(sourceUri string) DevicePowerOffResponse
    protected void PowerDownDevice(sourceUri string) DevicePowerOffResponse
    protected void Terminate()
    
}

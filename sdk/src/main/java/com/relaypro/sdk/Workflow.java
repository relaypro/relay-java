package com.relaypro.sdk;

public abstract class Workflow {

    protected void onStart(Object startEvent) {
        
    }
    
    protected void onInteractionLifecycle(Object lifecycleEvent) {
        
    }
    
//    protected void OnPrompt(PromptEvent promptEvent ){}         // seperate into start and stop?
//    protected void OnTimerFired(TimerFiredEvent timerFiredEvent ){}
//    protected void OnButton(ButtonEvent buttonEvent ){}
//
//    protected void OnStop(StopEvent stopEvent ){}
//    protected void OnTimer(TimerEvent timerEvent ){}
//    protected void OnNotification(){}
//    protected void OnSms(){}
//    protected void OnAudio(){}
//    protected void OnIncident(){}
//    protected void OnCallStartRequest(){}
//    protected void OnCallReceived(){}
//    protected void OnCallRinging(){}
//    protected void OnCallProgressing(){}
//    protected void OnCallConnected(){}
//    protected void OnCallDisconnected(){}
//    protected void OnCallFailed(){}
//    protected void OnPlayInboxMessage(){}
//
//    // api
//    protected StartInteractionResponse StartInteraction(String  sourceUri ) {}
//    protected SetTimerResponse SetTimer(TimerType timerType , String name , long timeout , TimeoutType timeoutType ) {}
//    protected ClearTimerResponse ClearTimer(String  name ) {}
//    protected SayResponse Say(String  sourceUri , String text , String lang) {}
//    protected string Play(String  sourceUri , String filename ) {}
//    protected StopPlaybackResponse StopPlayback(String  sourceUri , String[] ids []) {}
//    protected SetLedResponse SwitchAllLedOn(String source , String color ) {}
//    protected SetLedResponse SwitchAllLedOff(String source) {}
//    protected SetLedResponse Rainbow(String source, long rotations) {}
//    protected SetLedResponse Rotate(String source, String color ) {}
//    protected SetLedResponse Flash(String source, String color ) {}
//    protected SetLedResponse Breathe(String source, String color ) {}
//    protected SetLedResponse SetLeds(String source, LedEffect effect , LedInfo args ){} 
//    protected VibrateResponse Vibrate(String source, long[] pattern) {}
//    protected string GetDeviceName(String source, boolean refresh ) {}
//    protected string GetDeviceId(String source, boolean refresh ) {}
//    protected string GetDeviceAddress(String source, boolean refresh ){} 
//    protected double[] GetDeviceLatLong(String source, boolean refresh ) {}
//    protected string GetDeviceIndoorLocation(String source, boolean refresh ){} 
//    protected long GetDeviceBattery(String source, boolean refresh ){} 
//    protected String GetDeviceType(String source, boolean refresh ){} 
//    protected String GetDeviceUsername(String source, boolean refresh ){} 
//    protected boolean GetDeviceLocationEnabled(String source, boolean refresh ){} 
//    protected SetDeviceInfoResponse SetDeviceName(String source, String name){} 
//    protected SetDeviceInfoResponse EnableLocation(String source){} 
//    protected SetDeviceInfoResponse DisableLocation(String source){} 
//    protected SetUserProfileResponse SetUserProfile(String source,String  username , boolean force ){} 
//    protected SetChannelResponse SetChannel(String source, String channelName , boolean suppressTTS , boolean disableHomeChannel ){} 
//    
//    protected SetDeviceModeResponse SetDeviceMode(String sourceUri , DeviceMode mode ){} 
//
//    protected DevicePowerOffResponse RestartDevice(String sourceUri ){} 
//    protected DevicePowerOffResponse PowerDownDevice(String sourceUri ){} 
//    protected void Terminate(){}
    
}

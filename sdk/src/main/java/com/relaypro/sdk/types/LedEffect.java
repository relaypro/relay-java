package com.relaypro.sdk.types;

public enum LedEffect {
    
    RAINBOW("rainbow"), 
    ROTATE("rotate"), 
    FLASH("flash"), 
    BREATHE("breathe"), 
    STATIC("static"), 
    OFF("off");

    private final String value;
    public String value() {
        return value;
    }

    LedEffect(String value) {
        this.value = value;
    }
    
}

// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * The supported languages that can be used for speech, listending, or 
 * translation on the device.
 */
public enum LanguageType {

    English("en-US"),
    German("de-DE"),
    Spanish("es-ES"),
    French("fr-FR"),
    Italian("it-IT"),
    Russian("ru-RU"),
    Swedish("sv-SE"),
    Turkish("tr-TR"),
    Hindi("hi-IN"),
    Icelandic("is-IS"),
    Japanese("ja-JP"),
    Korean("ko-KR"),
    Polish("pl-PK"),
    Portuguese("pt-BR"),
    Norwegian("nb-NO"),
    Dutch("nl-NL"),
    Chinese("zh");

    private final String value;

    /**
     * Returns the LanguageType enum's value.
     * @return a String representing the language.
     */
    public String value() {
        return value;
    }

    LanguageType(String value) {
        this.value = value;
    }
    
}

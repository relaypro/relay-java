package com.relaypro.sdk.types;

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
    public String value() {
        return value;
    }

    LanguageType(String value) {
        this.value = value;
    }
    
}

package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CardRole {
    RED, BLUE, NEUTRAL, ASSASSIN;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static CardRole fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}

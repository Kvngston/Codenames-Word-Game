package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Phase {
    LOBBY, CLUE, GUESSING, FINISHED;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Phase fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}

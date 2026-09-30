package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Seat {
    SPYMASTER, OPERATIVE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Seat fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}

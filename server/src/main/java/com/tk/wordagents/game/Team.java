package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Team {
    RED, BLUE;

    public Team other() {
        return this == RED ? BLUE : RED;
    }

    public CardRole cardRole() {
        return this == RED ? CardRole.RED : CardRole.BLUE;
    }

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Team fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}

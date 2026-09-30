package com.tk.wordagents.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A clue number or guess budget: a whole number, or unlimited.
 * On the wire it is a JSON number or the string {@code "unlimited"}; in the database, NULL means unlimited.
 */
public record Count(Integer value) {

    public static final Count UNLIMITED = new Count(null);

    public static Count of(int value) {
        return new Count(value);
    }

    public static Count fromColumn(Integer value) {
        return new Count(value);
    }

    public boolean unlimited() {
        return value == null;
    }

    @JsonValue
    public Object json() {
        return value == null ? "unlimited" : value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Count fromJson(Object raw) {
        if ("unlimited".equals(raw)) return UNLIMITED;
        if (raw instanceof Integer number) return of(number);
        throw new IllegalArgumentException("Expected a whole number or \"unlimited\".");
    }

    @Override
    public String toString() {
        return value == null ? "∞" : value.toString();
    }
}

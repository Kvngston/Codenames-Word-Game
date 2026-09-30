package com.tk.wordagents.game;

/** A move that the rules don't allow; reported to the player as a 409. */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }
}

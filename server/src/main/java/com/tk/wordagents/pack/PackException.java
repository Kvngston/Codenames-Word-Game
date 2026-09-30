package com.tk.wordagents.pack;

import org.springframework.http.HttpStatus;

/** A word-pack request that can't be served, with the HTTP status to report. */
public class PackException extends RuntimeException {

    private final HttpStatus status;

    PackException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static PackException notFound() {
        return new PackException(HttpStatus.NOT_FOUND, "No word pack with that code. Check it and try again.");
    }

    static PackException notYours() {
        return new PackException(HttpStatus.FORBIDDEN, "Only the device that made this pack can change it.");
    }
}

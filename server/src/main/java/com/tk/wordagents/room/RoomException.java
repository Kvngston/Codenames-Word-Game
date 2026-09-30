package com.tk.wordagents.room;

import org.springframework.http.HttpStatus;

/** A request about a room that can't be served, with the HTTP status to report. */
public class RoomException extends RuntimeException {

    private final HttpStatus status;

    public RoomException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static RoomException notFound() {
        return new RoomException(HttpStatus.NOT_FOUND, "No room with that code. Check it and try again.");
    }

    static RoomException badSeat() {
        return new RoomException(HttpStatus.FORBIDDEN, "Your seat in this room has expired. Join again.");
    }
}

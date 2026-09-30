package com.tk.wordagents.room;

import com.tk.wordagents.config.RateLimitException;
import com.tk.wordagents.game.GameException;
import com.tk.wordagents.pack.PackException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every API error is {@code {"error": "..."}}, which the client shows as a toast. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(RoomException.class)
    ResponseEntity<Map<String, String>> room(RoomException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    ResponseEntity<Map<String, String>> tooMany(RateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
            .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(PackException.class)
    ResponseEntity<Map<String, String>> pack(PackException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GameException.class)
    ResponseEntity<Map<String, String>> game(GameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
            .map(error -> error.getDefaultMessage()).orElse("Invalid request.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request."));
    }
}

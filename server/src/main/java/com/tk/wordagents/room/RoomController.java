package com.tk.wordagents.room;

import com.tk.wordagents.game.GameAction;
import com.tk.wordagents.room.RoomService.SeatGrant;
import com.tk.wordagents.room.RoomViews.RoomView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RoomController {

    record NameRequest(
        @NotBlank(message = "Enter a name.") @Size(max = 24, message = "Keep names to 24 characters.") String name) {}

    private final RoomService rooms;

    RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/healthz")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    SeatGrant create(@Valid @RequestBody NameRequest request) {
        return rooms.createRoom(request.name());
    }

    @PostMapping("/rooms/{code}/players")
    @ResponseStatus(HttpStatus.CREATED)
    SeatGrant join(@PathVariable String code, @Valid @RequestBody NameRequest request) {
        return rooms.join(code, request.name());
    }

    /** The player's current view; clients load it on connect, then receive updates over STOMP. */
    @GetMapping("/rooms/{code}/view")
    ResponseEntity<RoomView> view(@PathVariable String code, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.view(code, bearer(auth)));
    }

    @PostMapping("/rooms/{code}/actions")
    RoomView act(@PathVariable String code, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestBody GameAction action) {
        return rooms.act(code, bearer(auth), action);
    }

    @DeleteMapping("/rooms/{code}/players/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable String code, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        rooms.leave(code, bearer(auth));
    }

    private static String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}

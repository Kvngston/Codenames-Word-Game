package com.tk.wordagents.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the real server over HTTP and STOMP, against MySQL and RabbitMQ in containers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RoomFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:4.1")
        .withCopyToContainer(Transferable.of("[rabbitmq_stomp].\n"), "/etc/rabbitmq/enabled_plugins")
        .withExposedPorts(61613)
        .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("app.stomp-relay.host", RABBIT::getHost);
        registry.add("app.stomp-relay.port", () -> RABBIT.getMappedPort(61613));
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<StompSession> sessions = new ArrayList<>();

    @LocalServerPort
    int port;

    record Response(int status, JsonNode body) {}

    record Seat(String code, String playerId, String token) {}

    @AfterEach
    void closeSessions() {
        sessions.forEach(session -> { if (session.isConnected()) session.disconnect(); });
    }

    // ---- HTTP helpers ----

    private Response call(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body().isEmpty() ? null : JSON.readTree(response.body()));
    }

    private Seat seat(Response response) {
        assertThat(response.status()).isEqualTo(201);
        return new Seat(response.body().get("code").asString(), response.body().get("playerId").asString(), response.body().get("token").asString());
    }

    private Seat create(String name) throws Exception {
        return seat(call("POST", "/rooms", "{\"name\":\"" + name + "\"}", null));
    }

    private Seat join(String code, String name) throws Exception {
        return seat(call("POST", "/rooms/" + code + "/players", "{\"name\":\"" + name + "\"}", null));
    }

    private Response act(Seat seat, String actionJson) throws Exception {
        return call("POST", "/rooms/" + seat.code() + "/actions", actionJson, seat.token());
    }

    // ---- STOMP helpers ----

    private CompletableFuture<StompSession> connectAsync(String room, String token, BlockingQueue<JsonNode> views, BlockingQueue<String> errors) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connect = new StompHeaders();
        connect.add("room", room);
        connect.add("token", token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                session.subscribe(StompAuthInterceptor.SUBSCRIBE_DESTINATION, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return JsonNode.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        views.add((JsonNode) payload);
                    }
                });
            }

            @Override
            public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errors.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errors.add("transport: " + exception.getMessage());
            }

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add("ERROR frame: " + headers.getFirst("message"));
            }
        });
    }

    private BlockingQueue<JsonNode> listen(Seat seat) throws Exception {
        BlockingQueue<JsonNode> views = new LinkedBlockingQueue<>();
        sessions.add(connectAsync(seat.code(), seat.token(), views, new LinkedBlockingQueue<>()).get(10, TimeUnit.SECONDS));
        return views;
    }

    /** Waits for the next pushed view matching the condition, skipping older ones. */
    private static JsonNode awaitView(BlockingQueue<JsonNode> views, Predicate<JsonNode> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            JsonNode view = views.poll(250, TimeUnit.MILLISECONDS);
            if (view != null && condition.test(view)) return view;
        }
        throw new AssertionError("No matching view was pushed in time");
    }

    private static List<JsonNode> cards(JsonNode view) {
        return StreamSupport.stream(view.get("cards").spliterator(), false).toList();
    }

    private static String cardWithRole(JsonNode spymasterView, String role) {
        return cards(spymasterView).stream()
            .filter(card -> role.equals(card.get("role").asString()) && !card.get("revealed").asBoolean())
            .findFirst().orElseThrow().get("id").asString();
    }

    // ---- Tests ----

    @Test
    void eachPlayerIsPushedOnlyTheirOwnView() throws Exception {
        Seat redSpy = create("Ada");
        String code = redSpy.code();
        Seat redOp = join(code, "Ben");
        Seat blueSpy = join(code, "Cy");
        Seat blueOp = join(code, "Di");

        assertThat(act(redOp, "{\"type\":\"start\"}").status()).isEqualTo(409);
        act(redSpy, "{\"type\":\"take-seat\",\"team\":\"red\",\"seat\":\"spymaster\"}");
        assertThat(act(redOp, "{\"type\":\"take-seat\",\"team\":\"red\",\"seat\":\"spymaster\"}").body().get("error").asString())
            .contains("already has a spymaster");
        act(redOp, "{\"type\":\"take-seat\",\"team\":\"red\",\"seat\":\"operative\"}");
        act(blueSpy, "{\"type\":\"take-seat\",\"team\":\"blue\",\"seat\":\"spymaster\"}");
        act(blueOp, "{\"type\":\"take-seat\",\"team\":\"blue\",\"seat\":\"operative\"}");

        BlockingQueue<JsonNode> spyViews = listen(redSpy);
        BlockingQueue<JsonNode> opViews = listen(redOp);
        // Subscribing pushes the current view, and others see the new player come online.
        awaitView(opViews, view -> view.get("you").get("connected").asBoolean());
        awaitView(spyViews, view -> StreamSupport.stream(view.get("players").spliterator(), false)
            .anyMatch(p -> p.get("id").asString().equals(redOp.playerId()) && p.get("connected").asBoolean()));

        assertThat(act(redSpy, "{\"type\":\"start\"}").status()).isEqualTo(200);
        JsonNode spyView = awaitView(spyViews, view -> view.get("phase").asString().equals("clue"));
        JsonNode opView = awaitView(opViews, view -> view.get("phase").asString().equals("clue"));
        assertThat(cards(opView)).allMatch(card -> card.get("role").isNull());
        assertThat(cards(spyView)).noneMatch(card -> card.get("role").isNull());
        assertThat(opView.toString()).doesNotContain(redSpy.token(), blueSpy.token());

        String first = spyView.get("activeTeam").asString();
        Seat firstSpy = first.equals("red") ? redSpy : blueSpy;
        Seat firstOp = first.equals("red") ? redOp : blueOp;
        Seat secondSpy = first.equals("red") ? blueSpy : redSpy;
        JsonNode firstSpyView = first.equals("red") ? spyView : call("GET", "/rooms/" + code + "/view", null, blueSpy.token()).body();

        assertThat(act(secondSpy, "{\"type\":\"give-clue\",\"word\":\"OCEAN\",\"number\":2}").status()).isEqualTo(409);
        assertThat(act(firstSpy, "{\"type\":\"give-clue\",\"word\":\"OCEAN\",\"number\":42}").status()).isEqualTo(409);
        assertThat(act(firstSpy, "{\"type\":\"give-clue\",\"word\":\"OCEAN\",\"number\":\"lots\"}").status()).isEqualTo(400);
        assertThat(act(firstSpy, "{\"type\":\"give-clue\",\"word\":\"OCEAN\",\"number\":\"unlimited\"}").status()).isEqualTo(200);

        JsonNode guessing = awaitView(opViews, view -> view.get("phase").asString().equals("guessing"));
        assertThat(guessing.get("clue").get("word").asString()).isEqualTo("OCEAN");
        assertThat(guessing.get("guessesRemaining").asString()).isEqualTo("unlimited");

        String friendly = cardWithRole(firstSpyView, first);
        assertThat(act(firstSpy, "{\"type\":\"guess\",\"cardId\":\"" + friendly + "\"}").status()).isEqualTo(409);
        assertThat(act(firstOp, "{\"type\":\"guess\",\"cardId\":\"" + friendly + "\"}").status()).isEqualTo(200);
        JsonNode afterGuess = awaitView(opViews, view -> cards(view).stream().anyMatch(card -> card.get("revealed").asBoolean()));
        assertThat(cards(afterGuess)).filteredOn(card -> !card.get("role").isNull()).hasSize(1)
            .allMatch(card -> card.get("id").asString().equals(friendly) && card.get("role").asString().equals(first));

        // The operative's REST view agrees with what was pushed.
        Response restView = call("GET", "/rooms/" + code + "/view", null, redOp.token());
        assertThat(restView.body().get("version").asLong()).isEqualTo(afterGuess.get("version").asLong());
    }

    @Test
    void theSocketRejectsBadSeatsAndForeignSubscriptions() throws Exception {
        Seat host = create("Ada");
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        assertThatThrownBy(() -> connectAsync(host.code(), "not-a-token", new LinkedBlockingQueue<>(), errors).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
        assertThat(errors.poll(5, TimeUnit.SECONDS)).contains("expired");

        Seat other = create("Eve");
        BlockingQueue<String> crossRoom = new LinkedBlockingQueue<>();
        assertThatThrownBy(() -> connectAsync(host.code(), other.token(), new LinkedBlockingQueue<>(), crossRoom).get(10, TimeUnit.SECONDS))
            .as("a seat from another room can't connect to this one")
            .isInstanceOf(ExecutionException.class);
        assertThat(crossRoom.poll(5, TimeUnit.SECONDS)).contains("expired");

        BlockingQueue<String> hostErrors = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> hostViews = new LinkedBlockingQueue<>();
        StompSession session = connectAsync(host.code(), host.token(), hostViews, hostErrors).get(10, TimeUnit.SECONDS);
        sessions.add(session);
        // Let the handler's own subscription finish before writing another frame.
        awaitView(hostViews, view -> true);
        session.subscribe("/topic/simp-user-registry", new StompSessionHandlerAdapter() {});
        assertThat(hostErrors.poll(10, TimeUnit.SECONDS)).contains("Only /user/topic/view");
    }

    @Test
    void restErrorsAreJson() throws Exception {
        Seat host = create("Ada");
        assertThat(call("GET", "/rooms/" + host.code() + "/view", null, "nope").status()).isEqualTo(403);
        assertThat(call("GET", "/rooms/ZZZZZ/view", null, host.token()).status()).isEqualTo(404);
        Response blank = call("POST", "/rooms", "{\"name\":\" \"}", null);
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.body().get("error").asString()).isEqualTo("Enter a name.");
        assertThat(call("POST", "/rooms/" + host.code() + "/actions", "{\"type\":\"dance\"}", host.token()).status()).isEqualTo(400);
    }

    @Test
    void simultaneousJoinsAreAllKept() throws Exception {
        Seat host = create("Ada");
        List<CompletableFuture<Seat>> joins = IntStream.range(0, 12)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                try {
                    return join(host.code(), "P" + i);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }))
            .toList();
        CompletableFuture.allOf(joins.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        JsonNode view = call("GET", "/rooms/" + host.code() + "/view", null, host.token()).body();
        assertThat(view.get("players").size()).isEqualTo(13);
    }

    @Test
    void leavingTheLastSeatClosesTheRoom() throws Exception {
        Seat host = create("Ada");
        Seat guest = join(host.code(), "Ben");
        assertThat(call("DELETE", "/rooms/" + host.code() + "/players/me", null, host.token()).status()).isEqualTo(204);
        JsonNode view = call("GET", "/rooms/" + host.code() + "/view", null, guest.token()).body();
        assertThat(view.get("you").get("isHost").asBoolean()).as("host passes to the next player").isTrue();
        call("DELETE", "/rooms/" + host.code() + "/players/me", null, guest.token());
        assertThat(call("GET", "/rooms/" + host.code() + "/view", null, guest.token()).status()).isEqualTo(404);
    }
}

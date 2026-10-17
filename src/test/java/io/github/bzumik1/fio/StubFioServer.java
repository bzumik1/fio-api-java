package io.github.bzumik1.fio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

final class StubFioServer implements AutoCloseable {

    static final String JSON = "application/json;charset=UTF-8";

    record RecordedRequest(String method, String path, String accept) {
    }

    record RecordedBody(String contentType, byte[] content) {

        String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private record CannedResponse(int status, String contentType, byte[] body, Duration delay) {
    }

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final List<RecordedBody> bodies = new CopyOnWriteArrayList<>();
    private final Deque<CannedResponse> queue = new ArrayDeque<>();
    private volatile CannedResponse fallback = new CannedResponse(200, JSON, new byte[0], Duration.ZERO);

    private StubFioServer(HttpServer server) {
        this.server = server;
    }

    static StubFioServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            StubFioServer stub = new StubFioServer(server);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    URI baseUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1/rest");
    }

    void respondWith(int status, String body) {
        respondWith(status, JSON, body.getBytes(StandardCharsets.UTF_8));
    }

    void respondWith(int status, String contentType, byte[] body) {
        fallback = new CannedResponse(status, contentType, body, Duration.ZERO);
    }

    void respondAfter(Duration delay, int status, String body) {
        fallback = new CannedResponse(status, JSON, body.getBytes(StandardCharsets.UTF_8), delay);
    }

    void enqueue(int status, String body) {
        enqueue(status, JSON, body.getBytes(StandardCharsets.UTF_8));
    }

    void enqueue(int status, String contentType, byte[] body) {
        synchronized (queue) {
            queue.add(new CannedResponse(status, contentType, body, Duration.ZERO));
        }
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    List<RecordedBody> bodies() {
        return List.copyOf(bodies);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestHeaders().getFirst("Accept")));
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        if (requestBody.length > 0) {
            bodies.add(new RecordedBody(exchange.getRequestHeaders().getFirst("Content-Type"), requestBody));
        }
        CannedResponse canned = next();
        pause(canned.delay());
        if (!canned.contentType().isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", canned.contentType());
        }
        exchange.sendResponseHeaders(canned.status(), canned.body().length == 0 ? -1 : canned.body().length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(canned.body());
        }
    }

    private CannedResponse next() {
        synchronized (queue) {
            CannedResponse queued = queue.poll();
            return queued == null ? fallback : queued;
        }
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

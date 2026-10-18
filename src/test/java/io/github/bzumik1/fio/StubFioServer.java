package io.github.bzumik1.fio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class StubFioServer implements AutoCloseable {

    static final String JSON = "application/json;charset=UTF-8";
    static final String TOO_SOON = "Překročen limit: s tokenem je možné poslat jeden požadavek za 30 sekund.";

    private static final Duration BANK_INTERVAL = Duration.ofSeconds(30);
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final DateTimeFormatter CZECH_DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    record RecordedRequest(String method, String path, String accept) {
    }

    record RecordedBody(String contentType, byte[] content) {

        String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    static final class Hold {

        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void awaitArrival() {
            await(arrived);
        }

        void release() {
            released.countDown();
        }
    }

    private enum Delivery {
        WHOLE,
        STALLING,
        TRICKLING,
        ENDLESS
    }

    private record Answer(int status, String contentType, byte[] body, Duration handlingTime, Delivery delivery) {

        static Answer of(int status, String contentType, byte[] body) {
            return new Answer(status, contentType, body, Duration.ZERO, Delivery.WHOLE);
        }
    }

    private static final class Departure {

        private final AtomicInteger inFlight;
        private boolean left;

        Departure(AtomicInteger inFlight) {
            this.inFlight = inFlight;
        }

        void leave() {
            if (!left) {
                left = true;
                inFlight.decrementAndGet();
            }
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final @Nullable FakeTime time;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private final List<Instant> arrivals = new ArrayList<>();
    private final List<RecordedBody> bodies = new CopyOnWriteArrayList<>();
    private final Deque<Answer> queue = new ArrayDeque<>();
    private final Map<String, Answer> routes = new LinkedHashMap<>();
    private final Deque<Hold> pendingHolds = new ArrayDeque<>();
    private final List<Hold> allHolds = new CopyOnWriteArrayList<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private final CountDownLatch dropped = new CountDownLatch(1);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger mostInFlight = new AtomicInteger();
    private volatile Function<RecordedRequest, Answer> fallback = request -> Answer.of(200, JSON, new byte[0]);
    private @Nullable Instant lastArrival;

    private StubFioServer(HttpServer server, ExecutorService executor, @Nullable FakeTime time) {
        this.server = server;
        this.executor = executor;
        this.time = time;
    }

    static StubFioServer start() {
        return start(null);
    }

    static StubFioServer start(@Nullable FakeTime time) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            StubFioServer stub = new StubFioServer(server, executor, time);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String historyLockedBody(LocalDate earliestDay) {
        return "Data není možné poskytnout bez silné autorizace. Pokyn k zobrazení dat si autorizujte ve Vašem"
                + " Internetovém bankovnictví a data si vyžádejte znovu. Platnost ověření je 10 minut od autorizace."
                + " Nebo požádejte o data, která nejsou starší jak 90 dnů (od " + CZECH_DATE.format(earliestDay) + ").";
    }

    URI baseUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1/rest");
    }

    void respondWith(int status, String body) {
        respondWith(status, JSON, body.getBytes(StandardCharsets.UTF_8));
    }

    void respondWith(int status, String contentType, byte[] body) {
        Answer answer = Answer.of(status, contentType, body);
        fallback = request -> answer;
    }

    void respondWithPath(String contentType) {
        fallback = request -> Answer.of(200, contentType, request.path().getBytes(StandardCharsets.UTF_8));
    }

    void respondHistoryLocked() {
        respondWith(422, "text/plain;charset=UTF-8", historyLockedBody(earliestUnlockedDay()).getBytes(StandardCharsets.UTF_8));
    }

    LocalDate earliestUnlockedDay() {
        return LocalDate.ofInstant(requireTime().now(), PRAGUE).minusDays(90);
    }

    void route(String pathFragment, int status, String contentType, byte[] body) {
        synchronized (routes) {
            routes.put(pathFragment, Answer.of(status, contentType, body));
        }
    }

    void enqueue(int status, String body) {
        enqueue(status, JSON, body.getBytes(StandardCharsets.UTF_8));
    }

    void enqueue(int status, String contentType, byte[] body) {
        enqueue(Answer.of(status, contentType, body));
    }

    void enqueueTaking(Duration handlingTime, int status, String body) {
        requireTime();
        enqueue(new Answer(status, JSON, body.getBytes(StandardCharsets.UTF_8), handlingTime, Delivery.WHOLE));
    }

    void enqueueStallingAfterPartOfBody(String partOfBody) {
        enqueue(new Answer(200, JSON, partOfBody.getBytes(StandardCharsets.UTF_8), Duration.ZERO, Delivery.STALLING));
    }

    void enqueueTricklingAfterPartOfBody(int status, String partOfBody) {
        enqueue(new Answer(status, "text/html", partOfBody.getBytes(StandardCharsets.UTF_8), Duration.ZERO, Delivery.TRICKLING));
    }

    void enqueueEndless(int status, byte[] repeatedPart) {
        enqueue(new Answer(status, "text/html", repeatedPart.clone(), Duration.ZERO, Delivery.ENDLESS));
    }

    boolean awaitDroppedConnection(Duration timeout) throws InterruptedException {
        return dropped.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    Hold holdNextRequest() {
        Hold hold = new Hold();
        allHolds.add(hold);
        synchronized (pendingHolds) {
            pendingHolds.add(hold);
        }
        return hold;
    }

    List<RecordedRequest> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    List<Instant> arrivals() {
        synchronized (requests) {
            return List.copyOf(arrivals);
        }
    }

    List<RecordedBody> bodies() {
        return List.copyOf(bodies);
    }

    int mostRequestsInFlight() {
        return mostInFlight.get();
    }

    @Override
    public void close() {
        closed.countDown();
        allHolds.forEach(Hold::release);
        server.stop(0);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The stub bank's threads did not stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        Departure departure = new Departure(inFlight);
        try {
            answer(exchange, departure);
        } finally {
            departure.leave();
        }
    }

    private void answer(HttpExchange exchange, Departure departure) throws IOException {
        RecordedRequest request = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestHeaders().getFirst("Accept"));
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        if (requestBody.length > 0) {
            bodies.add(new RecordedBody(exchange.getRequestHeaders().getFirst("Content-Type"), requestBody));
        }
        boolean tooSoon = arrive(request);
        Hold hold = nextHold();
        if (hold != null) {
            hold.arrived.countDown();
            await(hold.released);
        }
        Answer answer = tooSoon ? Answer.of(409, "text/plain;charset=UTF-8", TOO_SOON.getBytes(StandardCharsets.UTF_8)) : next(request);
        if (!answer.handlingTime().isZero()) {
            requireTime().advance(answer.handlingTime());
        }
        if (!answer.contentType().isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", answer.contentType());
        }
        switch (answer.delivery()) {
            case WHOLE -> send(exchange, answer, departure);
            case STALLING -> stall(exchange, answer);
            case TRICKLING -> trickle(exchange, answer);
            case ENDLESS -> stream(exchange, answer);
        }
    }

    private void send(HttpExchange exchange, Answer answer, Departure departure) throws IOException {
        byte[] body = answer.body();
        if (body.length == 0) {
            departure.leave();
            exchange.sendResponseHeaders(answer.status(), -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(answer.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body, 0, body.length - 1);
            departure.leave();
            output.write(body, body.length - 1, 1);
        }
    }

    private boolean arrive(RecordedRequest request) {
        synchronized (requests) {
            requests.add(request);
            if (time == null) {
                return false;
            }
            Instant arrival = time.now();
            arrivals.add(arrival);
            Instant previous = lastArrival;
            lastArrival = arrival;
            return previous != null && Duration.between(previous, arrival).abs().compareTo(BANK_INTERVAL) < 0;
        }
    }

    private void stall(HttpExchange exchange, Answer answer) throws IOException {
        exchange.sendResponseHeaders(answer.status(), answer.body().length + 1024L);
        OutputStream output = exchange.getResponseBody();
        output.write(answer.body());
        output.flush();
        await(closed);
        exchange.close();
    }

    private void trickle(HttpExchange exchange, Answer answer) throws IOException {
        int missing = 1024;
        exchange.sendResponseHeaders(answer.status(), answer.body().length + (long) missing);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(answer.body());
            output.flush();
            for (int written = 0; written < missing && !closesWithin(Duration.ofMillis(20)); written++) {
                output.write('y');
                output.flush();
            }
        } catch (IOException e) {
            dropped.countDown();
        }
        exchange.close();
    }

    private void stream(HttpExchange exchange, Answer answer) throws IOException {
        exchange.sendResponseHeaders(answer.status(), 0);
        OutputStream output = exchange.getResponseBody();
        try {
            while (closed.getCount() > 0) {
                output.write(answer.body());
                output.flush();
            }
        } catch (IOException e) {
            dropped.countDown();
        }
        exchange.close();
    }

    private boolean closesWithin(Duration pause) {
        try {
            return closed.await(pause.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private @Nullable Hold nextHold() {
        synchronized (pendingHolds) {
            return pendingHolds.poll();
        }
    }

    private void enqueue(Answer answer) {
        synchronized (queue) {
            queue.add(answer);
        }
    }

    private Answer next(RecordedRequest request) {
        synchronized (queue) {
            Answer queued = queue.poll();
            if (queued != null) {
                return queued;
            }
        }
        synchronized (routes) {
            for (Map.Entry<String, Answer> route : routes.entrySet()) {
                if (request.path().contains(route.getKey())) {
                    return route.getValue();
                }
            }
        }
        return fallback.apply(request);
    }

    private FakeTime requireTime() {
        if (time == null) {
            throw new IllegalStateException("This stub bank was started without a fake clock");
        }
        return time;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

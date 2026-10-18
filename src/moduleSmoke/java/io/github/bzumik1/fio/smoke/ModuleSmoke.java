package io.github.bzumik1.fio.smoke;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.bzumik1.fio.AccountStatement;
import io.github.bzumik1.fio.FioClient;
import io.github.bzumik1.fio.FioToken;
import io.github.bzumik1.fio.ImportResult;
import io.github.bzumik1.fio.Money;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModuleSmoke {

    private static final FioToken TOKEN = FioToken.of("module-smoke-test-token");
    private static final byte[] IMPORT_ACCEPTED = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><responseImport><result>"
            + "<errorCode>0</errorCode><idInstruction>105699806</idInstruction><status>ok</status></result></responseImport>")
            .getBytes(StandardCharsets.UTF_8);

    private ModuleSmoke() {
    }

    public static void main(String[] args) throws IOException {
        byte[] statement = Files.readAllBytes(Path.of(args[0]));
        List<String> imports = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (exchange.getRequestURI().getPath().startsWith("/import/")) {
                imports.add(new String(request, StandardCharsets.UTF_8));
                answer(exchange, "application/xml", IMPORT_ACCEPTED);
            } else {
                answer(exchange, "application/json", statement);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
            requireNamedModule();
            fetchStatement(base);
            submitPayment(base, imports);
            System.out.println("Module " + FioClient.class.getModule().getName() + " works on the module path");
        } finally {
            server.stop(0);
        }
    }

    private static void requireNamedModule() {
        if (!FioClient.class.getModule().isNamed()) {
            throw new IllegalStateException("The library was loaded from the class path, not as a module");
        }
    }

    private static void fetchStatement(URI base) {
        FioClient fio = FioClient.builder(TOKEN).baseUri(base).httpClient(HttpClient.newBuilder().build()).build();

        AccountStatement fetched = fio.transactions().between(LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 30)).fetch();

        if (fetched.transactions().size() != 3) {
            throw new IllegalStateException("Expected 3 transactions, got " + fetched.transactions().size());
        }
    }

    private static void submitPayment(URI base, List<String> imports) {
        FioClient fio = FioClient.builder(TOKEN).baseUri(base).build();

        ImportResult result = fio.payments().batch()
                .domestic(payment -> payment
                        .from("2400222222/2010")
                        .to("19-2000145399/0800")
                        .amount(Money.czk("1250.00"))
                        .date(LocalDate.of(2026, 10, 19)))
                .submit();

        if (!result.isAccepted()) {
            throw new IllegalStateException("Expected the payment to be accepted, got " + result);
        }
        if (imports.size() != 1 || !imports.get(0).contains("<DomesticTransaction>")) {
            throw new IllegalStateException("Expected one import with a domestic payment, got " + imports);
        }
    }

    private static void answer(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}

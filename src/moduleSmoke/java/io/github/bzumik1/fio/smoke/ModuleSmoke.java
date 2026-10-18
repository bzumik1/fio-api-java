package io.github.bzumik1.fio.smoke;

import com.sun.net.httpserver.HttpServer;
import io.github.bzumik1.fio.AccountStatement;
import io.github.bzumik1.fio.FioClient;
import io.github.bzumik1.fio.FioToken;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class ModuleSmoke {

    private ModuleSmoke() {
    }

    public static void main(String[] args) throws IOException {
        byte[] statement = Files.readAllBytes(Path.of(args[0]));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, statement.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(statement);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
            FioClient fio = FioClient.builder(FioToken.of("smoke")).baseUri(base).build();

            AccountStatement fetched = fio.transactions().between(LocalDate.of(2012, 6, 26), LocalDate.of(2012, 6, 30)).fetch();

            if (fetched.transactions().size() != 3) {
                throw new IllegalStateException("Expected 3 transactions, got " + fetched.transactions().size());
            }
            System.out.println("Module " + FioClient.class.getModule().getName() + " works on the module path");
        } finally {
            server.stop(0);
        }
    }
}

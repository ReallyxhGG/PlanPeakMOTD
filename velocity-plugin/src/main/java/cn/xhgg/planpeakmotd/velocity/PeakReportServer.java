package cn.xhgg.planpeakmotd.velocity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class PeakReportServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 32768;

    private final Logger logger;
    private final PeakReportStore store;
    private final Supplier<VelocityConfig> configSupplier;
    private final HttpServer server;
    private final ExecutorService executor;

    PeakReportServer(Logger logger, PeakReportStore store, Supplier<VelocityConfig> configSupplier)
            throws IOException {
        this.logger = logger;
        this.store = store;
        this.configSupplier = configSupplier;

        VelocityConfig config = configSupplier.get();
        this.server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 16);
        this.executor = Executors.newFixedThreadPool(2, daemonFactory());
        this.server.setExecutor(executor);
        this.server.createContext("/report", this::handleReport);
    }

    void start() {
        server.start();
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "POST required");
                return;
            }

            VelocityConfig config = configSupplier.get();
            String suppliedToken = exchange.getRequestHeaders().getFirst("X-PlanPeak-Token");
            if (!constantTimeEquals(config.sharedToken(), suppliedToken)) {
                respond(exchange, 403, "invalid token");
                return;
            }

            byte[] bodyBytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (bodyBytes.length > MAX_BODY_BYTES) {
                respond(exchange, 413, "body too large");
                return;
            }

            Map<String, String> form = parseForm(new String(bodyBytes, StandardCharsets.UTF_8));
            String serverId = form.getOrDefault("server", "");
            LocalDate reportDay;
            int peak;
            try {
                reportDay = LocalDate.parse(form.getOrDefault("day", ""));
                peak = Integer.parseInt(form.getOrDefault("peak", ""));
            } catch (RuntimeException exception) {
                respond(exchange, 400, "invalid report");
                return;
            }

            LocalDate today = LocalDate.now(config.zoneId());
            Map<String, String> papiValues = new HashMap<>();
            form.forEach((key, value) -> {
                if (key.startsWith("papi.") && papiValues.size() < 32) {
                    String alias = key.substring("papi.".length());
                    if (PeakReportStore.isValidServerId(alias)) {
                        papiValues.put(alias, value);
                    }
                }
            });
            PeakReportStore.ReportResult result = store.report(
                    today,
                    reportDay,
                    serverId,
                    peak,
                    papiValues
            );
            if (result == PeakReportStore.ReportResult.WRONG_DAY) {
                respond(exchange, 409, "day mismatch");
                return;
            }
            if (result == PeakReportStore.ReportResult.INVALID) {
                respond(exchange, 400, "invalid server or peak");
                return;
            }
            if (result == PeakReportStore.ReportResult.FIRST_REPORT) {
                logger.info("已收到子服 {} 的今日 Plan 峰值：{}", serverId, peak);
            }
            exchange.sendResponseHeaders(204, -1);
        } catch (RuntimeException exception) {
            logger.warn("处理子服峰值回报时发生错误", exception);
            if (exchange.getResponseCode() == -1) {
                respond(exchange, 500, "internal error");
            }
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        if (body.isEmpty()) {
            return values;
        }
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "plan-peak-http-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
    }
}

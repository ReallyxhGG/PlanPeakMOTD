package cn.xhgg.planpeakmotd.folia;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

final class PeakHttpReporter {
    private final HttpClient client;

    PeakHttpReporter(int connectTimeoutSeconds) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    void report(
            FoliaBridgeConfig config,
            LocalDate day,
            int peak,
            Map<String, String> papiValues
    ) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder("server=")
                .append(encode(config.serverId()))
                .append("&day=").append(encode(day.toString()))
                .append("&peak=").append(peak);
        papiValues.forEach((alias, value) -> body
                .append("&papi.").append(encode(alias))
                .append('=').append(encode(value)));
        HttpRequest request = HttpRequest.newBuilder(config.velocityUrl())
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("X-PlanPeak-Token", config.sharedToken())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 204) {
            throw new IOException("Velocity 返回 HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

package cn.xhgg.planpeakmotd.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeakReportServerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void acceptsAuthenticatedPeakAndPapiReport() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        String token = "0123456789abcdef0123456789abcdef";
        VelocityConfig config = new VelocityConfig(
                InetAddress.getByName("127.0.0.1"),
                port,
                token,
                ZoneId.of("Asia/Shanghai"),
                AggregateMode.SUM,
                true,
                List.of("{today_peak}")
        );
        LocalDate today = LocalDate.now(config.zoneId());
        PeakReportStore store = new PeakReportStore(tempDirectory.resolve("cache.properties"));
        store.load(today);

        try (PeakReportServer server = new PeakReportServer(
                LoggerFactory.getLogger(PeakReportServerTest.class),
                store,
                () -> config
        )) {
            server.start();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI uri = URI.create("http://127.0.0.1:" + port + "/report");

            HttpResponse<String> accepted = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("X-PlanPeak-Token", token)
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "server=survival&day=" + today + "&peak=18&papi.tps=19.98"
                            ))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(204, accepted.statusCode());
            assertEquals(18, store.snapshot(today).peakOf("survival"));
            assertEquals("19.98", store.snapshot(today).papiValue("survival", "tps"));

            HttpResponse<String> rejected = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("X-PlanPeak-Token", "wrong-token-wrong-token")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "server=survival&day=" + today + "&peak=999"
                            ))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, rejected.statusCode());
            assertEquals(18, store.snapshot(today).peakOf("survival"));
        }
    }
}

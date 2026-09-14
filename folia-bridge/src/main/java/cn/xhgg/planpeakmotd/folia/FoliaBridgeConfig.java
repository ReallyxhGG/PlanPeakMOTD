package cn.xhgg.planpeakmotd.folia;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

record FoliaBridgeConfig(
        URI velocityUrl,
        String serverId,
        String sharedToken,
        ZoneId zoneId,
        int reportIntervalSeconds,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        Map<String, String> papiPlaceholders
) {
    private static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    static final String FILE_NAME = "config.properties";

    static FoliaBridgeConfig load(Path dataDirectory, int serverPort) throws IOException {
        Files.createDirectories(dataDirectory);
        Path path = dataDirectory.resolve(FILE_NAME);
        if (Files.notExists(path)) {
            Files.writeString(
                    path,
                    defaultConfig(serverPort),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        URI velocityUrl;
        try {
            velocityUrl = URI.create(properties.getProperty(
                    "velocity-url",
                    "http://127.0.0.1:18765/report"
            ).strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("velocity-url 不是有效地址", exception);
        }
        validateLocalHttpUrl(velocityUrl);

        String serverId = properties.getProperty("server-id", "server-" + serverPort).strip();
        if (!SERVER_ID.matcher(serverId).matches()) {
            throw new IllegalArgumentException("server-id 只能包含字母、数字、点、下划线和短横线，最长 64 字符");
        }

        String token = properties.getProperty("shared-token", "").strip();
        if (token.length() < 16) {
            throw new IllegalArgumentException("shared-token 至少需要 16 个字符，请复制 Velocity 端配置中的值");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(properties.getProperty("timezone", "Asia/Shanghai").strip());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone 不是有效时区", exception);
        }

        Map<String, String> papiPlaceholders = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("papi.")) {
                continue;
            }
            String alias = key.substring("papi.".length());
            if (!SERVER_ID.matcher(alias).matches()) {
                throw new IllegalArgumentException("PAPI 别名无效：" + alias);
            }
            if (papiPlaceholders.size() >= 32) {
                throw new IllegalArgumentException("PAPI 映射最多支持 32 个");
            }
            papiPlaceholders.put(alias, properties.getProperty(key));
        }

        return new FoliaBridgeConfig(
                velocityUrl,
                serverId,
                token,
                zoneId,
                parseInt(properties, "report-interval-seconds", 60, 10, 3600),
                parseInt(properties, "connect-timeout-seconds", 3, 1, 30),
                parseInt(properties, "request-timeout-seconds", 5, 1, 60),
                Map.copyOf(papiPlaceholders)
        );
    }

    private static void validateLocalHttpUrl(URI uri) throws UnknownHostException {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("velocity-url 必须使用 http://");
        }
        if (uri.getHost() == null || !InetAddress.getByName(uri.getHost()).isLoopbackAddress()) {
            throw new IllegalArgumentException("velocity-url 必须指向本机回环地址（推荐 127.0.0.1）");
        }
        if (!"/report".equals(uri.getPath())) {
            throw new IllegalArgumentException("velocity-url 路径必须是 /report");
        }
    }

    private static int parseInt(Properties properties, String key, int defaultValue, int min, int max) {
        String raw = properties.getProperty(key, Integer.toString(defaultValue)).strip();
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(key + " 必须在 " + min + " 到 " + max + " 之间");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " 必须是整数", exception);
        }
    }

    private static String defaultConfig(int serverPort) {
        return """
                # PlanPeakBridge Folia 配置
                # server-id 在所有子服中必须唯一。
                server-id=server-%d

                # 复制 Velocity 端 plugins/planpeakmotd/config.properties 中的地址与密钥。
                velocity-url=http://127.0.0.1:18765/report
                shared-token=COPY_TOKEN_FROM_VELOCITY

                # 两端时区必须一致。
                timezone=Asia/Shanghai
                report-interval-seconds=60
                connect-timeout-seconds=3
                request-timeout-seconds=5

                # 可选：把子服 PAPI 变量映射成别名，再在 Velocity 悬浮文字使用。
                # 这里只适合不需要玩家上下文、且兼容 Folia 异步调用的服务器型变量。
                # papi.tps=%%server_tps_1%%
                # papi.plan_online=%%plan_server_players_online%%
                """.formatted(serverPort);
    }
}

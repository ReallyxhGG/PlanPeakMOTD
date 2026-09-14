package cn.xhgg.planpeakmotd.velocity;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

record VelocityConfig(
        InetAddress bindAddress,
        int port,
        String sharedToken,
        ZoneId zoneId,
        AggregateMode aggregateMode,
        boolean modifyMaxPlayers,
        List<String> hoverLines
) {
    static final String FILE_NAME = "config.properties";

    static VelocityConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path path = dataDirectory.resolve(FILE_NAME);
        if (Files.notExists(path)) {
            Files.writeString(
                    path,
                    defaultConfig(randomToken()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        InetAddress bindAddress = parseLoopback(properties.getProperty("bind-address", "127.0.0.1"));
        int port = parseInt(properties, "port", 18765, 1, 65535);
        String token = properties.getProperty("shared-token", "").strip();
        if (token.length() < 16) {
            throw new IllegalArgumentException("shared-token 至少需要 16 个字符");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(properties.getProperty("timezone", "Asia/Shanghai").strip());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone 不是有效时区", exception);
        }

        AggregateMode aggregateMode = AggregateMode.parse(properties.getProperty("aggregate-mode", "sum"));
        boolean modifyMaxPlayers = Boolean.parseBoolean(
                properties.getProperty("modify-max-players", "true").strip()
        );
        List<String> lines = new ArrayList<>();
        for (int lineNumber = 1; lineNumber <= 20; lineNumber++) {
            String line = properties.getProperty("hover-line-" + lineNumber);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("至少需要设置 hover-line-1");
        }

        return new VelocityConfig(
                bindAddress,
                port,
                token,
                zoneId,
                aggregateMode,
                modifyMaxPlayers,
                List.copyOf(lines)
        );
    }

    private static InetAddress parseLoopback(String value) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(value.strip());
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException("bind-address 必须是本机回环地址（推荐 127.0.0.1）");
        }
        return address;
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

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String defaultConfig(String token) {
        return """
                # PlanPeakMOTD Velocity 配置
                # 子服桥接插件的 velocity-url 端口和 shared-token 必须与这里一致。
                bind-address=127.0.0.1
                port=18765
                shared-token=%s

                # 决定“今天”的时区。两端必须一致。
                timezone=Asia/Shanghai

                # 多个子服的峰值汇总方式：sum（相加）或 max（取最大值）。
                aggregate-mode=sum

                # true：右侧最大人数显示今日峰值；false：保留 MiniMOTD 的右侧人数。
                modify-max-players=true

                # 最多可添加到 hover-line-20。支持 & 颜色代码以及下列变量：
                # {online}、{today_peak}、{today_peak:子服ID}、{papi:子服ID:别名}
                hover-line-1=服务器总在线人数: {online}
                hover-line-2=服务器今日巅峰: {today_peak}
                """.formatted(token);
    }
}

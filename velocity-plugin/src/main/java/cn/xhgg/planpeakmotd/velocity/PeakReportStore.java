package cn.xhgg.planpeakmotd.velocity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

final class PeakReportStore {
    private static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final Path cacheFile;
    private LocalDate day;
    private final Map<String, Integer> serverPeaks = new HashMap<>();
    private final Map<String, Map<String, String>> papiValues = new HashMap<>();
    private volatile PeakSnapshot cachedSnapshot;
    private boolean cacheDirty;

    PeakReportStore(Path cacheFile) {
        this.cacheFile = cacheFile;
    }

    static boolean isValidServerId(String serverId) {
        return SERVER_ID.matcher(serverId).matches();
    }

    synchronized void load(LocalDate today) throws IOException {
        serverPeaks.clear();
        papiValues.clear();
        day = today;
        if (Files.notExists(cacheFile)) {
            rebuildSnapshot();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        LocalDate cachedDay;
        try {
            cachedDay = LocalDate.parse(properties.getProperty("day", ""));
        } catch (RuntimeException exception) {
            rebuildSnapshot();
            return;
        }
        if (!cachedDay.equals(today)) {
            rebuildSnapshot();
            return;
        }

        day = cachedDay;
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("peak.")) {
                continue;
            }
            String serverId = key.substring("peak.".length());
            if (!isValidServerId(serverId)) {
                continue;
            }
            try {
                int peak = Integer.parseInt(properties.getProperty(key));
                if (peak >= 0) {
                    serverPeaks.put(serverId, peak);
                }
            } catch (NumberFormatException ignored) {
                // Ignore a broken cache entry instead of disabling the plugin.
            }
        }
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("papi64.")) {
                continue;
            }
            String[] parts = key.substring("papi64.".length()).split("\\.", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                String serverId = decode(parts[0]);
                String alias = decode(parts[1]);
                if (isValidServerId(serverId) && isValidServerId(alias)) {
                    papiValues.computeIfAbsent(serverId, ignored -> new HashMap<>())
                            .put(alias, properties.getProperty(key, ""));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed base64 cache entries.
            }
        }
        rebuildSnapshot();
    }

    synchronized ReportResult report(
            LocalDate today,
            LocalDate reportDay,
            String serverId,
            int peak,
            Map<String, String> reportedPapiValues
    )
            throws IOException {
        rollDay(today);
        if (!today.equals(reportDay)) {
            return ReportResult.WRONG_DAY;
        }
        if (!isValidServerId(serverId) || peak < 0) {
            return ReportResult.INVALID;
        }

        boolean firstReport = !serverPeaks.containsKey(serverId);
        int previous = serverPeaks.getOrDefault(serverId, 0);
        serverPeaks.put(serverId, Math.max(previous, peak));
        Map<String, String> sanitized = new HashMap<>();
        reportedPapiValues.forEach((alias, value) -> {
            if (isValidServerId(alias) && value != null) {
                sanitized.put(alias, sanitizeValue(value));
            }
        });
        Map<String, String> previousPapi = papiValues.put(serverId, Map.copyOf(sanitized));
        boolean changed = firstReport || peak > previous || !sanitized.equals(previousPapi);
        if (changed) {
            rebuildSnapshot();
            cacheDirty = true;
        }
        if (cacheDirty) {
            persist();
            cacheDirty = false;
        }
        return firstReport ? ReportResult.FIRST_REPORT : ReportResult.UPDATED;
    }

    PeakSnapshot snapshot(LocalDate today) {
        PeakSnapshot snapshot = cachedSnapshot;
        if (snapshot != null && today.equals(snapshot.day())) {
            return snapshot;
        }
        synchronized (this) {
            rollDay(today);
            return cachedSnapshot;
        }
    }

    private void rollDay(LocalDate today) {
        if (!today.equals(day)) {
            day = today;
            serverPeaks.clear();
            papiValues.clear();
            rebuildSnapshot();
        }
    }

    private void rebuildSnapshot() {
        Map<String, Map<String, String>> copiedPapiValues = new HashMap<>();
        papiValues.forEach((server, values) -> copiedPapiValues.put(server, Map.copyOf(values)));
        cachedSnapshot = new PeakSnapshot(day, Map.copyOf(serverPeaks), Map.copyOf(copiedPapiValues));
    }

    private void persist() throws IOException {
        Files.createDirectories(cacheFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("day", day.toString());
        serverPeaks.forEach((server, peak) -> properties.setProperty("peak." + server, Integer.toString(peak)));
        papiValues.forEach((server, values) -> values.forEach((alias, value) -> properties.setProperty(
                "papi64." + encode(server) + "." + encode(alias),
                value
        )));

        Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "PlanPeakMOTD cache - do not edit while Velocity is running");
        }
        try {
            Files.move(
                    temporary,
                    cacheFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sanitizeValue(String value) {
        String singleLine = value.replace('\r', ' ').replace('\n', ' ');
        return singleLine.length() <= 512 ? singleLine : singleLine.substring(0, 512);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    enum ReportResult {
        FIRST_REPORT,
        UPDATED,
        WRONG_DAY,
        INVALID
    }
}

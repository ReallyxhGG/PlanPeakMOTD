package cn.xhgg.planpeakmotd.folia;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

public final class PlanPeakBridgePlugin extends JavaPlugin {
    private final AtomicReference<FoliaBridgeConfig> config = new AtomicReference<>();
    private final AtomicInteger lastPeak = new AtomicInteger();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>("尚未回报");
    private final AtomicReference<Map<String, String>> lastPapiValues = new AtomicReference<>(Map.of());
    private final PlanDailyPeakReader peakReader = new PlanDailyPeakReader();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledReport;
    private PeakHttpReporter reporter;
    private PapiResolver papiResolver;

    @Override
    public void onEnable() {
        try {
            FoliaBridgeConfig loaded = loadConfig();
            config.set(loaded);
            reporter = new PeakHttpReporter(loaded.connectTimeoutSeconds());
            papiResolver = PapiResolver.detect();
        } catch (Exception exception) {
            getLogger().severe("配置加载失败：" + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "plan-peak-bridge");
            thread.setDaemon(true);
            return thread;
        });
        registerCommand();
        scheduleReports(1);
        warnIfPapiUnavailable(config.get());
        getLogger().info("PlanPeakBridge 已启动；所有 Plan 查询与 HTTP 回报均在独立异步线程执行，兼容 Folia。");
    }

    @Override
    public void onDisable() {
        if (scheduledReport != null) {
            scheduledReport.cancel(false);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    void reloadBridge() throws IOException {
        FoliaBridgeConfig loaded = loadConfig();
        config.set(loaded);
        reporter = new PeakHttpReporter(loaded.connectTimeoutSeconds());
        papiResolver = PapiResolver.detect();
        scheduleReports(0);
        warnIfPapiUnavailable(loaded);
    }

    void reportNow() {
        if (executor != null) {
            executor.execute(this::reportSafely);
        }
    }

    String statusMessage() {
        FoliaBridgeConfig current = config.get();
        String success = lastSuccess.get() == null
                ? "从未"
                : DateTimeFormatter.ISO_INSTANT.format(lastSuccess.get());
        return "§bPlanPeakBridge: server-id=" + current.serverId()
                + ", 上次峰值=" + lastPeak.get()
                + ", 上次成功=" + success
                + ", PAPI=" + lastPapiValues.get()
                + ", 状态=" + lastError.get();
    }

    private void scheduleReports(long initialDelaySeconds) {
        if (scheduledReport != null) {
            scheduledReport.cancel(false);
        }
        FoliaBridgeConfig current = config.get();
        scheduledReport = executor.scheduleWithFixedDelay(
                this::reportSafely,
                initialDelaySeconds,
                current.reportIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void reportSafely() {
        FoliaBridgeConfig current = config.get();
        LocalDate today = LocalDate.now(current.zoneId());
        try {
            int peak = peakReader.read(today, current.zoneId());
            PapiResolver.Resolution papiResolution = papiResolver.resolve(current.papiPlaceholders());
            Map<String, String> papiValues = papiResolution.values();
            reporter.report(current, today, peak, papiValues);
            lastPeak.set(peak);
            lastPapiValues.set(papiValues);
            lastSuccess.set(Instant.now());
            lastError.set(papiResolution.failedAliases().isEmpty()
                    ? "正常"
                    : "峰值回报正常，PAPI 解析失败=" + papiResolution.failedAliases());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastError.set("任务被中断");
        } catch (Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            String previous = lastError.getAndSet(message);
            if (!message.equals(previous)) {
                getLogger().warning("读取或回报今日 Plan 峰值失败：" + message);
            }
        }
    }

    private FoliaBridgeConfig loadConfig() throws IOException {
        Path dataDirectory = getDataFolder().toPath();
        return FoliaBridgeConfig.load(dataDirectory, Bukkit.getPort());
    }

    private void registerCommand() {
        PluginCommand command = Objects.requireNonNull(getCommand("planpeakbridge"));
        PlanPeakBridgeCommand handler = new PlanPeakBridgeCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void warnIfPapiUnavailable(FoliaBridgeConfig current) {
        if (!current.papiPlaceholders().isEmpty() && !papiResolver.available()) {
            getLogger().warning("配置了 PAPI 映射，但未检测到 PlaceholderAPI；峰值仍会正常回报，PAPI 值将为空。");
        }
    }
}

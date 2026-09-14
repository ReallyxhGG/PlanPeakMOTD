package cn.xhgg.planpeakmotd.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Plugin(
        id = "planpeakmotd",
        name = "PlanPeakMOTD",
        version = "1.0.0",
        description = "MiniMOTD player hover and Plan peak bridge for Velocity",
        authors = {"xhGG"},
        dependencies = {
                @Dependency(id = "minimotd-velocity", optional = true)
        }
)
public final class PlanPeakMotdPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final AtomicReference<VelocityConfig> config = new AtomicReference<>();

    private PeakReportStore reportStore;
    private PeakReportServer reportServer;
    private volatile HoverCache hoverCache;

    @Inject
    public PlanPeakMotdPlugin(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            VelocityConfig loaded = VelocityConfig.load(dataDirectory);
            config.set(loaded);
            reportStore = new PeakReportStore(dataDirectory.resolve("peak-cache.properties"));
            reportStore.load(LocalDate.now(loaded.zoneId()));
            startReportServer();
            registerCommand();
            logger.info(
                    "PlanPeakMOTD 已启动，峰值回报地址为 http://{}:{}/report",
                    loaded.bindAddress().getHostAddress(),
                    loaded.port()
            );
        } catch (Exception exception) {
            logger.error("PlanPeakMOTD 启动失败，请检查 {}", dataDirectory.resolve(VelocityConfig.FILE_NAME), exception);
        }
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onProxyPing(ProxyPingEvent event) {
        VelocityConfig currentConfig = config.get();
        if (currentConfig == null || reportStore == null) {
            return;
        }

        LocalDate today = LocalDate.now(currentConfig.zoneId());
        PeakSnapshot snapshot = reportStore.snapshot(today);
        int online = proxy.getPlayerCount();
        HoverCache currentHover = resolveHover(currentConfig, snapshot, online);

        ServerPing.Builder builder = pingBuilderWithPlayers(event.getPing())
                .onlinePlayers(online)
                .clearSamplePlayers();
        if (currentConfig.modifyMaxPlayers()) {
            builder.maximumPlayers(currentHover.displayedPeak());
        }
        builder.samplePlayers(currentHover.samples());
        event.setPing(builder.build());
    }

    private HoverCache resolveHover(VelocityConfig currentConfig, PeakSnapshot snapshot, int online) {
        HoverCache cached = hoverCache;
        if (cached != null
                && cached.config() == currentConfig
                && cached.snapshot() == snapshot
                && cached.online() == online) {
            return cached;
        }

        int displayedPeak = Math.max(online, snapshot.aggregate(currentConfig.aggregateMode()));
        List<ServerPing.SamplePlayer> samples = new ArrayList<>();
        int index = 0;
        for (String template : currentConfig.hoverLines()) {
            if (template.isEmpty()) {
                continue;
            }
            String rendered = VariableRenderer.render(template, online, displayedPeak, snapshot);
            UUID id = new UUID(0x506c616e5065616bL, index++);
            samples.add(new ServerPing.SamplePlayer(rendered, id));
        }
        HoverCache created = new HoverCache(
                currentConfig,
                snapshot,
                online,
                displayedPeak,
                samples.toArray(ServerPing.SamplePlayer[]::new)
        );
        hoverCache = created;
        return created;
    }

    private static ServerPing.Builder pingBuilderWithPlayers(ServerPing ping) {
        if (ping.getPlayers().isPresent()) {
            return ping.asBuilder();
        }

        ServerPing.Builder builder = ServerPing.builder()
                .version(ping.getVersion())
                .description(ping.getDescriptionComponent());
        ping.getFavicon().ifPresent(builder::favicon);
        ping.getModinfo().ifPresentOrElse(builder::mods, builder::notModCompatible);
        return builder;
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        stopReportServer();
    }

    void reload() throws IOException {
        VelocityConfig previous = config.get();
        VelocityConfig loaded = VelocityConfig.load(dataDirectory);
        boolean endpointChanged = previous == null
                || !previous.bindAddress().equals(loaded.bindAddress())
                || previous.port() != loaded.port();

        config.set(loaded);
        hoverCache = null;
        if (endpointChanged) {
            stopReportServer();
            try {
                startReportServer();
            } catch (IOException exception) {
                config.set(previous);
                try {
                    startReportServer();
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    VelocityConfig config() {
        return config.get();
    }

    PeakSnapshot snapshot(LocalDate today) {
        return reportStore.snapshot(today);
    }

    int onlinePlayers() {
        return proxy.getPlayerCount();
    }

    private void startReportServer() throws IOException {
        reportServer = new PeakReportServer(logger, reportStore, config::get);
        reportServer.start();
    }

    private void stopReportServer() {
        if (reportServer != null) {
            reportServer.close();
            reportServer = null;
        }
    }

    private void registerCommand() {
        CommandManager commands = proxy.getCommandManager();
        commands.register(
                commands.metaBuilder("peakmotd").plugin(this).build(),
                new PlanPeakCommand(this)
        );
    }

    private record HoverCache(
            VelocityConfig config,
            PeakSnapshot snapshot,
            int online,
            int displayedPeak,
            ServerPing.SamplePlayer[] samples
    ) {
    }
}

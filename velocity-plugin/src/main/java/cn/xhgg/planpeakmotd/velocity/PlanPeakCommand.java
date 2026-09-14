package cn.xhgg.planpeakmotd.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.LocalDate;
import java.util.List;

final class PlanPeakCommand implements SimpleCommand {
    private final PlanPeakMotdPlugin plugin;

    PlanPeakCommand(PlanPeakMotdPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String action = invocation.arguments().length == 0
                ? "status"
                : invocation.arguments()[0].toLowerCase();
        switch (action) {
            case "reload" -> {
                try {
                    plugin.reload();
                    invocation.source().sendMessage(Component.text("PlanPeakMOTD 配置已重载。", NamedTextColor.GREEN));
                } catch (Exception exception) {
                    invocation.source().sendMessage(Component.text(
                            "重载失败：" + exception.getMessage(),
                            NamedTextColor.RED
                    ));
                }
            }
            case "status" -> sendStatus(invocation);
            default -> invocation.source().sendMessage(Component.text(
                    "用法：/peakmotd <status|reload>",
                    NamedTextColor.YELLOW
            ));
        }
    }

    private void sendStatus(Invocation invocation) {
        VelocityConfig config = plugin.config();
        PeakSnapshot snapshot = plugin.snapshot(LocalDate.now(config.zoneId()));
        int peak = snapshot.aggregate(config.aggregateMode());
        invocation.source().sendMessage(Component.text(
                "今日峰值=" + peak + "，在线=" + plugin.onlinePlayers()
                        + "，子服=" + snapshot.serverPeaks(),
                NamedTextColor.AQUA
        ));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of("status", "reload");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("planpeakmotd.admin");
    }
}


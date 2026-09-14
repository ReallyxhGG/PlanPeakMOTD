package cn.xhgg.planpeakmotd.folia;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class PlanPeakBridgeCommand implements CommandExecutor, TabCompleter {
    private final PlanPeakBridgePlugin plugin;

    PlanPeakBridgeCommand(PlanPeakBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (action) {
            case "status" -> sender.sendMessage(plugin.statusMessage());
            case "send" -> {
                plugin.reportNow();
                sender.sendMessage("§a已安排立即读取 Plan 并回报。结果请查看控制台或再次执行 status。");
            }
            case "reload" -> {
                try {
                    plugin.reloadBridge();
                    sender.sendMessage("§aPlanPeakBridge 配置已重载。");
                } catch (Exception exception) {
                    sender.sendMessage("§c重载失败：" + exception.getMessage());
                }
            }
            default -> sender.sendMessage("§e用法：/planpeakbridge <status|reload|send>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        return args.length == 1 ? List.of("status", "reload", "send") : List.of();
    }
}


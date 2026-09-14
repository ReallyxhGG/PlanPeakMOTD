package cn.xhgg.planpeakmotd.folia;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PapiResolver {
    private final Method setPlaceholders;

    private PapiResolver(Method setPlaceholders) {
        this.setPlaceholders = setPlaceholders;
    }

    static PapiResolver detect() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return new PapiResolver(null);
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            return new PapiResolver(method);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return new PapiResolver(null);
        }
    }

    boolean available() {
        return setPlaceholders != null;
    }

    Resolution resolve(Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return new Resolution(Map.of(), List.of());
        }
        if (setPlaceholders == null) {
            return new Resolution(Map.of(), List.copyOf(placeholders.keySet()));
        }

        Map<String, String> values = new HashMap<>();
        List<String> failures = new ArrayList<>();
        placeholders.forEach((alias, placeholder) -> {
            try {
                values.put(alias, resolveOne(placeholder));
            } catch (RuntimeException exception) {
                failures.add(alias);
            }
        });
        return new Resolution(Map.copyOf(values), List.copyOf(failures));
    }

    private String resolveOne(String placeholder) {
        try {
            Object result = setPlaceholders.invoke(null, null, placeholder);
            return result == null ? "" : result.toString();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("无法调用 PlaceholderAPI", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("PAPI 变量解析失败：" + cause.getMessage(), cause);
        }
    }

    record Resolution(Map<String, String> values, List<String> failedAliases) {
    }
}

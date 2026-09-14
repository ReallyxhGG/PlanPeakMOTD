package cn.xhgg.planpeakmotd.velocity;

import java.time.LocalDate;
import java.util.Map;

record PeakSnapshot(
        LocalDate day,
        Map<String, Integer> serverPeaks,
        Map<String, Map<String, String>> papiValues
) {
    int aggregate(AggregateMode mode) {
        long aggregate = 0;
        for (int peak : serverPeaks.values()) {
            if (mode == AggregateMode.MAX) {
                aggregate = Math.max(aggregate, peak);
            } else {
                aggregate += peak;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, aggregate);
    }

    int peakOf(String serverId) {
        return serverPeaks.getOrDefault(serverId, 0);
    }

    String papiValue(String serverId, String alias) {
        return papiValues.getOrDefault(serverId, Map.of()).getOrDefault(alias, "");
    }
}

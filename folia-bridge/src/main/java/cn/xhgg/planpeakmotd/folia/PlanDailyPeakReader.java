package cn.xhgg.planpeakmotd.folia;

import com.djrapitops.plan.query.QueryService;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

final class PlanDailyPeakReader {
    private static final String SQL = """
            SELECT COALESCE(MAX(t.players_online), 0) AS peak
            FROM plan_tps t
            INNER JOIN plan_servers s ON s.id = t.server_id
            WHERE s.uuid = ? AND t.date >= ? AND t.date < ?
            """;

    int read(LocalDate day, ZoneId zoneId) {
        QueryService queryService = QueryService.getInstance();
        UUID serverUuid = queryService.getServerUUID()
                .orElseThrow(() -> new IllegalStateException("Plan 尚未取得当前子服 UUID"));
        long start = day.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();

        return queryService.query(SQL, statement -> {
            statement.setString(1, serverUuid.toString());
            statement.setLong(2, start);
            statement.setLong(3, end);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Math.max(0, results.getInt("peak")) : 0;
            }
        });
    }
}


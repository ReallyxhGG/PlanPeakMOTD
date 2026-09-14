package cn.xhgg.planpeakmotd.velocity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableRendererTest {
    @Test
    void rendersAggregateAndPerServerVariables() {
        PeakSnapshot snapshot = new PeakSnapshot(
                LocalDate.of(2026, 9, 1),
                Map.of("survival", 23),
                Map.of("survival", Map.of("tps", "19.98"))
        );

        assertEquals(
                "在线 8 / 今日 23 / 生存 23 / TPS 19.98 / 未知 0",
                VariableRenderer.render(
                        "在线 {online} / 今日 {today_peak} / 生存 {today_peak:survival} / TPS {papi:survival:tps} / 未知 {today_peak:lobby}",
                        8,
                        23,
                        snapshot
                )
        );
    }

    @Test
    void translatesLegacyAndHexColors() {
        assertEquals("§a绿色 §x§1§2§a§b§e§f渐变", VariableRenderer.translateColors("&a绿色 &#12abef渐变"));
    }
}

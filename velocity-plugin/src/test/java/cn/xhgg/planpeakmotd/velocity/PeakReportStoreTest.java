package cn.xhgg.planpeakmotd.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeakReportStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void keepsHighestReportAndPersistsIt() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 1);
        Path cache = tempDirectory.resolve("peak-cache.properties");
        PeakReportStore store = new PeakReportStore(cache);
        store.load(today);

        store.report(today, today, "survival", 12, Map.of("tps", "19.98"));
        store.report(today, today, "survival", 8, Map.of("tps", "20.0"));
        store.report(today, today, "lobby", 3, Map.of());

        assertEquals(15, store.snapshot(today).aggregate(AggregateMode.SUM));
        assertEquals(12, store.snapshot(today).aggregate(AggregateMode.MAX));

        PeakReportStore reloaded = new PeakReportStore(cache);
        reloaded.load(today);
        assertEquals(12, reloaded.snapshot(today).peakOf("survival"));
        assertEquals("20.0", reloaded.snapshot(today).papiValue("survival", "tps"));
    }

    @Test
    void rollsOverAtNewDay() throws Exception {
        LocalDate firstDay = LocalDate.of(2026, 9, 1);
        LocalDate nextDay = firstDay.plusDays(1);
        PeakReportStore store = new PeakReportStore(tempDirectory.resolve("peak-cache.properties"));
        store.load(firstDay);
        store.report(firstDay, firstDay, "survival", 12, Map.of());

        assertEquals(0, store.snapshot(nextDay).aggregate(AggregateMode.SUM));
        assertEquals(PeakReportStore.ReportResult.WRONG_DAY,
                store.report(nextDay, firstDay, "survival", 99, Map.of()));
    }
}

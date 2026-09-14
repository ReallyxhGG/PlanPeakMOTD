package cn.xhgg.planpeakmotd.velocity;

import java.util.Locale;

enum AggregateMode {
    SUM,
    MAX;

    static AggregateMode parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("aggregate-mode 只能是 sum 或 max", exception);
        }
    }
}


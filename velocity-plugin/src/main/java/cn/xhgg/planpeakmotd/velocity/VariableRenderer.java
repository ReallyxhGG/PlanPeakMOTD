package cn.xhgg.planpeakmotd.velocity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VariableRenderer {
    private static final Pattern SERVER_PEAK = Pattern.compile("\\{today_peak:([A-Za-z0-9_.-]{1,64})}");
    private static final Pattern PAPI = Pattern.compile(
            "\\{papi:([A-Za-z0-9_.-]{1,64}):([A-Za-z0-9_.-]{1,64})}"
    );
    private static final Pattern LEGACY_COLOR = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");

    private VariableRenderer() {
    }

    static String render(String template, int online, int aggregatePeak, PeakSnapshot snapshot) {
        Matcher matcher = SERVER_PEAK.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Integer.toString(snapshot.peakOf(matcher.group(1))));
        }
        matcher.appendTail(result);

        Matcher papiMatcher = PAPI.matcher(result.toString());
        StringBuilder papiResult = new StringBuilder();
        while (papiMatcher.find()) {
            String value = snapshot.papiValue(papiMatcher.group(1), papiMatcher.group(2));
            papiMatcher.appendReplacement(papiResult, Matcher.quoteReplacement(value));
        }
        papiMatcher.appendTail(papiResult);

        String replaced = papiResult.toString()
                .replace("{online}", Integer.toString(online))
                .replace("{today_peak}", Integer.toString(aggregatePeak));
        return translateColors(replaced);
    }

    static String translateColors(String input) {
        Matcher hexMatcher = HEX_COLOR.matcher(input);
        StringBuilder hexResult = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1).toLowerCase(Locale.ROOT);
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : hex.toCharArray()) {
                replacement.append('§').append(character);
            }
            hexMatcher.appendReplacement(hexResult, Matcher.quoteReplacement(replacement.toString()));
        }
        hexMatcher.appendTail(hexResult);

        Matcher legacyMatcher = LEGACY_COLOR.matcher(hexResult.toString());
        return legacyMatcher.replaceAll(match -> "§" + match.group(1).toLowerCase(Locale.ROOT));
    }
}

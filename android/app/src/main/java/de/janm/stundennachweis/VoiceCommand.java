package de.janm.stundennachweis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VoiceCommand {
    record Parsed(boolean clearDay, List<String> deleteSlots, Map<String,String> entries, String unassigned) {}

    private static final Pattern SLOT = Pattern.compile("(?iu)(frühaufsicht|frühstück|mittagessen|spätaufsicht|(?:erste|1\\.?)\\s*stunde|(?:zweite|2\\.?)\\s*stunde|(?:dritte|3\\.?)\\s*stunde|(?:vierte|4\\.?)\\s*stunde|(?:fünfte|5\\.?)\\s*stunde|(?:sechste|6\\.?)\\s*stunde|(?:siebte|7\\.?)\\s*stunde|(?:erste|zweite)?\\s*pausenaufsicht)");

    static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        String lower = text.toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
        boolean clear = lower.matches(".*\\b(lösche|entferne|leere)\\s+(bitte\\s+)?(alles|alle einträge|den ganzen tag)\\b.*")
                || lower.matches(".*\\b(alles|alle einträge|den ganzen tag)\\s+(löschen|entfernen|leeren)\\b.*");
        if (clear) return new Parsed(true, List.of(), Map.of(), "");

        List<String> deletes = new ArrayList<>();
        if (lower.matches(".*\\b(lösche|löschen|entferne|entfernen|leere|leeren)\\b.*")) {
            Matcher matcher = SLOT.matcher(lower);
            while (matcher.find()) deletes.add(key(matcher.group()));
            if (lower.matches(".*erste\\s+und\\s+zweite\\s+stunde.*")) { deletes.add("1"); deletes.add("2"); }
            return new Parsed(false, deletes.stream().distinct().toList(), Map.of(), "");
        }

        Map<String,String> entries = new LinkedHashMap<>();
        Matcher matcher = SLOT.matcher(text);
        List<int[]> positions = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        while (matcher.find()) { positions.add(new int[]{matcher.start(), matcher.end()}); keys.add(key(matcher.group())); }
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i)[1];
            int end = i + 1 < positions.size() ? positions.get(i + 1)[0] : text.length();
            String value = text.substring(start, end).replaceFirst("^[\\s:,-]+", "").trim();
            if (!value.isBlank()) entries.put(keys.get(i), value);
        }
        if (positions.size() == 1 && lower.matches(".*erste\\s+und\\s+zweite\\s+stunde.*")) {
            String value = entries.values().stream().findFirst().orElse("");
            entries.put("1", value); entries.put("2", value);
        }
        return new Parsed(false, List.of(), entries, entries.isEmpty() ? text : "");
    }

    private static String key(String label) {
        String s = label.toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
        if (s.contains("frühaufsicht")) return "early";
        if (s.contains("frühstück")) return "breakfast";
        if (s.contains("mittagessen")) return "lunch";
        if (s.contains("spätaufsicht")) return "late";
        if (s.contains("pausenaufsicht")) return s.contains("zweite") ? "pause2" : "pause1";
        if (s.contains("erste") || s.startsWith("1")) return "1";
        if (s.contains("zweite") || s.startsWith("2")) return "2";
        if (s.contains("dritte") || s.startsWith("3")) return "3";
        if (s.contains("vierte") || s.startsWith("4")) return "4";
        if (s.contains("fünfte") || s.startsWith("5")) return "5";
        if (s.contains("sechste") || s.startsWith("6")) return "6";
        if (s.contains("siebte") || s.startsWith("7")) return "7";
        return "";
    }

    private VoiceCommand() {}
}

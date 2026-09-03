package de.janm.stundennachweis;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GermanEntryFormatter {
    private static final Map<String,String> FIXED = new LinkedHashMap<>();
    static {
        FIXED.put("frei\\s+arbeit", "Freiarbeit");
        FIXED.put("pausen\\s+aufsicht", "Pausenaufsicht");
        FIXED.put("früh(?:e)?\\s+aufsicht", "Frühaufsicht");
        FIXED.put("spät\\s+aufsicht", "Spätaufsicht");
        FIXED.put("mittag\\s+essen", "Mittagessen");
        FIXED.put("psycho\\s+motorik", "Psychomotorik");
        FIXED.put("fuß\\s+ball", "Fußball");
        FIXED.put("meta\\s+talk", "MetaTalk");
        FIXED.put("go\\s+talk", "GoTalk");
        FIXED.put("\\baldi\\b", "ALDI");
        FIXED.put("\\bedeka\\b", "EDEKA");
        FIXED.put("\\brewe\\b", "REWE");
    }

    static String localFormat(String raw) {
        if (raw == null) return "";
        String value = raw.trim().replaceAll("\\s+", " ");
        for (Map.Entry<String,String> e : FIXED.entrySet()) value = value.replaceAll("(?iu)" + e.getKey(), e.getValue());
        value = value.replaceAll("(?iu)\\b(?:ähm+|äh|also|okay|ok|halt|quasi)\\b", " ").replaceAll("\\s+", " ").trim();

        String lower = value.toLowerCase(Locale.GERMAN);
        String subject = "";
        if (lower.matches(".*\\b(mathe|mathematik|rechnen|plusaufgaben|minusaufgaben|zahlenraum)\\b.*")) subject = "Mathematik";
        else if (lower.matches(".*\\b(deutsch|lesen|schreiben|rechtschreibung|grammatik|text)\\b.*")) subject = "Deutsch";
        else if (lower.matches(".*\\b(sport|turnhalle|fußball|parcours|schwimmen)\\b.*")) subject = "Sport";
        else if (lower.matches(".*\\b(hauswirtschaft|kochen|einkauf|ALDI)\\b.*")) subject = "Hauswirtschaft";
        else if (lower.matches(".*\\b(psychomotorik|motorik|wahrnehmung|gleichgewicht)\\b.*")) subject = "Psychomotorik";
        else if (lower.matches(".*\\b(erdkunde|geografie|bundesländer|kontinente)\\b.*")) subject = "Erdkunde";
        else if (lower.matches(".*\\b(englisch|english|vokabeln)\\b.*")) subject = "Englisch";
        else if (lower.matches(".*\\b(kunst|malen|zeichnen|basteln)\\b.*")) subject = "Kunst";

        value = value.replaceAll("(?iu)\\b(?:ich|wir)\\s+(?:habe|haben|hatte|hatten)\\b", " ")
                .replaceAll("(?iu)\\bmit (?:den )?(?:kindern|schülern|der klasse)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        value = value.replaceAll("(?iu)\\bplus(?:aufgaben)? und minus(?:aufgaben)?(?: aufgaben)? bis (\\d+) (?:gemacht|bearbeitet|gerechnet|geübt)", "Übung von Additions- und Subtraktionsaufgaben im Zahlenraum bis $1");
        value = value.replaceAll("(?iu)\\beinen? text gelesen", "Lesen und Erschließen eines Textes");
        value = value.replaceAll("(?iu)\\bfragen dazu beantwortet", "Beantwortung der zugehörigen Verständnisfragen");
        value = value.replaceAll("(?iu)\\bbei ALDI (?:ein)?gekauft", "Durchführung eines Einkaufs bei ALDI");
        value = value.replaceAll("(?iu)\\bfußball gespielt", "Fußballspiel");
        value = value.replaceAll("(?iu)\\b(?:einen )?(?:bewegungs)?parcours (?:gemacht|durchgeführt)", "Durchführung eines Bewegungsparcours");
        value = value.replaceAll("(?iu)\\bgleichgewicht geübt", "Übung und Festigung des Gleichgewichts");

        if (!subject.isEmpty()) value = value.replaceFirst("(?iu)^" + Pattern.quote(subject) + "\\s*[:,]?\\s*", "");
        value = capitaliseKnown(value);
        if (value.isBlank()) value = "Fachunterricht";
        value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
        if (!subject.isEmpty() && !value.regionMatches(true, 0, subject + ":", 0, subject.length() + 1)) value = subject + ": " + value;
        return value.replaceAll("[.!?]+$", "") + ".";
    }

    static String finaliseAi(String value) {
        if (value == null) return "";
        value = value.trim();
        for (Map.Entry<String,String> e : FIXED.entrySet()) value = value.replaceAll("(?iu)" + e.getKey(), e.getValue());
        value = capitaliseKnown(value);
        if (value.isBlank()) return "Unterrichtliche Tätigkeit.";
        value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
        return value.replaceAll("[.!?]+$", "") + ".";
    }

    private static String capitaliseKnown(String value) {
        String[][] words = {
                {"bearbeitung","Bearbeitung"},{"arbeitsauftrag","Arbeitsauftrag"},{"arbeitsauftrags","Arbeitsauftrags"},
                {"partnerarbeit","Partnerarbeit"},{"gruppenarbeit","Gruppenarbeit"},{"besprechung","Besprechung"},
                {"aufgabe","Aufgabe"},{"aufgaben","Aufgaben"},{"arbeitsblatt","Arbeitsblatt"},{"arbeitsblätter","Arbeitsblätter"},
                {"text","Text"},{"texte","Texte"},{"fragen","Fragen"},{"verständnisfragen","Verständnisfragen"},
                {"ferien","Ferien"},{"erlebnisse","Erlebnisse"},{"erlebnissen","Erlebnissen"},{"erzählrunde","Erzählrunde"},
                {"einkauf","Einkauf"},{"frühstück","Frühstück"},{"mittagessen","Mittagessen"},{"pausenaufsicht","Pausenaufsicht"},
                {"selbstversorgung","Selbstversorgung"},{"unterstützung","Unterstützung"},{"begleitung","Begleitung"},
                {"mathe","Mathematik"},{"mathematik","Mathematik"},{"deutsch","Deutsch"},{"hauswirtschaft","Hauswirtschaft"},
                {"freiarbeit","Freiarbeit"},{"unterricht","Unterricht"},{"übung","Übung"},{"festigung","Festigung"}
        };
        for (String[] pair : words) value = value.replaceAll("(?iu)\\b" + pair[0] + "\\b", pair[1]);
        return value.replaceAll("\\s+([,.;:])", "$1").replaceAll("\\s+", " ").trim();
    }

    private GermanEntryFormatter() {}
}

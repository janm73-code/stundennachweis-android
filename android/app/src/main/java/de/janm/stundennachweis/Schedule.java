package de.janm.stundennachweis;

import java.util.List;
import java.util.Map;

final class Schedule {
    static final String[] DAYS = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag"};
    static final String[] ROW_KEYS = {"early", "1", "2", "breakfast", "3", "pause1", "4", "5", "lunch", "pause2", "6", "7", "late"};
    static final String[] ROW_LABELS = {"Frühaufsicht", "1. Stunde", "2. Stunde", "Frühstück", "3. Stunde", "Pausenaufsicht", "4. Stunde", "5. Stunde", "Mittagessen", "Pausenaufsicht", "6. Stunde", "7. Stunde", "Spätaufsicht"};
    static final boolean[][] ACTIVE = {
            {true,true,true,true,true,true,true,true,true,true,true,true,true},
            {true,true,true,true,true,true,true,true,true,true,true,true,true},
            {true,true,true,false,true,false,true,true,true,true,true,true,true},
            {true,true,true,true,true,true,true,true,false,false,false,false,true},
            {true,true,true,true,true,true,true,true,true,false,false,false,false}
    };

    static int rowForKey(String key) {
        for (int i = 0; i < ROW_KEYS.length; i++) if (ROW_KEYS[i].equals(key)) return i;
        return -1;
    }

    static int requiredCount(int day) {
        int total = 0;
        for (boolean active : ACTIVE[day]) if (active) total++;
        return total;
    }

    static String labelForKey(String key) {
        int row = rowForKey(key);
        return row < 0 ? key : ROW_LABELS[row];
    }

    static List<String> activeKeys(int day) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < ROW_KEYS.length; i++) if (ACTIVE[day][i]) out.add(ROW_KEYS[i]);
        return out;
    }

    private Schedule() {}
}

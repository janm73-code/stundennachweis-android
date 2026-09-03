package de.janm.stundennachweis;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

final class EntryStore {
    private final SharedPreferences prefs;

    EntryStore(Context context) {
        prefs = context.getSharedPreferences("stundennachweis-data-v2", Context.MODE_PRIVATE);
    }

    String get(LocalDate date, String slot) {
        try { return day(date).optString(slot, ""); }
        catch (JSONException e) { return ""; }
    }

    void put(LocalDate date, String slot, String value) {
        try {
            JSONObject all = all();
            JSONObject day = all.optJSONObject(date.toString());
            if (day == null) day = new JSONObject();
            if (value == null || value.isBlank()) day.remove(slot); else day.put(slot, value.trim());
            all.put(date.toString(), day);
            save(all);
        } catch (JSONException ignored) {}
    }

    void clearDay(LocalDate date) {
        try { JSONObject all = all(); all.remove(date.toString()); save(all); }
        catch (JSONException ignored) {}
    }

    int filled(LocalDate date) {
        int day = date.getDayOfWeek().getValue() - 1;
        if (day < 0 || day > 4) return 0;
        int count = 0;
        for (String key : Schedule.activeKeys(day)) if (!get(date, key).isBlank()) count++;
        return count;
    }

    boolean isComplete(LocalDate date) {
        int day = date.getDayOfWeek().getValue() - 1;
        return day >= 0 && day < 5 && filled(date) == Schedule.requiredCount(day);
    }

    LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    Map<LocalDate, Map<String,String>> week(LocalDate anyDay) {
        Map<LocalDate, Map<String,String>> result = new LinkedHashMap<>();
        LocalDate monday = monday(anyDay);
        for (int d = 0; d < 5; d++) {
            LocalDate date = monday.plusDays(d);
            Map<String,String> entries = new LinkedHashMap<>();
            for (String key : Schedule.ROW_KEYS) entries.put(key, get(date, key));
            result.put(date, entries);
        }
        return result;
    }

    String exportJson() { return all().toString(); }

    boolean importJson(String json) {
        try { save(new JSONObject(json)); return true; }
        catch (JSONException e) { return false; }
    }

    private JSONObject day(LocalDate date) throws JSONException {
        JSONObject object = all().optJSONObject(date.toString());
        return object == null ? new JSONObject() : object;
    }

    private JSONObject all() {
        try { return new JSONObject(prefs.getString("entries", "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }

    private void save(JSONObject all) {
        prefs.edit().putString("entries", all.toString()).apply();
    }
}

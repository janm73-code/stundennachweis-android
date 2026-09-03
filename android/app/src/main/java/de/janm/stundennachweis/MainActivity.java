package de.janm.stundennachweis;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends android.app.Activity implements RecognitionListener {
    private static final int AUDIO_PERMISSION = 41;
    private static final long SILENCE_MS = 3000;
    private static final int NAVY = Color.rgb(12, 24, 50);
    private static final int GREEN = Color.rgb(15, 159, 120);
    private static final int SURFACE = Color.rgb(246, 249, 252);

    private EntryStore store;
    private SecretStore secrets;
    private OpenAiClient ai;
    private LocalDate selectedDate;
    private LinearLayout root;
    private LinearLayout content;
    private TextView dateTitle;
    private TextView weekTitle;
    private TextView aiDot;
    private Button micButton;
    private SpeechRecognizer recognizer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder spoken = new StringBuilder();
    private long lastSpeechAt;
    private boolean listening;
    private boolean stopRequested;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new EntryStore(this);
        secrets = new SecretStore(this);
        ai = new OpenAiClient(secrets);
        selectedDate = nearestSchoolDay(LocalDate.now());
        buildShell();
        showDay();
        if (getIntent().getBooleanExtra("start_voice", false)) handler.postDelayed(this::startVoice, 550);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(14), dp(18), dp(12));
        top.setBackgroundColor(NAVY);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView app = text("STUNDENNACHWEIS", 17, Color.WHITE, true);
        titleLine.addView(app, new LinearLayout.LayoutParams(0, dp(42), 1));
        aiDot = text("●", 25, secrets.hasKey() ? GREEN : Color.RED, true);
        aiDot.setContentDescription("KI-Verbindung");
        aiDot.setOnClickListener(v -> showSettings());
        titleLine.addView(aiDot, new LinearLayout.LayoutParams(dp(40), dp(42)));
        Button settings = smallButton("⚙");
        settings.setContentDescription("Einstellungen");
        settings.setOnClickListener(v -> showSettings());
        titleLine.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(42)));
        top.addView(titleLine);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = smallButton("‹"); previous.setOnClickListener(v -> shiftDay(-1));
        Button next = smallButton("›"); next.setOnClickListener(v -> shiftDay(1));
        dateTitle = text("", 25, Color.WHITE, true); dateTitle.setGravity(Gravity.CENTER);
        dateTitle.setOnClickListener(v -> pickDate());
        navigation.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        navigation.addView(dateTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(navigation);
        weekTitle = text("", 13, Color.rgb(193, 207, 230), false); weekTitle.setGravity(Gravity.CENTER);
        top.addView(weekTitle);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(120));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(10), dp(8), dp(10), dp(8));
        actions.setGravity(Gravity.CENTER);
        actions.setBackgroundColor(Color.WHITE);
        Button calendar = actionButton("Kalender"); calendar.setOnClickListener(v -> showCalendar());
        Button pdf = actionButton("PDF"); pdf.setOnClickListener(v -> savePdf());
        micButton = actionButton("●  SPRECHEN");
        micButton.setTextColor(Color.WHITE); micButton.setTextSize(15); micButton.setBackground(round(GREEN, 22));
        micButton.setOnClickListener(v -> { if (listening) finishVoice(); else startVoice(); });
        actions.addView(calendar, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(pdf, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(0, dp(56), 1.6f); micParams.setMargins(dp(8),0,0,0);
        actions.addView(micButton, micParams);
        root.addView(actions);
        setContentView(root);
    }

    private void showDay() {
        selectedDate = nearestSchoolDay(selectedDate);
        int day = selectedDate.getDayOfWeek().getValue() - 1;
        dateTitle.setText(Schedule.DAYS[day]);
        LocalDate monday = store.monday(selectedDate);
        weekTitle.setText("KW " + monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " · " + selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        content.removeAllViews();

        int filled = store.filled(selectedDate), required = Schedule.requiredCount(day);
        TextView status = text(filled == required ? "✓ Tag vollständig" : filled + " von " + required + " Einträgen", 14,
                filled == required ? Color.rgb(0,120,82) : filled == 0 ? Color.RED : Color.rgb(207,119,0), true);
        status.setPadding(dp(8), dp(4), dp(8), dp(12));
        content.addView(status);

        for (int row = 0; row < Schedule.ROW_KEYS.length; row++) addRow(day, row);
        LinearLayout remarks = new LinearLayout(this);
        remarks.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView remarkLabel = text("Bemerkungen", 14, Color.GRAY, true);
        TextView remarkValue = text("—", 15, Color.LTGRAY, false);
        remarks.addView(remarkLabel, new LinearLayout.LayoutParams(dp(125), ViewGroup.LayoutParams.WRAP_CONTENT));
        remarks.addView(remarkValue);
        content.addView(remarks);
    }

    private void addRow(int day, int row) {
        boolean active = Schedule.ACTIVE[day][row];
        String key = Schedule.ROW_KEYS[row];
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, active ? dp(76) : dp(54));
        params.setMargins(0, 0, 0, dp(1));
        line.setBackground(round(active ? Color.WHITE : Color.rgb(237,241,246), 2));
        TextView label = text(Schedule.ROW_LABELS[row], 14, active ? Color.rgb(62,79,101) : Color.GRAY, true);
        line.addView(label, new LinearLayout.LayoutParams(dp(125), ViewGroup.LayoutParams.WRAP_CONTENT));
        String value = active ? store.get(selectedDate, key) : "—";
        TextView entry = text(value.isBlank() ? "Noch kein Eintrag" : value, value.length() > 90 ? 12 : 14,
                value.isBlank() || !active ? Color.LTGRAY : Color.rgb(29,40,56), false);
        entry.setMaxLines(3);
        line.addView(entry, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (active) {
            TextView edit = text("✎", 20, GREEN, true); edit.setGravity(Gravity.CENTER);
            line.addView(edit, new LinearLayout.LayoutParams(dp(38), dp(48)));
            line.setOnClickListener(v -> editSlot(key));
        }
        content.addView(line, params);
    }

    private void editSlot(String key) {
        EditText input = new EditText(this);
        input.setText(store.get(selectedDate, key));
        input.setSelection(input.length()); input.setMinLines(3); input.setGravity(Gravity.TOP);
        int p = dp(20); input.setPadding(p,p,p,p);
        new AlertDialog.Builder(this)
                .setTitle(Schedule.labelForKey(key))
                .setView(input)
                .setNeutralButton("Löschen", (d,w) -> { store.put(selectedDate,key,""); showDay(); })
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d,w) -> { store.put(selectedDate,key,GermanEntryFormatter.finaliseAi(input.getText().toString())); showDay(); })
                .show();
    }

    private void shiftDay(int direction) {
        do { selectedDate = selectedDate.plusDays(direction); } while (selectedDate.getDayOfWeek() == DayOfWeek.SATURDAY || selectedDate.getDayOfWeek() == DayOfWeek.SUNDAY);
        showDay();
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (picker,y,m,d) -> {
            selectedDate = nearestSchoolDay(LocalDate.of(y,m+1,d)); showDay();
        }, selectedDate.getYear(), selectedDate.getMonthValue()-1, selectedDate.getDayOfMonth());
        dialog.show();
    }

    private void showCalendar() {
        YearMonth month = YearMonth.from(selectedDate);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView legend = text("● vollständig   ● teilweise   ● fehlt", 13, Color.DKGRAY, false); box.addView(legend);
        GridLayout grid = new GridLayout(this); grid.setColumnCount(7);
        for (String name : new String[]{"Mo","Di","Mi","Do","Fr","Sa","So"}) { TextView t=text(name,12,Color.GRAY,true); t.setGravity(Gravity.CENTER); grid.addView(t,new ViewGroup.LayoutParams(dp(42),dp(32))); }
        LocalDate first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i=0;i<42;i++) {
            LocalDate date = first.plusDays(i); boolean school = date.getDayOfWeek().getValue() <= 5;
            int filled = store.filled(date); int required = school ? Schedule.requiredCount(date.getDayOfWeek().getValue()-1) : 0;
            int color = !school ? Color.LTGRAY : filled==0 ? Color.RED : filled==required ? GREEN : Color.rgb(236,143,0);
            TextView cell = text(date.getDayOfMonth()+"\n●",12,color,false); cell.setGravity(Gravity.CENTER);
            if (!YearMonth.from(date).equals(month)) cell.setAlpha(.35f);
            cell.setOnClickListener(v -> { selectedDate=nearestSchoolDay(date); showDay(); ((AlertDialog)grid.getTag()).dismiss(); });
            grid.addView(cell,new ViewGroup.LayoutParams(dp(42),dp(48)));
        }
        box.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))).setView(box).setNegativeButton("Schließen",null).create();
        grid.setTag(dialog); dialog.show();
    }

    private void showSettings() {
        EditText key = new EditText(this); key.setHint("sk-…"); key.setSingleLine(true); key.setInputType(0x00000081);
        if (secrets.hasKey()) key.setHint("Schlüssel ist sicher gespeichert");
        new AlertDialog.Builder(this).setTitle("KI-Einstellungen")
                .setMessage("Modell: " + OpenAiClient.MODEL + "\nDer Schlüssel bleibt verschlüsselt auf diesem Handy.")
                .setView(key).setNegativeButton("Abbrechen",null)
                .setNeutralButton("Entfernen",(d,w)-> { try { secrets.saveApiKey(""); } catch(Exception ignored){} updateAiDot(false); })
                .setPositiveButton("Speichern & prüfen",(d,w)-> {
                    try { if (!key.getText().toString().isBlank()) secrets.saveApiKey(key.getText().toString()); }
                    catch(Exception e){ toast("Schlüssel konnte nicht gespeichert werden"); return; }
                    updateAiDot(null); executor.execute(() -> { boolean ok=ai.test(); runOnUiThread(() -> { updateAiDot(ok); toast(ok?"KI verbunden":"Keine KI-Verbindung"); }); });
                }).show();
    }

    private void updateAiDot(Boolean connected) {
        aiDot.setTextColor(connected == null ? Color.rgb(236,143,0) : connected ? GREEN : Color.RED);
    }

    private void startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("Auf diesem Handy ist keine Spracherkennung verfügbar"); return; }
        if (recognizer == null) { recognizer = SpeechRecognizer.createSpeechRecognizer(this); recognizer.setRecognitionListener(this); }
        spoken.setLength(0); lastSpeechAt=0; stopRequested=false; listening=true;
        micButton.setText("■  AUFNAHME"); micButton.setBackground(round(Color.rgb(210,50,50),22));
        beginRecognition();
    }

    private void beginRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS);
        try { recognizer.startListening(intent); } catch (Exception e) { finishVoice(); }
    }

    private void received(Bundle results, boolean finalResult) {
        ArrayList<String> list = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return;
        String best = list.get(0).trim();
        if (best.isBlank()) return;
        if (finalResult) mergeSpoken(best); else { lastSpeechAt=System.currentTimeMillis(); scheduleSilenceCheck(); }
    }

    private void mergeSpoken(String text) {
        String current = spoken.toString();
        if (current.isBlank()) spoken.append(text);
        else if (!current.toLowerCase(Locale.GERMAN).endsWith(text.toLowerCase(Locale.GERMAN))) spoken.append(' ').append(text);
        lastSpeechAt=System.currentTimeMillis(); scheduleSilenceCheck();
    }

    private void scheduleSilenceCheck() {
        handler.removeCallbacksAndMessages("silence");
        handler.postAtTime(() -> {
            if (!listening) return;
            long remaining = SILENCE_MS - (System.currentTimeMillis()-lastSpeechAt);
            if (lastSpeechAt>0 && remaining<=0) finishVoice(); else handler.postAtTime(this::finishVoice,"silence",System.currentTimeMillis()+Math.max(300,remaining));
        }, "silence", System.currentTimeMillis()+SILENCE_MS);
    }

    private void finishVoice() {
        if (!listening) return;
        listening=false; stopRequested=true;
        try { recognizer.stopListening(); } catch(Exception ignored) {}
        micButton.setText("●  SPRECHEN"); micButton.setBackground(round(GREEN,22));
        String value=spoken.toString().trim();
        if (!value.isBlank()) processVoice(value); else toast("Nichts verstanden – bitte erneut sprechen");
    }

    private void processVoice(String text) {
        String lower=text.toLowerCase(Locale.GERMAN);
        if (lower.matches(".*\\b(drucke|druck|speichere|erstelle)\\b.*\\b(woche|stundennachweis|pdf)\\b.*")) { savePdf(); return; }
        VoiceCommand.Parsed parsed=VoiceCommand.parse(text);
        if (parsed.clearDay()) {
            new AlertDialog.Builder(this).setTitle("Alle Einträge löschen?").setMessage(Schedule.DAYS[selectedDate.getDayOfWeek().getValue()-1]+", "+selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Tag löschen",(d,w)->{store.clearDay(selectedDate);showDay();}).show(); return;
        }
        if (!parsed.deleteSlots().isEmpty()) { for(String slot:parsed.deleteSlots()) store.put(selectedDate,slot,""); showDay(); toast("Einträge gelöscht"); return; }
        if (!parsed.entries().isEmpty()) { applySpokenEntries(parsed.entries()); return; }
        chooseSlotFor(parsed.unassigned());
    }

    private void applySpokenEntries(Map<String,String> entries) {
        ProgressBar progress=new ProgressBar(this); progress.setPadding(dp(32),dp(24),dp(32),dp(24));
        AlertDialog wait=new AlertDialog.Builder(this).setTitle("Eintrag wird geprüft …").setView(progress).setCancelable(false).create(); wait.show();
        executor.execute(() -> {
            for (Map.Entry<String,String> entry:entries.entrySet()) {
                int row=Schedule.rowForKey(entry.getKey()); int day=selectedDate.getDayOfWeek().getValue()-1;
                if (row<0 || !Schedule.ACTIVE[day][row]) continue;
                String formatted;
                try { formatted=secrets.hasKey()?ai.professionalise(entry.getValue()):GermanEntryFormatter.localFormat(entry.getValue()); }
                catch(Exception e){ formatted=GermanEntryFormatter.localFormat(entry.getValue()); }
                store.put(selectedDate,entry.getKey(),formatted);
            }
            runOnUiThread(()->{wait.dismiss();showDay();toast(secrets.hasKey()?"Fachlich und orthografisch geprüft":"Lokal rechtschreibgeprüft");});
        });
    }

    private void chooseSlotFor(String text) {
        int day=selectedDate.getDayOfWeek().getValue()-1; List<String> keys=Schedule.activeKeys(day);
        String[] labels=keys.stream().map(Schedule::labelForKey).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Wohin gehört der Eintrag?").setItems(labels,(d,which)->applySpokenEntries(Map.of(keys.get(which),text))).setNegativeButton("Abbrechen",null).show();
    }

    private void savePdf() {
        executor.execute(() -> {
            try {
                Uri uri=WeekPdf.save(this,store,selectedDate);
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("PDF gespeichert")
                        .setMessage(WeekPdf.filename(store,selectedDate)+"\n\nOrdner: Dokumente/Stundennachweise")
                        .setNegativeButton("Schließen",null).setPositiveButton("Öffnen / Drucken",(d,w)->openPdf(uri)).show());
            } catch(Exception e){ runOnUiThread(()->toast("PDF konnte nicht gespeichert werden")); }
        });
    }

    private void openPdf(Uri uri) {
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(intent,"PDF öffnen oder drucken")); } catch(Exception e){ toast("Keine PDF-App gefunden"); }
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() { lastSpeechAt=System.currentTimeMillis(); scheduleSilenceCheck(); }
    @Override public void onRmsChanged(float rmsdB) { if (listening && rmsdB>1.5f) { lastSpeechAt=System.currentTimeMillis(); scheduleSilenceCheck(); } }
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onError(int error) {
        if (!listening || stopRequested) return;
        long elapsed=lastSpeechAt==0?0:System.currentTimeMillis()-lastSpeechAt;
        if (lastSpeechAt>0 && elapsed<SILENCE_MS) handler.postDelayed(this::beginRecognition,250); else if(lastSpeechAt>0) finishVoice(); else { listening=false; micButton.setText("●  SPRECHEN"); micButton.setBackground(round(GREEN,22)); toast("Nicht verstanden – bitte erneut sprechen"); }
    }
    @Override public void onResults(Bundle results) { received(results,true); if(listening) handler.postDelayed(this::beginRecognition,250); }
    @Override public void onPartialResults(Bundle partialResults) { received(partialResults,false); }
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==AUDIO_PERMISSION && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) startVoice();
        else if(requestCode==AUDIO_PERMISSION) new AlertDialog.Builder(this).setTitle("Mikrofon benötigt").setMessage("Für den einzigen Sprachknopf muss die Mikrofon-Berechtigung erlaubt sein.")
                .setNegativeButton("Später",null).setPositiveButton("Einstellungen",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).show();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); if(recognizer!=null) recognizer.destroy(); executor.shutdownNow(); super.onDestroy();
    }

    private LocalDate nearestSchoolDay(LocalDate date) {
        if(date.getDayOfWeek()==DayOfWeek.SATURDAY) return date.minusDays(1);
        if(date.getDayOfWeek()==DayOfWeek.SUNDAY) return date.plusDays(1);
        return date;
    }
    private TextView text(String value,float size,int color,boolean bold) { TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(v.getTypeface(),1);return v; }
    private Button smallButton(String value){Button b=new Button(this);b.setText(value);b.setTextSize(22);b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(0,0,0,0);return b;}
    private Button actionButton(String value){Button b=new Button(this);b.setText(value);b.setTextColor(NAVY);b.setTextSize(13);b.setAllCaps(false);b.setBackground(round(Color.rgb(235,241,248),16));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}

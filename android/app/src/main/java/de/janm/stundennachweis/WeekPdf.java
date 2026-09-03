package de.janm.stundennachweis;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class WeekPdf {
    private static final int WIDTH = 595;
    private static final int HEIGHT = 842;
    private static final int MARGIN = 22;

    static byte[] create(EntryStore store, LocalDate anyDay) throws Exception {
        LocalDate monday = store.monday(anyDay);
        Map<LocalDate, Map<String,String>> week = store.week(monday);
        PdfDocument document = new PdfDocument();
        drawPage(document, week, monday, 1, 0, 3);
        drawPage(document, week, monday, 2, 3, 5);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.writeTo(output);
        document.close();
        return output.toByteArray();
    }

    static Uri save(Context context, EntryStore store, LocalDate anyDay) throws Exception {
        LocalDate monday = store.monday(anyDay);
        LocalDate friday = monday.plusDays(4);
        int kw = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String filename = String.format("Stundennachweis_KW%02d_%s_bis_%s.pdf", kw, monday, friday);
        String schoolYear = monday.getMonthValue() >= 8
                ? monday.getYear() + "-" + String.valueOf(monday.getYear() + 1).substring(2)
                : (monday.getYear() - 1) + "-" + String.valueOf(monday.getYear()).substring(2);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Stundennachweise/Schuljahr_" + schoolYear);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
        if (uri == null) throw new IllegalStateException("Datei konnte nicht angelegt werden");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Datei konnte nicht geöffnet werden");
            out.write(create(store, monday));
        }
        return uri;
    }

    static String filename(EntryStore store, LocalDate anyDay) {
        LocalDate monday = store.monday(anyDay);
        return String.format("Stundennachweis_KW%02d_%s_bis_%s.pdf",
                monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), monday, monday.plusDays(4));
    }

    private static void drawPage(PdfDocument document, Map<LocalDate, Map<String,String>> week, LocalDate monday, int number, int startDay, int endDay) {
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create());
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(13);
        int kw = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        canvas.drawText("Stundennachweis KW " + kw + " · " + format(monday) + " bis " + format(monday.plusDays(4)), MARGIN, 30, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(7);
        canvas.drawText("Seite " + number + " von 2", WIDTH - 70, 30, paint);

        float top = 42;
        float available = number == 2 ? 690 : 780;
        float dayHeight = available / (endDay - startDay);
        for (int day = startDay; day < endDay; day++) {
            LocalDate date = monday.plusDays(day);
            drawDay(canvas, paint, day, date, week.get(date), top, dayHeight - 6);
            top += dayHeight;
        }
        if (number == 2) {
            paint.setTextSize(7.5f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            float y = 770;
            canvas.drawText("Datum / Unterschrift Beschäftigte:r", MARGIN, y, paint);
            canvas.drawLine(MARGIN, y + 28, 270, y + 28, paint);
            canvas.drawText("Datum / Unterschrift Vorgesetzte:r", 320, y, paint);
            canvas.drawLine(320, y + 28, WIDTH - MARGIN, y + 28, paint);
        }
        document.finishPage(page);
    }

    private static void drawDay(Canvas canvas, Paint paint, int day, LocalDate date, Map<String,String> values, float top, float height) {
        float left = MARGIN, right = WIDTH - MARGIN;
        float header = 21;
        float rowHeight = (height - header) / 13f;
        float labelRight = left + 87;
        float remarkLeft = right - 54;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(235, 241, 248));
        canvas.drawRect(left, top, right, top + header, paint);
        paint.setColor(Color.BLACK);
        paint.setTextSize(8.5f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText(Schedule.DAYS[day] + " · " + format(date), left + 5, top + 14, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.7f);
        canvas.drawRect(left, top, right, top + height, paint);
        canvas.drawLine(labelRight, top + header, labelRight, top + height, paint);
        canvas.drawLine(remarkLeft, top + header, remarkLeft, top + height, paint);

        for (int row = 0; row < 13; row++) {
            float y = top + header + row * rowHeight;
            canvas.drawLine(left, y, right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(6.6f);
            paint.setTypeface(Schedule.ACTIVE[day][row] ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setColor(Schedule.ACTIVE[day][row] ? Color.DKGRAY : Color.GRAY);
            canvas.drawText(Schedule.ROW_LABELS[row], left + 4, y + Math.min(10, rowHeight - 2), paint);
            if (Schedule.ACTIVE[day][row]) {
                String value = values == null ? "" : values.getOrDefault(Schedule.ROW_KEYS[row], "");
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setColor(Color.BLACK);
                paint.setTextSize(fittedSize(value));
                drawWrapped(canvas, paint, value, labelRight + 4, y + 2, remarkLeft - labelRight - 8, rowHeight - 3);
            }
            paint.setStyle(Paint.Style.STROKE);
        }
        canvas.drawLine(left, top + height, right, top + height, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static float fittedSize(String text) {
        int length = text == null ? 0 : text.length();
        if (length > 130) return 4.4f;
        if (length > 95) return 4.9f;
        if (length > 65) return 5.4f;
        return 6.0f;
    }

    private static void drawWrapped(Canvas canvas, Paint paint, String text, float x, float y, float width, float height) {
        if (text == null || text.isBlank()) return;
        List<String> lines = wrap(paint, text, width);
        float lineHeight = paint.getTextSize() + 1.2f;
        int max = Math.max(1, (int)(height / lineHeight));
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            String line = lines.get(i);
            if (i == max - 1 && lines.size() > max) line = ellipsize(paint, line + " …", width);
            canvas.drawText(line, x, y + lineHeight * (i + 1), paint);
        }
    }

    private static List<String> wrap(Paint paint, String text, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (paint.measureText(candidate) <= width) line = new StringBuilder(candidate);
            else { if (!line.isEmpty()) lines.add(line.toString()); line = new StringBuilder(word); }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static String ellipsize(Paint paint, String text, float width) {
        while (text.length() > 2 && paint.measureText(text) > width) text = text.substring(0, text.length() - 2) + "…";
        return text;
    }

    private static String format(LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
    private WeekPdf() {}
}

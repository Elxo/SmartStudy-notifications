package com.calendar.monitor;

import com.calendar.model.Event;
import com.calendar.scraper.ScheduleScraper;
import com.calendar.university.TuribaUniversity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public final class ScheduleMonitor {

    private static final int WINDOW_DAYS = 7;
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("EEE dd.MM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleMonitor() {}

    /** One scheduled lesson, as stored in the snapshot file. */
    record Slot(LocalDateTime start, LocalDateTime end, String title, String location) {

        static Slot of(Event event) {
            return new Slot(event.getStartTime(), event.getEndTime(),
                    clean(event.getTitle()), clean(event.getLocation()));
        }

        static Slot parse(String line) {
            String[] parts = line.split("\t", -1);
            return new Slot(LocalDateTime.parse(parts[0]), LocalDateTime.parse(parts[1]),
                    parts[2], parts[3]);
        }

        String serialize() {
            return start + "\t" + end + "\t" + title + "\t" + location;
        }

        LocalDate date() { return start.toLocalDate(); }

        String time() { return start.format(TIME_FMT) + "-" + end.format(TIME_FMT); }

        String room() { return location.isEmpty() ? "no room" : location; }

        String describe() { return time() + " " + title + " (" + room() + ")"; }

        private static String clean(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").trim();
        }
    }

    public static void main(String[] args) throws Exception {
        String groupCode = requiredEnv("TURIBA_GROUP_CODE");
        String topicUrl = requiredEnv("NTFY_TOPIC_URL");
        Path stateFile = Path.of(System.getenv().getOrDefault(
                "SCHEDULE_STATE_FILE", "monitor/schedule.tsv"));

        List<Slot> current = new ScheduleScraper(null, new TuribaUniversity(), groupCode)
                .scrapeEvents().stream()
                .map(Slot::of)
                .sorted(Comparator.comparing(Slot::serialize))
                .toList();

        if (!Files.exists(stateFile)) {
            writeState(stateFile, current);
            System.out.println("Stored initial schedule snapshot (" + current.size() + " events).");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Riga"));
        String changes = describeChanges(readState(stateFile), current,
                today, today.plusDays(WINDOW_DAYS));

        if (changes.isEmpty()) {
            System.out.println("No schedule changes in the next " + WINDOW_DAYS + " days.");
        } else {
            System.out.println("Schedule changes:\n" + changes);
            sendNotification(topicUrl, changes);
            System.out.println("ntfy notification sent.");
        }
        writeState(stateFile, current);
    }

    /** Lists the differences for lessons dated in [from, to), grouped by day. */
    static String describeChanges(List<Slot> previous, List<Slot> current,
                                  LocalDate from, LocalDate to) {
        List<Slot> removed = inWindow(previous, from, to);
        List<Slot> added = inWindow(current, from, to);
        removed.removeIf(added::remove);

        TreeMap<LocalDate, List<String>> byDay = new TreeMap<>();

        // A lesson with the same title on the same day counts as changed, not removed + added.
        for (Iterator<Slot> it = removed.iterator(); it.hasNext(); ) {
            Slot old = it.next();
            Slot updated = added.stream()
                    .filter(s -> s.date().equals(old.date()) && s.title().equals(old.title()))
                    .findFirst().orElse(null);
            if (updated == null) continue;
            added.remove(updated);
            it.remove();

            List<String> diffs = new ArrayList<>();
            if (!old.time().equals(updated.time())) {
                diffs.add(old.time() + " → " + updated.time());
            }
            if (!old.location().equals(updated.location())) {
                diffs.add("room " + old.room() + " → " + updated.room());
            }
            day(byDay, old.date()).add("✏️ " + old.title() + ": " + String.join(", ", diffs));
        }
        added.forEach(slot -> day(byDay, slot.date()).add("➕ " + slot.describe()));
        removed.forEach(slot -> day(byDay, slot.date()).add("❌ " + slot.describe()));

        StringBuilder message = new StringBuilder();
        byDay.forEach((date, lines) -> {
            if (!message.isEmpty()) message.append("\n\n");
            message.append(date.format(DAY_FMT));
            lines.forEach(line -> message.append("\n").append(line));
        });
        return message.toString();
    }

    private static List<Slot> inWindow(List<Slot> slots, LocalDate from, LocalDate to) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (!slot.date().isBefore(from) && slot.date().isBefore(to)) result.add(slot);
        }
        result.sort(Comparator.comparing(Slot::start));
        return result;
    }

    private static List<String> day(TreeMap<LocalDate, List<String>> byDay, LocalDate date) {
        return byDay.computeIfAbsent(date, d -> new ArrayList<>());
    }

    private static void sendNotification(String topicUrl, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(topicUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Title", "SmartStudy schedule changed")
                .header("Priority", "high")
                .header("Tags", "calendar")
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("ntfy returned HTTP " + response.statusCode());
        }
    }

    private static List<Slot> readState(Path stateFile) throws IOException {
        return Files.readAllLines(stateFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .map(Slot::parse)
                .toList();
    }

    private static void writeState(Path stateFile, List<Slot> slots) throws IOException {
        Path parent = stateFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(stateFile, slots.stream().map(Slot::serialize).toList(), StandardCharsets.UTF_8);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }
}

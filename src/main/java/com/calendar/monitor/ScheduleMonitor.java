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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public final class ScheduleMonitor {

    private ScheduleMonitor() {}

    public static void main(String[] args) throws Exception {
        if ("schedule".equals(System.getenv("GITHUB_EVENT_NAME"))) {
            int hour = ZonedDateTime.now(ZoneId.of("Europe/Riga")).getHour();
            if (hour != 6 && hour != 22) {
                System.out.println("Skipping UTC trigger outside 06:00/22:00 Europe/Riga.");
                return;
            }
        }

        String groupCode = requiredEnv("TURIBA_GROUP_CODE");
        String topicUrl = requiredEnv("NTFY_TOPIC_URL");
        Path stateFile = Path.of(System.getenv().getOrDefault(
                "SCHEDULE_STATE_FILE", "monitor/schedule.sha256"));

        List<Event> events = new ScheduleScraper(null, new TuribaUniversity(), groupCode)
                .scrapeEvents();
        String currentHash = hash(events);
        String previousHash = readState(stateFile);

        if (previousHash.isBlank()) {
            writeState(stateFile, currentHash);
            System.out.println("Stored initial schedule snapshot (" + events.size() + " events).");
            return;
        }

        if (previousHash.equals(currentHash)) {
            System.out.println("No schedule changes detected.");
            return;
        }

        sendNotification(topicUrl, groupCode, events.size());
        writeState(stateFile, currentHash);
        System.out.println("Schedule changed; ntfy notification sent.");
    }

    private static String hash(List<Event> events) throws Exception {
        String snapshot = events.stream()
                .map(event -> String.join("|",
                        value(event.getTitle()),
                        event.getStartTime().toString(),
                        event.getEndTime().toString(),
                        value(event.getLocation())))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8)));
    }

    private static void sendNotification(String topicUrl, String groupCode, int eventCount)
            throws IOException, InterruptedException {
        String body = "The schedule for " + groupCode + " changed. "
                + eventCount + " published event(s) are available.";
        HttpRequest request = HttpRequest.newBuilder(URI.create(topicUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Title", "SmartStudy schedule changed")
                .header("Priority", "high")
                .header("Tags", "calendar")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("ntfy returned HTTP " + response.statusCode());
        }
    }

    private static String readState(Path stateFile) throws IOException {
        return Files.exists(stateFile) ? Files.readString(stateFile).trim() : "";
    }

    private static void writeState(Path stateFile, String hash) throws IOException {
        Path parent = stateFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(stateFile, hash + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
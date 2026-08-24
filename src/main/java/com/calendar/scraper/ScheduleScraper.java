package com.calendar.scraper;

import com.calendar.db.DatabaseManager;
import com.calendar.model.Event;
import com.calendar.university.University;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleScraper {

    private final University university;
    private final String groupCode;

    // Lesson slot number → {start, end} for Riga weekdays
    private static final Map<Integer, LocalTime[]> LESSON_TIMES = Map.of(
            1, new LocalTime[]{LocalTime.of(9,  0),  LocalTime.of(10, 30)},
            2, new LocalTime[]{LocalTime.of(10, 45), LocalTime.of(12, 15)},
            3, new LocalTime[]{LocalTime.of(12, 55), LocalTime.of(14, 25)},
            4, new LocalTime[]{LocalTime.of(14, 40), LocalTime.of(16, 10)},
            5, new LocalTime[]{LocalTime.of(16, 25), LocalTime.of(17, 55)},
            6, new LocalTime[]{LocalTime.of(17, 55), LocalTime.of(18, 15)},
            7, new LocalTime[]{LocalTime.of(18, 15), LocalTime.of(19, 45)},
            8, new LocalTime[]{LocalTime.of(20,  0),  LocalTime.of(21, 30)}
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DatabaseManager db;

    public ScheduleScraper(DatabaseManager db, University university, String groupCode) {
        this.db = db;
        this.university = university;
        this.groupCode = groupCode;
    }

    public int scrapeAndSave() throws Exception {
        int totalAdded = 0;
        List<Event> events = scrapeEvents();

        for (Event event : events) {
            if (!db.eventExists(event.getTitle(), event.getStartTime())) {
                db.saveEvent(event);
                totalAdded++;
            }
        }

        return totalAdded;
    }

    public List<Event> scrapeEvents() throws Exception {
        List<Event> allEvents = new ArrayList<>();
        int weeksWithData = 0;

        LocalDate currentWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate yearEnd = LocalDate.of(currentWeekStart.getYear(), 12, 31);
        int lastWeekOffset = (int) ChronoUnit.WEEKS.between(currentWeekStart, yearEnd);

        // Keep the lookback while scraping every week through the end of the year.
        for (int offset = -13; offset <= lastWeekOffset; offset++) {
            String url = university.buildScheduleUrl(groupCode, offset);
            System.out.println("  Checking week " + (offset >= 0 ? "+" : "") + offset + "...");

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(15_000)
                    .get();

            if (doc.select("tbody.content2").isEmpty()) continue;

            List<Event> events = parseWeek(doc);
            weeksWithData++;
            allEvents.addAll(events);
        }

        System.out.println("Scraped " + weeksWithData + " week(s) with published schedule.");
        return allEvents;
    }

    private List<Event> parseWeek(Document doc) {
        List<Event> events = new ArrayList<>();
        Elements rows = doc.select("tbody.content2 tr");

        Event current = null;
        LocalDate currentDate = null;

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.isEmpty()) continue;

            // Update date if this row has one
            String dateCellText = cells.get(0).text().trim();
            boolean hasDate = !dateCellText.isBlank() && !dateCellText.equals(" ");
            if (hasDate) {
                String dateStr = extractDate(cells.get(0));
                if (dateStr != null) {
                    try { currentDate = LocalDate.parse(dateStr, DATE_FMT); }
                    catch (Exception ignored) {}
                }
            }
            if (currentDate == null) continue;

            // The table has two column layouts:
            //   colspan row (4 cells): date | "N. Title" (colspan=2) | group | room
            //   normal row  (5 cells): date | "N. Category*"         | Title | group | room
            boolean isColspan = cells.size() <= 4;

            String title;
            String room;
            int lessonNum;

            if (isColspan) {
                String lessonAndTitle = cells.get(1).text().trim();
                // Blank colspan rows are empty subgroup slots — skip
                if (lessonAndTitle.isBlank() || lessonAndTitle.equals(" ")) continue;

                lessonNum = parseLessonNum(lessonAndTitle);
                title     = lessonAndTitle.replaceFirst("^\\d+\\.\\s*", "").trim();
                room      = cells.size() > 3 ? cells.get(3).text().trim() : "";
            } else {
                // 5-cell row: elective / "Study Courses by choice*"
                title     = cells.get(2).text().trim();
                room      = cells.get(4).text().trim();
                lessonNum = parseLessonNum(cells.get(1).text());
                if (title.isBlank() || title.equals(" ")) continue;
            }

            if (title.isBlank() || lessonNum < 1) continue;

            LocalTime[] times = LESSON_TIMES.get(lessonNum);
            if (times == null) continue;

            // Extend current event if same title + same day, otherwise start a new one
            boolean isContinuation = current != null
                    && current.getTitle().equals(title)
                    && current.getStartTime().toLocalDate().equals(currentDate);

            if (isContinuation) {
                current.setEndTime(LocalDateTime.of(currentDate, times[1]));
            } else {
                if (current != null) events.add(current);
                current = new Event(
                        title, null, null,
                        room.isBlank() ? null : room,
                        LocalDateTime.of(currentDate, times[0]),
                        LocalDateTime.of(currentDate, times[1]),
                        null, "scraped"
                );
            }
        }

        if (current != null) events.add(current);
        return events;
    }

    // Extracts "23.03.2026" from a td containing "Monday<br>23.03.2026"
    private String extractDate(Element td) {
        String html = td.html();
        if (html.contains("<br>")) {
            String after = html.split("<br>")[1].replaceAll("<[^>]+>", "").trim();
            if (after.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) return after;
        }
        for (String part : td.text().split("\\s+")) {
            if (part.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) return part;
        }
        return null;
    }

    // Extracts the leading integer from "2. Study Courses..." or "2. Title"
    private int parseLessonNum(String text) {
        String digits = text.replaceAll("^(\\d+).*", "$1").trim();
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException e) { return -1; }
    }
}

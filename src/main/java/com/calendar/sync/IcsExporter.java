package com.calendar.sync;

import com.calendar.model.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class IcsExporter {

    private static final DateTimeFormatter ICS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String OUTPUT_FILE = "university_schedule.ics";

    public String export(List<Event> events) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//University Calendar//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");

        for (Event event : events) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.getId()).append("@university-calendar\r\n");
            sb.append("DTSTART:").append(event.getStartTime().format(ICS_FMT)).append("\r\n");
            sb.append("DTEND:").append(event.getEndTime().format(ICS_FMT)).append("\r\n");
            sb.append("SUMMARY:").append(escapeText(event.getTitle())).append("\r\n");

            if (event.getLocation() != null && !event.getLocation().isEmpty())
                sb.append("LOCATION:").append(escapeText(event.getLocation())).append("\r\n");

            StringBuilder desc = new StringBuilder();
            if (event.getCourseCode() != null) desc.append("Course: ").append(event.getCourseCode());
            if (event.getInstructor() != null) {
                if (desc.length() > 0) desc.append("\\n");
                desc.append("Instructor: ").append(event.getInstructor());
            }
            if (desc.length() > 0)
                sb.append("DESCRIPTION:").append(desc).append("\r\n");

            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");

        Files.writeString(Path.of(OUTPUT_FILE), sb.toString());
        return Path.of(OUTPUT_FILE).toAbsolutePath().toString();
    }

    private String escapeText(String text) {
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}

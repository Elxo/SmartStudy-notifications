package com.calendar.ui;

import com.calendar.db.DatabaseManager;
import com.calendar.model.Event;
import com.calendar.scraper.ScheduleScraper;
import com.calendar.sync.GoogleCalendarSync;
import com.calendar.sync.IcsExporter;
import com.calendar.university.University;
import com.calendar.university.UniversityRegistry;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class ConsoleMenu {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DatabaseManager db;
    private final Scanner scanner = new Scanner(System.in);

    private University currentUniversity;
    private String currentGroupCode;

    public ConsoleMenu(DatabaseManager db) {
        this.db = db;
    }

    public void run() {
        System.out.println("==================================");
        System.out.println("    University Calendar v1.0      ");
        System.out.println("==================================");

        // Load saved university/group or prompt user to select
        loadOrSelectUniversityAndGroup();

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            System.out.println();
            switch (choice) {
                case "1" -> viewWeeklySchedule();
                case "2" -> addManualEvent();
                case "3" -> deleteEvent();
                case "4" -> exportToIcs();
                case "5" -> syncToGoogle();
                case "6" -> viewByMonth();
                case "7" -> changeUniversityAndGroup();
                case "0" -> running = false;
                default  -> System.out.println("Invalid option.");
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMenu() {
        System.out.println("\n  University : " + (currentUniversity != null ? currentUniversity.getName() : "None"));
        System.out.println("  Group      : " + (currentGroupCode != null ? currentGroupCode : "None"));
        System.out.println("  ----------------------------------");
        System.out.println("  1. View this week's schedule");
        System.out.println("  2. Add manual event");
        System.out.println("  3. Delete event");
        System.out.println("  4. Export to .ics (Apple / Outlook Calendar)");
        System.out.println("  5. Sync to Google Calendar");
        System.out.println("  6. View schedule for a specific month");
        System.out.println("  7. Change university / group");
        System.out.println("  0. Exit");
        System.out.print("\nChoice: ");
    }

    // ── University / Group selection ──────────────────────────────────────────

    private void loadOrSelectUniversityAndGroup() {
        try {
            String uCode = db.getSetting("university_code");
            String group = db.getSetting("group_code");

            if (uCode != null && group != null) {
                currentUniversity = UniversityRegistry.getByCode(uCode);
                currentGroupCode  = group;
                System.out.println("Loaded: " + currentUniversity.getName() + " | Group: " + group);
            } else {
                System.out.println("No university configured. Please select one now.");
                selectUniversityAndGroup();
            }
        } catch (Exception ex) {
            System.err.println("Error loading settings: " + ex.getMessage());
            selectUniversityAndGroup();
        }
    }

    private void changeUniversityAndGroup() {
        selectUniversityAndGroup();
    }

    private void selectUniversityAndGroup() {
        List<University> universities = UniversityRegistry.getAll();

        System.out.println("\n-- Select University --");
        for (int i = 0; i < universities.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + universities.get(i).getName());
        }
        System.out.print("Choice (1-" + universities.size() + "): ");

        int uIndex;
        try {
            uIndex = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (uIndex < 0 || uIndex >= universities.size()) {
                System.out.println("Invalid choice.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        University selected = universities.get(uIndex);
        System.out.println("Selected: " + selected.getName());
        System.out.println("Fetching available groups...");

        List<String> groups;
        try {
            groups = selected.fetchGroups();
        } catch (Exception ex) {
            System.err.println("Could not fetch groups: " + ex.getMessage());
            return;
        }

        System.out.println("\n-- Available Groups (" + groups.size() + ") --");
        for (int i = 0; i < groups.size(); i++) {
            System.out.printf("  %-12s", groups.get(i));
            if ((i + 1) % 6 == 0) System.out.println();
        }
        System.out.println();
        System.out.print("Enter your group code: ");
        String groupCode = scanner.nextLine().trim().toUpperCase();

        if (groupCode.isEmpty()) {
            System.out.println("Group code cannot be empty.");
            return;
        }

        if (!groups.contains(groupCode)) {
            System.out.println("Warning: '" + groupCode + "' not found in the group list. Saving anyway.");
        }

        try {
            db.setSetting("university_code", selected.getCode());
            db.setSetting("group_code", groupCode);
            currentUniversity = selected;
            currentGroupCode  = groupCode;
            System.out.println("Saved. University: " + selected.getName() + " | Group: " + groupCode);
            System.out.println("Fetching schedule (~3 months back and forward). This may take ~30 seconds...");
            autoScrape();
        } catch (Exception ex) {
            System.err.println("Failed to save settings: " + ex.getMessage());
        }
    }

    private void autoScrape() {
        try {
            System.out.println("Clearing previous scraped data...");
            db.deleteScrapedEvents();
            ScheduleScraper scraper = new ScheduleScraper(db, currentUniversity, currentGroupCode);
            int added = scraper.scrapeAndSave();
            System.out.println("Schedule loaded. " + added + " event(s) added.");
        } catch (Exception ex) {
            System.err.println("Auto-scrape failed: " + ex.getMessage());
        }
    }

    // ── Menu actions ──────────────────────────────────────────────────────────

    private void viewWeeklySchedule() {
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        try {
            List<Event> events = db.getEventsForWeek(weekStart);
            if (events.isEmpty()) { System.out.println("No events found for this week."); return; }
            System.out.println("--------------------------------------------------");
            for (Event e : events) System.out.println(e);
            System.out.println("--------------------------------------------------");
            System.out.println(events.size() + " event(s).");
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    private void addManualEvent() {
        System.out.println("-- Add Manual Event --");
        try {
            System.out.print("Title: ");
            String title = scanner.nextLine().trim();
            if (title.isEmpty()) { System.out.println("Title cannot be empty."); return; }

            System.out.print("Course code (press Enter to skip): ");
            String code = nullIfEmpty(scanner.nextLine().trim());

            System.out.print("Instructor (press Enter to skip): ");
            String instructor = nullIfEmpty(scanner.nextLine().trim());

            System.out.print("Location (press Enter to skip): ");
            String location = nullIfEmpty(scanner.nextLine().trim());

            System.out.print("Date (yyyy-MM-dd): ");
            LocalDate date = LocalDate.parse(scanner.nextLine().trim(), DATE_FMT);

            System.out.print("Start time (HH:mm): ");
            LocalTime start = LocalTime.parse(scanner.nextLine().trim(), TIME_FMT);

            System.out.print("End time (HH:mm): ");
            LocalTime end = LocalTime.parse(scanner.nextLine().trim(), TIME_FMT);

            Event event = new Event(title, code, instructor, location,
                    LocalDateTime.of(date, start), LocalDateTime.of(date, end),
                    null, "manual");
            int id = db.saveEvent(event);
            System.out.println("Event saved with ID: " + id);

        } catch (DateTimeParseException ex) {
            System.err.println("Invalid date or time format.");
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    private void deleteEvent() {
        try {
            List<Event> events = db.getAllEvents();
            if (events.isEmpty()) { System.out.println("No events to delete."); return; }
            System.out.println("--------------------------------------------------");
            for (Event e : events) System.out.println(e);
            System.out.println("--------------------------------------------------");
            System.out.print("Enter event ID to delete (0 to cancel): ");
            int id = Integer.parseInt(scanner.nextLine().trim());
            if (id == 0) return;
            db.deleteEvent(id);
            System.out.println("Event " + id + " deleted.");
        } catch (NumberFormatException ex) {
            System.err.println("Invalid ID.");
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    private void exportToIcs() {
        try {
            List<Event> events = db.getAllEvents();
            if (events.isEmpty()) { System.out.println("No events to export."); return; }
            String path = new IcsExporter().export(events);
            System.out.println("Exported " + events.size() + " event(s) to: " + path);
            System.out.println("Open this file in Apple Calendar, Outlook, or any CalDAV app.");
        } catch (Exception ex) {
            System.err.println("Export failed: " + ex.getMessage());
        }
    }

    private void syncToGoogle() {
        try {
            List<Event> events = db.getAllEvents();
            if (events.isEmpty()) { System.out.println("No events to sync."); return; }
            System.out.println("Starting Google Calendar sync for " + events.size() + " event(s)...");
            System.out.println("A browser window will open for Google authorization.");
            int synced = new GoogleCalendarSync(db).pushEvents(events);
            System.out.println("Synced " + synced + " event(s) to Google Calendar.");
        } catch (Exception ex) {
            System.err.println("Google sync failed: " + ex.getMessage());
        }
    }

    private void viewByMonth() {
        System.out.print("Enter month and year (MM.yyyy, e.g. 03.2026): ");
        String input = scanner.nextLine().trim();
        try {
            String[] parts = input.split("\\.");
            int month = Integer.parseInt(parts[0]);
            int year  = Integer.parseInt(parts[1]);
            if (month < 1 || month > 12) { System.out.println("Invalid month."); return; }
            List<Event> events = db.getEventsForMonth(year, month);
            if (events.isEmpty()) { System.out.println("No events found for " + input + "."); return; }
            System.out.println("--------------------------------------------------");
            for (Event e : events) System.out.println(e);
            System.out.println("--------------------------------------------------");
            System.out.println(events.size() + " event(s) in " + input + ".");
        } catch (Exception ex) {
            System.err.println("Invalid format or error: " + ex.getMessage());
        }
    }

    private String nullIfEmpty(String s) {
        return s.isEmpty() ? null : s;
    }
}

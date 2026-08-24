package com.calendar.db;

import com.calendar.model.Event;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:calendar.db";

    private Connection connection;

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing DB: " + e.getMessage());
        }
    }

    public void initializeSchema() throws SQLException {
        String createEvents = "CREATE TABLE IF NOT EXISTS events (" +
                "id              INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title           TEXT    NOT NULL," +
                "course_code     TEXT," +
                "instructor      TEXT," +
                "location        TEXT," +
                "start_time      TEXT    NOT NULL," +
                "end_time        TEXT    NOT NULL," +
                "recurrence_rule TEXT," +
                "source          TEXT    NOT NULL DEFAULT 'manual'," +
                "created_at      TEXT    NOT NULL DEFAULT (datetime('now'))," +
                "updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))" +
                ")";

        String createSettings = "CREATE TABLE IF NOT EXISTS settings (" +
                "key   TEXT PRIMARY KEY," +
                "value TEXT NOT NULL" +
                ")";

        String createSyncLog = "CREATE TABLE IF NOT EXISTS sync_log (" +
                "id         INTEGER PRIMARY KEY AUTOINCREMENT," +
                "event_id   INTEGER NOT NULL," +
                "platform   TEXT    NOT NULL," +
                "synced_at  TEXT    NOT NULL DEFAULT (datetime('now'))," +
                "status     TEXT    NOT NULL," +
                "FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSettings);
            stmt.execute(createEvents);
            stmt.execute(createSyncLog);
        }
    }

    public int saveEvent(Event event) throws SQLException {
        String sql = "INSERT INTO events " +
                "(title, course_code, instructor, location, start_time, end_time, recurrence_rule, source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.getTitle());
            ps.setString(2, event.getCourseCode());
            ps.setString(3, event.getInstructor());
            ps.setString(4, event.getLocation());
            ps.setString(5, event.getStartTime().toString());
            ps.setString(6, event.getEndTime().toString());
            ps.setString(7, event.getRecurrenceRule());
            ps.setString(8, event.getSource());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void updateEvent(Event event) throws SQLException {
        String sql = "UPDATE events SET title=?, course_code=?, instructor=?, location=?, " +
                "start_time=?, end_time=?, recurrence_rule=?, source=?, updated_at=datetime('now') " +
                "WHERE id=?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getTitle());
            ps.setString(2, event.getCourseCode());
            ps.setString(3, event.getInstructor());
            ps.setString(4, event.getLocation());
            ps.setString(5, event.getStartTime().toString());
            ps.setString(6, event.getEndTime().toString());
            ps.setString(7, event.getRecurrenceRule());
            ps.setString(8, event.getSource());
            ps.setInt(9, event.getId());
            ps.executeUpdate();
        }
    }

    public void deleteEvent(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM events WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<Event> getAllEvents() throws SQLException {
        List<Event> events = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM events ORDER BY start_time")) {
            while (rs.next()) events.add(mapRow(rs));
        }
        return events;
    }

    public List<Event> getEventsForWeek(LocalDateTime weekStart) throws SQLException {
        LocalDateTime weekEnd = weekStart.plusDays(7);
        String sql = "SELECT * FROM events WHERE start_time >= ? AND start_time < ? ORDER BY start_time";
        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, weekStart.toString());
            ps.setString(2, weekEnd.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapRow(rs));
            }
        }
        return events;
    }

    public List<Event> getEventsForMonth(int year, int month) throws SQLException {
        String from = String.format("%04d-%02d-01T00:00", year, month);
        String to   = String.format("%04d-%02d-01T00:00", month == 12 ? year + 1 : year, month == 12 ? 1 : month + 1);
        String sql  = "SELECT * FROM events WHERE start_time >= ? AND start_time < ? ORDER BY start_time";
        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapRow(rs));
            }
        }
        return events;
    }

    public boolean eventExists(String title, LocalDateTime startTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM events WHERE title=? AND start_time=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, startTime.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public String getSetting(String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    public void setSetting(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO settings(key,value) VALUES(?,?) " +
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public void deleteScrapedEvents() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("DELETE FROM events WHERE source='scraped'");
        }
    }

    public void logSync(int eventId, String platform, String status) throws SQLException {
        String sql = "INSERT INTO sync_log (event_id, platform, status) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, platform);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setCourseCode(rs.getString("course_code"));
        e.setInstructor(rs.getString("instructor"));
        e.setLocation(rs.getString("location"));
        e.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
        e.setEndTime(LocalDateTime.parse(rs.getString("end_time")));
        e.setRecurrenceRule(rs.getString("recurrence_rule"));
        e.setSource(rs.getString("source"));
        return e;
    }
}

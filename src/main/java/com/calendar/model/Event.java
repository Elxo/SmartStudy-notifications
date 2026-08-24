package com.calendar.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Event {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("EEE dd.MM.yyyy HH:mm");

    private int id;
    private String title;
    private String courseCode;
    private String instructor;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String recurrenceRule;
    private String source;

    public Event() {}

    public Event(String title, String courseCode, String instructor, String location,
                 LocalDateTime startTime, LocalDateTime endTime,
                 String recurrenceRule, String source) {
        this.title = title;
        this.courseCode = courseCode;
        this.instructor = instructor;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.recurrenceRule = recurrenceRule;
        this.source = source;
    }

    @Override
    public String toString() {
        return String.format("[%d] %-35s %s - %s  %s",
                id,
                title,
                startTime.format(DISPLAY_FMT),
                endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                location != null ? "@ " + location : "");
    }

    // --- Getters ---

    public int getId()               { return id; }
    public String getTitle()         { return title; }
    public String getCourseCode()    { return courseCode; }
    public String getInstructor()    { return instructor; }
    public String getLocation()      { return location; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime()   { return endTime; }
    public String getRecurrenceRule(){ return recurrenceRule; }
    public String getSource()        { return source; }

    // --- Setters ---

    public void setId(int id)                       { this.id = id; }
    public void setTitle(String title)              { this.title = title; }
    public void setCourseCode(String courseCode)    { this.courseCode = courseCode; }
    public void setInstructor(String instructor)    { this.instructor = instructor; }
    public void setLocation(String location)        { this.location = location; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime)   { this.endTime = endTime; }
    public void setRecurrenceRule(String rule)      { this.recurrenceRule = rule; }
    public void setSource(String source)            { this.source = source; }
}

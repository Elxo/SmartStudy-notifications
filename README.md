# SmartStudy — University Calendar Application

A desktop calendar application for university students. It automatically downloads your lecture schedule from the university website, lets you add personal events, and syncs everything to Google Calendar or exports to ICS format.

Built with Java 21, JavaFX 21.0.2, and SQLite. Currently supports Turiba University.

---

## Quick Start

Double-click **SmartStudy.exe** in the project folder. No terminal needed.

> **First launch only:** Maven downloads dependencies automatically. This takes 15–20 seconds. Subsequent launches are instant.

**Requirements:** Java 21+ and Maven 3.9.6 must be installed on the machine. See [DEPENDENCIES.md](DEPENDENCIES.md) for setup instructions.

---

## User Manual

### First-Time Setup

On the very first launch, the **Group Setup** dialog opens automatically — the application has no saved configuration yet.

1. The **University** field is pre-filled with *Turiba University*.
2. Click **Load group list from website** to fetch all active groups (126 groups load in a few seconds).
3. Select your group from the dropdown, or type your group code directly — for example `CSA3D1`.
4. Click **OK**. The application downloads your schedule from the previous 13 weeks through the end of the current year.

A status message in the top-right of the main window shows download progress. This takes approximately 30 seconds on first run.

---

### Navigating the Calendar

The main window shows the current month.

| Control | Action |
|---|---|
| **‹** / **›** arrows | Move one month back or forward |
| **Today** button | Jump back to the current month |
| Click any day cell | Open that day's full event list in the detail panel |

**Event chip colours:**
- **Blue** — scraped lecture from the university schedule
- **Red** — any event whose title contains "exam" or "eksāmens"
- **Green** — event you created manually

If a day has more than three events, the third chip is replaced with a **+N more** indicator. The full list is always visible in the detail panel below.

---

### Adding a Manual Event

1. Click **+ Add Event** in the top bar.
2. Fill in the dialog:
   - **Title** (required)
   - Course code, Instructor, Location (optional)
   - **Start date** — pre-filled with the selected day, format `yyyy-MM-dd`
   - **Start time** — format `HH:mm`
   - **End date** and **End time**
3. Click **OK**. The event appears immediately on the calendar with a green chip.

If the title is empty or the end time is before the start time, an error message is shown and the event is not saved.

---

### Deleting an Event

1. Click the day containing the event to open the detail panel.
2. Click the **✕** button next to the event.
3. Confirm the deletion in the dialog that appears.

The event is removed immediately from both the detail panel and the calendar grid.

---

### Exporting to ICS

Click **Export .ics** in the top bar. All events in the database are written to `university_schedule.ics` in the application folder. A confirmation dialog shows the total number of events exported and the full file path.

The ICS file can be imported into:
- **Google Calendar** — Settings → Import
- **Apple Calendar** — File → Import
- **Microsoft Outlook** — File → Open & Export → Import/Export

---

### Syncing with Google Calendar

Click **Sync Google** in the top bar.

**First use:** A browser window opens asking you to sign in to your Google account and grant access to Google Calendar. After you approve, the browser can be closed.

The application pushes all events to your primary Google Calendar. Each result is logged in the local database.

**Later uses:** The saved token is reused automatically — the browser does not open again.

### Automatic ntfy Notifications

The repository includes a GitHub Actions workflow that checks the Turiba schedule twice daily, at 05:50 and 21:50 Riga time, and sends an ntfy notification when the schedule changes.

1. Install **ntfy** from the iPhone App Store.
2. Create a long, private topic name and subscribe to it in the app.
3. In GitHub, open **Settings → Secrets and variables → Actions** and add:
    - `TURIBA_GROUP_CODE` — your group, for example `CSA3D1`
    - `NTFY_TOPIC_URL` — `https://ntfy.sh/<your-private-topic>`
4. Run **Actions → Check Turiba schedule → Run workflow** once to create the initial snapshot.

The first run only stores a baseline. Later runs notify ntfy when an event is added, removed, or changed. The workflow accounts for Latvia's UTC+2/UTC+3 daylight-saving changes and performs the scheduled check 10 minutes before 06:00 and 22:00 Riga time.

---

### Changing the University Group

Click **Change Group** in the top bar. The Group Setup dialog reopens.

Enter the new group code and click **OK**. All previously scraped events are deleted and replaced with the new group's schedule. **Manually added events are not affected.**

---

## Alternative Launch Methods

| Method | Command |
|---|---|
| Double-click launcher | `SmartStudy.exe` |
| PowerShell script | `.\run.ps1` |
| Direct Maven | `mvn javafx:run` |

---

## Project Structure

```
src/main/java/com/calendar/
├── Main.java                         Application entry point
├── model/Event.java                  Domain data model
├── db/DatabaseManager.java           All SQLite operations
├── scraper/ScheduleScraper.java      Web scraping and parsing
├── monitor/ScheduleMonitor.java      Headless change detection and ntfy alerts
├── sync/GoogleCalendarSync.java      Google Calendar API integration
├── sync/IcsExporter.java             ICS file export
├── ui/CalendarView.java              Main window
├── ui/GroupSetupDialog.java          Setup dialog
├── ui/ConsoleMenu.java               Terminal fallback menu
└── university/
    ├── University.java               Interface for university providers
    ├── TuribaUniversity.java         Turiba implementation
    └── UniversityRegistry.java       Registry of supported universities
src/main/resources/
├── logo.png                          Application logo
└── styles.css                        Dark theme stylesheet
pom.xml                               Maven build configuration
run.ps1                               PowerShell launch script
DEPENDENCIES.md                       Full dependency and setup guide
```

---

## Adding Support for Another University

The `University` interface in `com.calendar.university` defines four methods: `getName()`, `getCode()`, `fetchGroups()`, and `buildScheduleUrl()`. Implement those four methods in a new class, then add one line to the `SUPPORTED` list in `UniversityRegistry`. Nothing else needs to change.

---

## Source Code

[https://github.com/Elxo/SmartStudy](https://github.com/Elxo/SmartStudy)

---

*Course Paper 3 — DAT1080P | Turiba University IT Faculty | Emils Zvirbulis, CSA3D1 | Advisor: Jānis Pekša*

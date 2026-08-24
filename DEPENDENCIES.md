# SmartStudy – Dependencies & Requirements

## System Requirements

| Requirement | Minimum | Installed |
|---|---|---|
| OS | Windows 10 64-bit | Windows 11 |
| Java JDK | 21 | 26.0.1 (`C:\Program Files\Java\jdk-26.0.1`) |
| Apache Maven | 3.6 | 3.9.6 (`C:\tools\apache-maven-3.9.6`) |

---

## How to Run

**Option A — Double-click launcher (recommended)**
```
SmartStudy.exe
```
No terminal needed. Java and Maven paths are set automatically.

**Option B — PowerShell script**
```powershell
.\run.ps1
```

**Option C — Maven directly (requires Maven on PATH)**
```powershell
mvn javafx:run
```

---

## Maven Dependencies

These are downloaded automatically by Maven on the first build.
Cached in `%USERPROFILE%\.m2\repository` after that.

| Dependency | Version | Purpose |
|---|---|---|
| `org.openjfx:javafx-controls` | 21.0.2 | UI framework — controls (Button, Label, etc.) |
| `org.openjfx:javafx-base` | 21.0.2 | UI framework — base classes |
| `org.jsoup:jsoup` | 1.17.2 | HTML parser for schedule scraping |
| `org.xerial:sqlite-jdbc` | 3.45.1.0 | SQLite database driver |
| `org.slf4j:slf4j-nop` | 1.7.36 | Suppresses log warnings from Google API |
| `com.google.apis:google-api-services-calendar` | v3-rev20220715-2.0.0 | Google Calendar REST API client |
| `com.google.oauth-client:google-oauth-client-jetty` | 1.34.1 | OAuth 2.0 authorization flow (desktop) |
| `com.google.http-client:google-http-client-jackson2` | 1.43.3 | HTTP/JSON transport for Google API |
| `com.google.http-client:google-http-client-gson` | 1.43.3 | Gson serialization for Google API |

All dependency declarations are in [`pom.xml`](pom.xml).

---

## Optional: Google Calendar Sync

Sync requires a `credentials.json` file in the project root.

**Setup steps:**
1. Go to [https://console.cloud.google.com](https://console.cloud.google.com)
2. Create a project → Enable **Google Calendar API**
3. Go to **APIs & Services → Credentials**
4. Click **Create Credentials → OAuth client ID → Desktop app**
5. Download the JSON file and save it as `credentials.json` in `d:\StudyPaper3\`
6. On first sync, a browser window opens for Google account authorization
7. The token is saved to `tokens\StoredCredential` and reused automatically

> Without `credentials.json` the app runs fully — only the "Sync Google" button will show an error.

---

## Runtime Files (auto-generated, not in source control)

| File / Folder | Created by | Purpose |
|---|---|---|
| `calendar.db` | App on first launch | SQLite database — events, settings, sync log |
| `tokens\StoredCredential` | Google OAuth flow | Saved authorization token |
| `university_schedule.ics` | "Export .ics" button | iCalendar export file |
| `target\` | Maven build | Compiled `.class` files and resources |

---

## Build Tool Configuration

| File | Purpose |
|---|---|
| [`pom.xml`](pom.xml) | Maven project descriptor — dependencies, Java 21 target, JavaFX plugin |
| [`run.ps1`](run.ps1) | Windows launch script — sets `JAVA_HOME`, `PATH`, UTF-8 encoding |
| [`SmartStudy.exe`](SmartStudy.exe) | One-click launcher — wraps `mvn javafx:run`, no terminal needed |

---

## Checking Your Installation

Run this in PowerShell to verify everything is in place:

```powershell
java -version
mvn -version
Test-Path "d:\StudyPaper3\calendar.db"       # true after first launch
Test-Path "d:\StudyPaper3\credentials.json"   # true if Google sync is configured
```

Expected output:
```
java version "26.0.1" ...
Apache Maven 3.9.6 ...
```

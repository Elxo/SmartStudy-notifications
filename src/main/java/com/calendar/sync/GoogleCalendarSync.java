package com.calendar.sync;

import com.calendar.db.DatabaseManager;
import com.calendar.model.Event;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

public class GoogleCalendarSync {

    private static final String APPLICATION_NAME  = "University Calendar";
    private static final String CREDENTIALS_FILE  = "credentials.json";
    private static final String TOKENS_DIRECTORY  = "tokens";
    private static final List<String> SCOPES =
            Collections.singletonList(CalendarScopes.CALENDAR);

    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final DatabaseManager db;

    public GoogleCalendarSync(DatabaseManager db) {
        this.db = db;
    }

    public int pushEvents(List<Event> events) throws Exception {
        com.google.api.services.calendar.Calendar service = buildService();
        int count = 0;

        for (Event event : events) {
            try {
                com.google.api.services.calendar.model.Event gEvent = toGoogleEvent(event);
                service.events().insert("primary", gEvent).execute();
                db.logSync(event.getId(), "google", "success");
                count++;
            } catch (Exception e) {
                db.logSync(event.getId(), "google", "error: " + e.getMessage());
                System.err.println("Failed to sync event [" + event.getId() + "]: " + e.getMessage());
            }
        }
        return count;
    }

    private com.google.api.services.calendar.Calendar buildService() throws Exception {
        File credentialsFile = new File(CREDENTIALS_FILE);
        if (!credentialsFile.exists()) {
            throw new Exception(
                "credentials.json not found.\n" +
                "Setup steps:\n" +
                "  1. Go to https://console.cloud.google.com\n" +
                "  2. Create a project → Enable Google Calendar API\n" +
                "  3. Create OAuth 2.0 Desktop credentials\n" +
                "  4. Download credentials.json → place it in the project root folder"
            );
        }

        GoogleClientSecrets secrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                new InputStreamReader(new FileInputStream(credentialsFile))
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                secrets,
                SCOPES
        )
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY)))
                .setAccessType("offline")
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()
        ).authorize("user");

        return new com.google.api.services.calendar.Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private com.google.api.services.calendar.model.Event toGoogleEvent(Event event) {
        com.google.api.services.calendar.model.Event gEvent =
                new com.google.api.services.calendar.model.Event();

        gEvent.setSummary(event.getTitle());

        if (event.getLocation() != null)
            gEvent.setLocation(event.getLocation());

        StringBuilder desc = new StringBuilder();
        if (event.getCourseCode() != null) desc.append("Course: ").append(event.getCourseCode()).append("\n");
        if (event.getInstructor() != null) desc.append("Instructor: ").append(event.getInstructor());
        if (desc.length() > 0) gEvent.setDescription(desc.toString());

        ZoneId zone = ZoneId.systemDefault();
        DateTime start = new DateTime(event.getStartTime().atZone(zone).toInstant().toEpochMilli());
        DateTime end   = new DateTime(event.getEndTime().atZone(zone).toInstant().toEpochMilli());

        gEvent.setStart(new EventDateTime().setDateTime(start));
        gEvent.setEnd(new EventDateTime().setDateTime(end));

        return gEvent;
    }
}

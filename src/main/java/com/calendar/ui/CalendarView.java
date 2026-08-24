package com.calendar.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.calendar.db.DatabaseManager;
import com.calendar.model.Event;
import com.calendar.scraper.ScheduleScraper;
import com.calendar.sync.GoogleCalendarSync;
import com.calendar.sync.IcsExporter;
import com.calendar.university.University;
import com.calendar.university.UniversityRegistry;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class CalendarView extends BorderPane {

    private static final String[] DAY_NAMES    = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");

    private final DatabaseManager db;
    private final Stage stage;

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate selectedDate  = LocalDate.now();
    private Map<LocalDate, List<Event>> eventsByDay = new HashMap<>();

    private Label    monthLabel;
    private Label    groupBadge;
    private Label    statusLabel;
    private GridPane calendarGrid;
    private VBox     detailContent;

    public CalendarView(DatabaseManager db, Stage stage) {
        this.db    = db;
        this.stage = stage;
        buildLayout();
        loadAndRefresh();
    }

    public void checkInitialSetup() {
        try {
            if (db.getSetting("university_code") == null || db.getSetting("group_code") == null) {
                GroupSetupDialog dlg = new GroupSetupDialog(db);
                dlg.initOwner(stage);
                dlg.showAndWait().filter(Boolean::booleanValue).ifPresent(ok -> triggerScrape());
            }
        } catch (Exception e) {
            alert("Error", e.getMessage());
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildLayout() {
        setTop(buildHeader());
        setCenter(buildCalendarArea());
        setBottom(buildDetailPanel());
    }

    private VBox buildHeader() {
        // ── Row 1: App name + group badge ────────────────────────────────────
        Node logo = buildLogo();

        Label appTitle = new Label("University Calendar");
        appTitle.getStyleClass().add("app-title");

        groupBadge = new Label(groupText());
        groupBadge.getStyleClass().add("group-badge");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox titleRow = new HBox(14, logo, appTitle, gap, groupBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("app-header");

        // ── Row 2: Navigation + actions ──────────────────────────────────────
        Button prevBtn  = navBtn("‹");
        Button nextBtn  = navBtn("›");
        Button todayBtn = styledBtn("Today", "btn-today");

        monthLabel = new Label(currentMonth.format(MONTH_FMT));
        monthLabel.getStyleClass().add("month-label");
        monthLabel.setMinWidth(180);
        monthLabel.setAlignment(Pos.CENTER_LEFT);

        prevBtn.setOnAction(e  -> navigate(-1));
        nextBtn.setOnAction(e  -> navigate(1));
        todayBtn.setOnAction(e -> goToToday());

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        Button addBtn     = styledBtn("+ Add Event", "btn-outline");
        Button exportBtn  = styledBtn("Export .ics", "btn-outline");
        Button syncBtn    = styledBtn("Sync Google", "btn-primary");
        Button groupBtn   = styledBtn("Change Group", "btn-outline");

        addBtn.setOnAction(e    -> showAddEventDialog());
        exportBtn.setOnAction(e -> exportIcs());
        syncBtn.setOnAction(e   -> syncGoogle());
        groupBtn.setOnAction(e  -> changeGroup());

        Region navGap = new Region();
        HBox.setHgrow(navGap, Priority.ALWAYS);

        HBox navRow = new HBox(6,
                todayBtn, prevBtn, monthLabel, nextBtn,
                navGap, statusLabel,
                addBtn, exportBtn, syncBtn, groupBtn);
        navRow.setAlignment(Pos.CENTER_LEFT);
        navRow.getStyleClass().add("nav-bar");

        return new VBox(titleRow, navRow);
    }

    private VBox buildCalendarArea() {
        // Day name header
        GridPane headerGrid = new GridPane();
        addCols(headerGrid, 7);
        headerGrid.getStyleClass().add("day-header-row");

        for (int i = 0; i < 7; i++) {
            Label lbl = new Label(DAY_NAMES[i]);
            lbl.getStyleClass().addAll("day-header", i >= 5 ? "day-header-weekend" : "");
            lbl.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(lbl, Priority.ALWAYS);
            headerGrid.add(lbl, i, 0);
        }

        // Calendar grid
        calendarGrid = new GridPane();
        calendarGrid.getStyleClass().add("calendar-grid");
        addCols(calendarGrid, 7);

        ScrollPane scroll = new ScrollPane(calendarGrid);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox area = new VBox(headerGrid, scroll);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private VBox buildDetailPanel() {
        detailContent = new VBox(6);
        detailContent.getStyleClass().add("detail-panel");
        Label hint = new Label("Click a day to see its schedule.");
        hint.getStyleClass().add("detail-hint");
        detailContent.getChildren().add(hint);

        ScrollPane scroll = new ScrollPane(detailContent);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(140);
        scroll.setMaxHeight(140);

        VBox wrapper = new VBox(scroll);
        wrapper.setStyle("-fx-background-color: #14141E;");
        return wrapper;
    }

    // ── Calendar rendering ────────────────────────────────────────────────────

    private void loadAndRefresh() {
        try {
            List<Event> all = db.getAllEvents();
            eventsByDay = new HashMap<>();
            for (Event ev : all) {
                LocalDate d      = ev.getStartTime().toLocalDate();
                LocalDate endDay = ev.getEndTime().toLocalDate();
                // Add event chip to every calendar day it spans; skip end day if it ends exactly at midnight
                while (!d.isAfter(endDay)) {
                    if (d.equals(endDay) && ev.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT)) break;
                    eventsByDay.computeIfAbsent(d, k -> new ArrayList<>()).add(ev);
                    if (d.equals(endDay)) break;
                    d = d.plusDays(1);
                }
            }
            refreshGrid();
            monthLabel.setText(currentMonth.format(MONTH_FMT));
            groupBadge.setText(groupText());
        } catch (Exception ex) {
            alert("Error", "Failed to load events: " + ex.getMessage());
        }
    }

    private void refreshGrid() {
        calendarGrid.getChildren().clear();
        calendarGrid.getRowConstraints().clear();

        LocalDate firstDay    = currentMonth.atDay(1);
        int startCol          = firstDay.getDayOfWeek().getValue() - 1;
        int daysInMonth       = currentMonth.lengthOfMonth();
        int numRows           = (startCol + daysInMonth + 6) / 7;

        for (int r = 0; r < numRows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(95);
            rc.setVgrow(Priority.ALWAYS);
            calendarGrid.getRowConstraints().add(rc);
        }

        int col = 0;
        int row = 0;

        for (int blank = 0; blank < startCol; blank++) {
            calendarGrid.add(blankCell(), blank, 0);
            col++;
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date     = currentMonth.atDay(day);
            List<Event> events = eventsByDay.getOrDefault(date, List.of());
            calendarGrid.add(dayCell(date, events), col, row);
            col++;
            if (col == 7) { col = 0; row++; }
        }

        while (col > 0 && col < 7) {
            calendarGrid.add(blankCell(), col++, row);
        }
    }

    private Pane blankCell() {
        Pane p = new Pane();
        p.getStyleClass().add("day-cell-blank");
        return p;
    }

    private VBox dayCell(LocalDate date, List<Event> events) {
        boolean isToday    = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);
        boolean isWeekend  = date.getDayOfWeek().getValue() >= 6;

        VBox cell = new VBox(3);
        cell.getStyleClass().add("day-cell");
        if (isSelected)     cell.getStyleClass().add("day-cell-selected");
        else if (isWeekend) cell.getStyleClass().add("day-cell-weekend");

        // Day number — circle for today
        Label num = new Label(String.valueOf(date.getDayOfMonth()));
        if (isToday) {
            num.getStyleClass().add("day-num-today");
        } else {
            num.getStyleClass().add("day-num");
        }

        HBox numRow = new HBox(num);
        numRow.setAlignment(Pos.TOP_RIGHT);
        cell.getChildren().add(numRow);

        // Event chips
        int shown = 0;
        for (Event ev : events) {
            if (shown == 3) {
                Label more = new Label("+" + (events.size() - 3) + " more");
                more.getStyleClass().addAll("chip", "chip-more");
                cell.getChildren().add(more);
                break;
            }
            cell.getChildren().add(chip(ev, date));
            shown++;
        }

        cell.setOnMouseClicked(e -> onDayClicked(date));
        return cell;
    }

    private Label chip(Event ev, LocalDate cellDate) {
        boolean isExam   = ev.getTitle().toLowerCase().contains("exam")
                        || ev.getTitle().contains("eksāmens");
        boolean isManual = "manual".equals(ev.getSource());

        LocalDate startDay = ev.getStartTime().toLocalDate();
        LocalDate endDay   = ev.getEndTime().toLocalDate();
        boolean isStart    = cellDate.equals(startDay);
        boolean isEnd      = cellDate.equals(endDay);

        String prefix = isStart ? "" : (isEnd ? "← " : "↔ ");
        String chipText = prefix + truncate(ev.getTitle(), isStart ? 16 : 14)
                        + (isStart && !isEnd ? " →" : "");

        Label lbl = new Label(chipText);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.getStyleClass().add("chip");
        lbl.getStyleClass().add(isExam ? "chip-exam" : isManual ? "chip-manual" : "chip-lecture");
        return lbl;
    }

    private void onDayClicked(LocalDate date) {
        selectedDate = date;
        refreshGrid();
        showDetails(date);
    }

    private void showDetails(LocalDate date) {
        detailContent.getChildren().clear();

        Label heading = new Label(date.format(DATE_FMT));
        heading.getStyleClass().add("detail-date-label");
        detailContent.getChildren().add(heading);

        List<Event> events = eventsByDay.getOrDefault(date, List.of());
        if (events.isEmpty()) {
            Label none = new Label("No events.");
            none.getStyleClass().add("detail-hint");
            detailContent.getChildren().add(none);
            return;
        }

        for (Event ev : events) {
            boolean isExam      = ev.getTitle().toLowerCase().contains("exam")
                               || ev.getTitle().contains("eksāmens");
            boolean isManual    = "manual".equals(ev.getSource());
            boolean isMultiDay  = !ev.getStartTime().toLocalDate().equals(ev.getEndTime().toLocalDate());

            String time;
            if (isMultiDay) {
                DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd MMM");
                time = ev.getStartTime().format(shortDate) + " " + ev.getStartTime().format(TIME_FMT)
                     + " – "
                     + ev.getEndTime().format(shortDate) + " " + ev.getEndTime().format(TIME_FMT);
            } else {
                time = ev.getStartTime().format(TIME_FMT) + " – " + ev.getEndTime().format(TIME_FMT);
            }
            String room = ev.getLocation() != null ? "  ·  " + ev.getLocation() : "";

            Label line = new Label("  " + time + "   " + ev.getTitle() + room);
            line.getStyleClass().add(isExam ? "detail-exam" : isManual ? "detail-manual" : "detail-line");
            line.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(line, Priority.ALWAYS);

            Button delBtn = new Button("✕");
            delBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #6060808;" +
                "-fx-font-size: 11px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 4 0 4;"
            );
            delBtn.setOnMouseEntered(e -> delBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #E87878;" +
                "-fx-font-size: 11px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 4 0 4;"
            ));
            delBtn.setOnMouseExited(e -> delBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #6060808;" +
                "-fx-font-size: 11px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 4 0 4;"
            ));

            final int evId = ev.getId();
            final String evTitle = ev.getTitle();
            delBtn.setOnAction(e -> confirmAndDelete(evId, evTitle, date));

            HBox row = new HBox(line, delBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            detailContent.getChildren().add(row);
        }
    }

    private void confirmAndDelete(int eventId, String eventTitle, LocalDate returnDate) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Event");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete \"" + eventTitle + "\"?");
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    db.deleteEvent(eventId);
                    loadAndRefresh();
                    showDetails(returnDate);
                    setStatus("Event deleted.");
                } catch (Exception ex) {
                    alert("Error", "Could not delete event: " + ex.getMessage());
                }
            }
        });
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void showAddEventDialog() {
        Dialog<Event> dlg = new Dialog<>();
        dlg.setTitle("Add Event");
        dlg.setHeaderText("New event");
        dlg.initOwner(stage);
        dlg.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── Fields ────────────────────────────────────────────────────────────
        TextField titleField      = field("e.g. Mathematics lecture");
        TextField courseField     = field("e.g. MAT101");
        TextField instructorField = field("e.g. Dr. Smith");
        TextField locationField   = field("e.g. Room 214");
        TextField startDateField  = field("yyyy-MM-dd");
        TextField startTimeField  = field("09:00");
        TextField endDateField    = field("yyyy-MM-dd");
        TextField endTimeField    = field("10:30");

        // Pre-fill start date from selected day; end date follows automatically
        startDateField.setText(selectedDate.toString());
        endDateField.setText(selectedDate.toString());

        // Keep end date in sync with start date unless user has manually changed it
        final boolean[] endDateEdited = {false};
        endDateField.textProperty().addListener((o, ov, nv) -> {
            if (!nv.equals(startDateField.getText())) endDateEdited[0] = true;
        });
        startDateField.textProperty().addListener((o, ov, nv) -> {
            if (!endDateEdited[0]) endDateField.setText(nv);
        });

        Label errLabel = new Label();
        errLabel.getStyleClass().add("setup-status");
        errLabel.setStyle("-fx-text-fill: #E87878;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 24, 10, 24));
        grid.setStyle("-fx-background-color: #1E1E2C;");
        ColumnConstraints lc = new ColumnConstraints(110);
        ColumnConstraints fc = new ColumnConstraints();
        fc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(lc, fc);

        grid.add(new Label("Title *"),       0, 0); grid.add(titleField,      1, 0);
        grid.add(new Label("Course code"),   0, 1); grid.add(courseField,     1, 1);
        grid.add(new Label("Instructor"),    0, 2); grid.add(instructorField, 1, 2);
        grid.add(new Label("Location"),      0, 3); grid.add(locationField,   1, 3);
        grid.add(new Label("Start date *"),  0, 4); grid.add(startDateField,  1, 4);
        grid.add(new Label("Start time *"),  0, 5); grid.add(startTimeField,  1, 5);
        grid.add(new Label("End date *"),    0, 6); grid.add(endDateField,    1, 6);
        grid.add(new Label("End time *"),    0, 7); grid.add(endTimeField,    1, 7);
        grid.add(errLabel,                   0, 8, 2, 1);

        // Style labels
        grid.getChildren().stream()
                .filter(n -> n instanceof Label && n != errLabel)
                .forEach(n -> ((Label) n).setStyle("-fx-text-fill: #C0C0DC;"));

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().setPrefWidth(420);

        Button okBtn     = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        Button cancelBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("cancel-button");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String err = validateEventFields(
                    titleField.getText(),
                    startDateField.getText(), startTimeField.getText(),
                    endDateField.getText(),   endTimeField.getText());
            if (err != null) {
                errLabel.setText("⚠  " + err);
                ev.consume();
            }
        });

        dlg.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                LocalDateTime start = LocalDateTime.of(
                        LocalDate.parse(startDateField.getText().trim()),
                        LocalTime.parse(normalizeTime(startTimeField.getText())));
                LocalDateTime end = LocalDateTime.of(
                        LocalDate.parse(endDateField.getText().trim()),
                        LocalTime.parse(normalizeTime(endTimeField.getText())));
                return new Event(
                        titleField.getText().trim(),
                        nullIfBlank(courseField.getText()),
                        nullIfBlank(instructorField.getText()),
                        nullIfBlank(locationField.getText()),
                        start, end,
                        null, "manual"
                );
            } catch (Exception e) {
                return null;
            }
        });

        dlg.showAndWait().ifPresent(event -> {
            if (event == null) return;
            try {
                db.saveEvent(event);
                currentMonth = YearMonth.from(event.getStartTime());
                selectedDate = event.getStartTime().toLocalDate();
                loadAndRefresh();
                showDetails(selectedDate);
                setStatus("Event \"" + event.getTitle() + "\" added.");
            } catch (Exception ex) {
                alert("Error", "Could not save event: " + ex.getMessage());
            }
        });
    }

    /** Pads single-digit hours so "9:00" becomes "09:00" before LocalTime.parse(). */
    private static String normalizeTime(String t) {
        if (t == null) return t;
        t = t.trim();
        // If format is H:mm (one digit hour), pad to HH:mm
        if (t.matches("^\\d:\\d{2}$")) t = "0" + t;
        return t;
    }

    private String validateEventFields(String title,
                                       String startDate, String startTime,
                                       String endDate,   String endTime) {
        if (title == null || title.isBlank()) return "Title is required.";
        LocalDate sd, ed;
        LocalTime st, et;
        try { sd = LocalDate.parse(startDate.trim()); } catch (Exception e) { return "Start date must be yyyy-MM-dd (e.g. 2026-06-15)."; }
        try { st = LocalTime.parse(normalizeTime(startTime)); } catch (Exception e) { return "Start time must be HH:mm (e.g. 09:00)."; }
        try { ed = LocalDate.parse(endDate.trim());   } catch (Exception e) { return "End date must be yyyy-MM-dd (e.g. 2026-06-16)."; }
        try { et = LocalTime.parse(normalizeTime(endTime));   } catch (Exception e) { return "End time must be HH:mm (e.g. 01:00)."; }
        LocalDateTime start = LocalDateTime.of(sd, st);
        LocalDateTime end   = LocalDateTime.of(ed, et);
        if (!end.isAfter(start)) return "End must be after start (use next-day end date for overnight events).";
        return null;
    }

    private static TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("text-field");
        return tf;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void exportIcs() {
        try {
            List<Event> events = db.getAllEvents();
            if (events.isEmpty()) { alert("Export", "No events to export."); return; }
            alert("Exported", events.size() + " event(s) saved to:\n" + new IcsExporter().export(events));
        } catch (Exception ex) {
            alert("Error", ex.getMessage());
        }
    }

    private void syncGoogle() {
        setStatus("Syncing to Google Calendar…");
        Task<Integer> t = new Task<>() {
            @Override protected Integer call() throws Exception {
                return new GoogleCalendarSync(db).pushEvents(db.getAllEvents());
            }
        };
        t.setOnSucceeded(e -> setStatus("Synced " + t.getValue() + " event(s)."));
        t.setOnFailed(e -> {
            setStatus("Sync failed.");
            Platform.runLater(() -> alert("Sync Error", t.getException().getMessage()));
        });
        new Thread(t, "google-sync").start();
    }

    private void changeGroup() {
        GroupSetupDialog dlg = new GroupSetupDialog(db);
        dlg.initOwner(stage);
        dlg.showAndWait().filter(Boolean::booleanValue).ifPresent(ok -> triggerScrape());
    }

    private void triggerScrape() {
        setStatus("Scraping schedule… (~30 s)");
        groupBadge.setText(groupText());
        Task<Integer> t = new Task<>() {
            @Override protected Integer call() throws Exception {
                String uCode = db.getSetting("university_code");
                String group = db.getSetting("group_code");
                University u = UniversityRegistry.getByCode(uCode);
                db.deleteScrapedEvents();
                return new ScheduleScraper(db, u, group).scrapeAndSave();
            }
        };
        t.setOnSucceeded(e -> { setStatus("Loaded " + t.getValue() + " events."); loadAndRefresh(); });
        t.setOnFailed(e  -> setStatus("Scrape failed."));
        new Thread(t, "scraper").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void navigate(int months) {
        currentMonth = currentMonth.plusMonths(months);
        loadAndRefresh();
    }

    private void goToToday() {
        currentMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        loadAndRefresh();
    }

    private void setStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private String groupText() {
        try {
            String u = db.getSetting("university_code");
            String g = db.getSetting("group_code");
            if (u != null && g != null) return u + "  |  " + g;
        } catch (Exception ignored) {}
        return "Not configured";
    }

    private static Node buildLogo() {
        try (var is = CalendarView.class.getResourceAsStream("/logo.png")) {
            if (is != null) {
                ImageView iv = new ImageView(new Image(is));
                iv.setFitHeight(42);
                iv.setFitWidth(42);
                iv.setPreserveRatio(true);
                return iv;
            }
        } catch (Exception ignored) {}

        // Programmatic fallback: dark circle with golden "S?"
        Circle bg = new Circle(21);
        bg.setFill(Color.web("#0A0A12"));
        bg.setStroke(Color.web("#C8A415"));
        bg.setStrokeWidth(2);

        Label txt = new Label("S?");
        txt.setStyle("-fx-text-fill: #C8A415; -fx-font-size: 14px; -fx-font-weight: bold;");

        StackPane pane = new StackPane(bg, txt);
        pane.setMinSize(42, 42);
        pane.setMaxSize(42, 42);
        return pane;
    }

    private static void addCols(GridPane grid, int n) {
        for (int i = 0; i < n; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / n);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static Button navBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-nav");
        return b;
    }

    private static Button styledBtn(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        return b;
    }

    private void alert(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.initOwner(stage);
            a.showAndWait();
        });
    }
}
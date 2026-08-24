package com.calendar;

import com.calendar.db.DatabaseManager;
import com.calendar.ui.CalendarView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Main extends Application {

    private DatabaseManager db;

    @Override
    public void start(Stage stage) {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        try {
            db = new DatabaseManager();
            db.connect();
            db.initializeSchema();
        } catch (Exception e) {
            System.err.println("DB startup error: " + e.getMessage());
            Platform.exit();
            return;
        }

        CalendarView view = new CalendarView(db, stage);
        Scene scene = new Scene(view, 1050, 720);
        scene.getStylesheets().add(
                Objects.requireNonNull(Main.class.getResource("/styles.css")).toExternalForm());

        stage.setTitle("University Calendar");
        stage.setMinWidth(750);
        stage.setMinHeight(550);
        stage.setScene(scene);

        // Window / taskbar icon
        try (var is = Main.class.getResourceAsStream("/logo.png")) {
            if (is != null) stage.getIcons().add(new Image(is));
        } catch (Exception ignored) {}

        stage.show();

        // Check group config after window is visible
        Platform.runLater(view::checkInitialSetup);
    }

    @Override
    public void stop() {
        if (db != null) db.disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

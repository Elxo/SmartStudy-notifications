package com.calendar.ui;

import com.calendar.db.DatabaseManager;
import com.calendar.university.University;
import com.calendar.university.UniversityRegistry;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Objects;

public class GroupSetupDialog extends Dialog<Boolean> {

    private final DatabaseManager db;
    private final ComboBox<String> universityCombo = new ComboBox<>();
    private final ComboBox<String> groupCombo      = new ComboBox<>();
    private final Label            statusLabel      = new Label();

    public GroupSetupDialog(DatabaseManager db) {
        this.db = db;

        // Apply the same dark stylesheet
        getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());

        setTitle("University Calendar — Setup");
        setHeaderText("Select your university and student group");

        buildContent();
        buildButtons();
    }

    private void buildContent() {
        // University selector
        UniversityRegistry.getAll().forEach(u -> universityCombo.getItems().add(u.getName()));
        universityCombo.getSelectionModel().selectFirst();
        universityCombo.setMaxWidth(Double.MAX_VALUE);

        // Group combo — editable so user can type directly
        groupCombo.setEditable(true);
        groupCombo.setPromptText("e.g. CSA3D1");
        groupCombo.setMaxWidth(Double.MAX_VALUE);

        // Pre-fill saved value if available
        try {
            String saved = db.getSetting("group_code");
            if (saved != null) groupCombo.getEditor().setText(saved);
        } catch (Exception ignored) {}

        Button loadBtn = new Button("Load group list from website");
        loadBtn.getStyleClass().add("btn-outline");
        loadBtn.setMaxWidth(Double.MAX_VALUE);
        loadBtn.setOnAction(e -> loadGroups(loadBtn));

        statusLabel.getStyleClass().add("setup-status");

        // Labels
        Label uniLabel   = new Label("University");
        Label groupLabel = new Label("Group code");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(22, 24, 10, 24));
        grid.setStyle("-fx-background-color: #1E1E2C;");

        ColumnConstraints labelCol = new ColumnConstraints(100);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        grid.add(uniLabel,         0, 0);
        grid.add(universityCombo,  1, 0);
        grid.add(groupLabel,       0, 1);
        grid.add(groupCombo,       1, 1);
        grid.add(loadBtn,          0, 2, 2, 1);
        grid.add(statusLabel,      0, 3, 2, 1);

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(420);
    }

    private void buildButtons() {
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style cancel button differently
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("cancel-button");

        // Prevent closing with empty group code
        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (groupCode().isEmpty()) {
                statusLabel.setText("⚠  Please enter a group code.");
                event.consume();
            }
        });

        setResultConverter(btn -> btn == ButtonType.OK && save());
    }

    private void loadGroups(Button loadBtn) {
        statusLabel.setText("Fetching groups from website…");
        loadBtn.setDisable(true);
        University u = selectedUniversity();

        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception { return u.fetchGroups(); }
        };
        task.setOnSucceeded(e -> {
            groupCombo.getItems().setAll(task.getValue());
            statusLabel.setText("✓  " + task.getValue().size() + " groups loaded — select or type yours below.");
            loadBtn.setDisable(false);
        });
        task.setOnFailed(e -> {
            statusLabel.setText("✗  Could not fetch groups: " + task.getException().getMessage());
            loadBtn.setDisable(false);
        });
        new Thread(task, "group-loader").start();
    }

    private boolean save() {
        try {
            db.setSetting("university_code", selectedUniversity().getCode());
            db.setSetting("group_code", groupCode());
            return true;
        } catch (Exception ex) {
            statusLabel.setText("✗  Save failed: " + ex.getMessage());
            return false;
        }
    }

    private University selectedUniversity() {
        int idx = Math.max(universityCombo.getSelectionModel().getSelectedIndex(), 0);
        return UniversityRegistry.getAll().get(idx);
    }

    private String groupCode() {
        return groupCombo.getEditor().getText().trim().toUpperCase();
    }
}

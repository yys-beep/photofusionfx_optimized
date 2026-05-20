package com.photofusionfx.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class Dialogs {
    private Dialogs() {
    }

    // --- HELPER METHOD TO APPLY DARK THEME ---
    private static void applyTheme(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();
        try {
            java.net.URL cssURL = Dialogs.class.getResource("/styles/app.css");
            if (cssURL != null) {
                dialogPane.getStylesheets().add(cssURL.toExternalForm());
            } else {
                System.out.println("WARNING: Could not find /styles/app.css for Dialogs.");
            }
        } catch (Exception e) {
            System.err.println("Error loading stylesheet for dialog: " + e.getMessage());
        }
        
        // Apply the surface-panel class to make the background dark
        dialogPane.getStyleClass().add("surface-panel");
    }

    public static void info(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        applyTheme(alert); // Apply dark theme
        
        alert.showAndWait();
    }

    public static void warn(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        applyTheme(alert); // Apply dark theme
        
        alert.showAndWait();
    }

    public static void error(String title, String message, Throwable throwable) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        applyTheme(alert); // Apply dark theme

        if (throwable != null) {
            TextArea area = new TextArea(stackTrace(throwable));
            area.setEditable(false);
            area.setWrapText(false);
            VBox box = new VBox(area);
            VBox.setVgrow(area, Priority.ALWAYS);
            box.setMaxWidth(Double.MAX_VALUE);
            box.setMaxHeight(Double.MAX_VALUE);
            alert.getDialogPane().setExpandableContent(box);
        }
        alert.showAndWait();
    }

    private static String stackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("    at ").append(element).append("\n");
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            sb.append("Caused by: ").append(stackTrace(cause));
        }
        return sb.toString();
    }
}
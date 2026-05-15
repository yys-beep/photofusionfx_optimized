package com.photofusionfx.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class Dialogs {
    private Dialogs() {
    }

    public static void info(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void warn(String title, String message) {
        Alert alert = new Alert(AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void error(String title, String message, Throwable throwable) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);

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

package com.photofusionfx.ui;

//SharePane handles email sharing with real attachment and whatsapp file copy

import com.photofusionfx.AppContext;
import com.photofusionfx.model.EmailConfig;
import com.photofusionfx.util.Dialogs;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SharePane extends VBox {
    private final AppContext context;
    private final Label selectedFileLabel = new Label("Selected file: none");
    private final TextField smtpHostField = new TextField();
    private final Spinner<Integer> smtpPortSpinner = new Spinner<>();
    private final CheckBox startTlsCheckBox = new CheckBox("Use STARTTLS");
    private final CheckBox sslCheckBox = new CheckBox("Use SSL");
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField fromField = new TextField();
    private final TextField toField = new TextField();
    private final TextField subjectField = new TextField();
    private final TextArea bodyArea = new TextArea();
    private final ProgressIndicator sendingIndicator = new ProgressIndicator();

    private File selectedFile;

    public SharePane(AppContext context) {
        this.context = context;
        setSpacing(12);
        setPadding(new Insets(12));

        smtpPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 587));
        smtpPortSpinner.setEditable(true);
        bodyArea.setPrefRowCount(8);
        bodyArea.setWrapText(true);
        bodyArea.setPromptText("Email body...");
        sendingIndicator.setVisible(false);
        sendingIndicator.setPrefSize(24, 24);

        getChildren().addAll(buildFileSection(), buildMailSection(), buildShareNotes());
        loadSavedConfig();

        context.latestExportFileProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                selectedFile = newValue;
                selectedFileLabel.setText("Selected file: " + newValue.getAbsolutePath());
            }
        });
        if (context.getLatestExportFile() != null) {
            selectedFile = context.getLatestExportFile();
            selectedFileLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
        }
    }

    private VBox buildFileSection() {
        Button useSelectedPhotoButton = new Button("Use Current Selected Photo");
        useSelectedPhotoButton.setOnAction(e -> {
            if (context.getSelectedPhoto() == null) {
                Dialogs.warn("No Photo", "Select a photo in Repository first.");
                return;
            }
            selectedFile = new File(context.getSelectedPhoto().getFilePath());
            selectedFileLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
        });

        Button chooseFileButton = new Button("Browse File");
        chooseFileButton.setOnAction(e -> browseForFile());

        Button openWhatsAppButton = new Button("Open WhatsApp + Copy Actual File");
        openWhatsAppButton.setOnAction(e -> openWhatsAppHelper());
        openWhatsAppButton.getStyleClass().add("primary-button");

        HBox row = new HBox(10, selectedFileLabel, spacer(), useSelectedPhotoButton, chooseFileButton, openWhatsAppButton);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selectedFileLabel, Priority.ALWAYS);

        VBox box = new VBox(8, new Label("Attachment / export file"), row);
        box.getStyleClass().add("section-card");
        return box;
    }

    private VBox buildMailSection() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("SMTP Host"), 0, 0);
        grid.add(smtpHostField, 1, 0);
        grid.add(new Label("Port"), 2, 0);
        grid.add(smtpPortSpinner, 3, 0);
        grid.add(startTlsCheckBox, 1, 1);
        grid.add(sslCheckBox, 2, 1);
        grid.add(new Label("Username"), 0, 2);
        grid.add(usernameField, 1, 2, 3, 1);
        grid.add(new Label("Password / App Password"), 0, 3);
        grid.add(passwordField, 1, 3, 3, 1);
        grid.add(new Label("From"), 0, 4);
        grid.add(fromField, 1, 4, 3, 1);
        grid.add(new Label("To"), 0, 5);
        grid.add(toField, 1, 5, 3, 1);
        grid.add(new Label("Subject"), 0, 6);
        grid.add(subjectField, 1, 6, 3, 1);
        grid.add(new Label("Body"), 0, 7);
        grid.add(bodyArea, 1, 7, 3, 1);
        GridPane.setHgrow(smtpHostField, Priority.ALWAYS);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);
        GridPane.setHgrow(fromField, Priority.ALWAYS);
        GridPane.setHgrow(toField, Priority.ALWAYS);
        GridPane.setHgrow(subjectField, Priority.ALWAYS);
        GridPane.setHgrow(bodyArea, Priority.ALWAYS);

        Button saveSettingsButton = new Button("Save Mail Settings");
        saveSettingsButton.setOnAction(e -> saveConfig());
        saveSettingsButton.getStyleClass().add("success-button");

        Button sendButton = new Button("Send Email with Attachment");
        sendButton.setOnAction(e -> sendEmail());
        sendButton.getStyleClass().add("primary-button");

        HBox buttons = new HBox(10, saveSettingsButton, sendButton, sendingIndicator);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, new Label("Email integration"), grid, buttons);
        box.getStyleClass().add("section-card");
        return box;
    }

    private VBox buildShareNotes() {
        Label label = new Label(
                "Email sends the selected image or generated video as a real MIME attachment. WhatsApp desktop/web does not expose a reliable public Java API for silently attaching local files, so this app copies the actual selected File object to the system clipboard and opens WhatsApp with the caption. Paste in the chat to attach the real file, not a path string."
        );
        label.setWrapText(true);
        VBox box = new VBox(6, new Label("Sharing notes"), label);
        box.getStyleClass().add("section-card");
        return box;
    }

    private void browseForFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose File to Share");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp", "*.mp4", "*.mov"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            selectedFile = file;
            selectedFileLabel.setText("Selected file: " + file.getAbsolutePath());
        }
    }

    private void loadSavedConfig() {
        try {
            EmailConfig config = context.getEmailService().loadConfig();
            smtpHostField.setText(config.getSmtpHost());
            smtpPortSpinner.getValueFactory().setValue(config.getSmtpPort());
            startTlsCheckBox.setSelected(config.isStartTls());
            sslCheckBox.setSelected(config.isSsl());
            usernameField.setText(config.getUsername());
            passwordField.setText(config.getPassword());
            fromField.setText(config.getFrom());
        } catch (Exception ex) {
            Dialogs.error("Config Error", "Could not load saved mail settings.", ex);
        }
    }

    private void saveConfig() {
        try {
            //persist SMTP settings locally so user doesnt have to re-enter app password on every launch
            context.getEmailService().saveConfig(buildEmailConfig());
            Dialogs.info("Saved", "Mail settings saved locally for this application.");
        } catch (Exception ex) {
            Dialogs.error("Save Error", "Could not save mail settings.", ex);
        }
    }

    private void sendEmail() {
        //validate file exists on disk before attempting STMP send
        if (selectedFile == null || !selectedFile.exists()) {
            Dialogs.warn("No File Selected", "Choose an image or video file to attach first.");
            return;
        }
        //validate required address fields are filled before opening SMTP connection
        if (toField.getText().isBlank() || fromField.getText().isBlank()) {
            Dialogs.warn("Missing Fields", "Fill in both the From and To email addresses.");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                context.getEmailService().sendEmail(
                        buildEmailConfig(),
                        toField.getText().trim(),
                        subjectField.getText().trim(),
                        bodyArea.getText(),
                        selectedFile
                );
                return null;
            }
        };
        sendingIndicator.visibleProperty().bind(task.runningProperty());
        task.setOnSucceeded(e -> {
            sendingIndicator.visibleProperty().unbind();
            sendingIndicator.setVisible(false);
            Dialogs.info("Email Sent", "The file was sent successfully.");
        });
        task.setOnFailed(e -> {
            sendingIndicator.visibleProperty().unbind();
            sendingIndicator.setVisible(false);
            Dialogs.error("Send Error", "Could not send the email.", task.getException());
        });
        Thread thread = new Thread(task, "email-sender");
        thread.setDaemon(true);
        thread.start();
    }

    private EmailConfig buildEmailConfig() {
        EmailConfig config = new EmailConfig();
        config.setSmtpHost(smtpHostField.getText().trim());
        config.setSmtpPort(smtpPortSpinner.getValue());
        config.setStartTls(startTlsCheckBox.isSelected());
        config.setSsl(sslCheckBox.isSelected());
        config.setUsername(usernameField.getText().trim());
        config.setPassword(passwordField.getText());
        config.setFrom(fromField.getText().trim());
        return config;
    }

    //
    private void openWhatsAppHelper() {
        if (selectedFile == null || !selectedFile.exists()) {
            Dialogs.warn("No File Selected", "Choose an image or video file first.");
            return;
        }
        try {
            String caption = bodyArea.getText().isBlank() ? "Shared via PhotoFusion FX" : bodyArea.getText().trim();
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(selectedFile));
            content.putString(caption);
            Clipboard.getSystemClipboard().setContent(content);

            String url = "https://wa.me/?text=" + URLEncoder.encode(caption, StandardCharsets.UTF_8);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
            Dialogs.info("WhatsApp Opened", "WhatsApp was opened and the actual selected file was copied to your clipboard. Paste in the chat to attach the file itself.");
        } catch (Exception ex) {
            Dialogs.error("WhatsApp Helper Error", "Could not open the WhatsApp Web helper.", ex);
        }
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}

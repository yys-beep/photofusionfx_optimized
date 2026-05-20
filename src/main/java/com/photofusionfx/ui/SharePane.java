package com.photofusionfx.ui;

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

import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ComboBox;

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
    private final TextArea whatsappMessageArea = new TextArea("Ctrl+V to share the image from Photofusion ");

    private File selectedFile;

public SharePane(AppContext context) {
        this.context = context;
        setSpacing(15);
        setPadding(new Insets(15));

        smtpPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 587));
        smtpPortSpinner.setEditable(true);
        bodyArea.setPrefRowCount(6);
        bodyArea.setWrapText(true);
        bodyArea.setPromptText("Email body...");
        
        // Setup new WhatsApp text area
        whatsappMessageArea.setPrefRowCount(3);
        whatsappMessageArea.setWrapText(true);

        sendingIndicator.setVisible(false);
        sendingIndicator.setPrefSize(24, 24);

        // We load the file section first, then our new Tab layout
        getChildren().addAll(buildFileSection(), buildSharingTabs());
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

        // Removed the WhatsApp button from here (moved to Tab)
        HBox row = new HBox(10, selectedFileLabel, spacer(), useSelectedPhotoButton, chooseFileButton);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selectedFileLabel, Priority.ALWAYS);

        VBox box = new VBox(8, new Label("Attachment / Export File"), row);
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

    private TabPane buildSharingTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        Tab emailTab = new Tab("✉ Share via Email", buildEmailContent());
        Tab whatsappTab = new Tab("💬 Share via WhatsApp", buildWhatsAppContent());
        
        tabPane.getTabs().addAll(emailTab, whatsappTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        return tabPane;
    }

    private VBox buildWhatsAppContent() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        
        Label instructionLabel = new Label("Customize your WhatsApp caption:");
        instructionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        ComboBox<String> templateBox = new ComboBox<>();
        templateBox.setPromptText("Choose a quick template...");
        templateBox.getItems().addAll(
            "Hey! Check out this awesome edit I just made using PhotoFusion FX",
            "Made this today using PhotoFusion FX. Hope you like it!",
            "Just finished working on this! What do you think?"
        );
        
        templateBox.setOnAction(e -> {
            if (templateBox.getValue() != null) {
                whatsappMessageArea.setText(templateBox.getValue());
            }
        });

        Button clearButton = new Button("Clear Message");
        clearButton.setOnAction(e -> {
            whatsappMessageArea.clear();
            templateBox.getSelectionModel().clearSelection();
        });

        HBox templateRow = new HBox(10, new Label("Templates:"), templateBox, clearButton);
        templateRow.setAlignment(Pos.CENTER_LEFT);

        Label pasteReminder = new Label(" After WhatsApp opens, click the chat box and press Ctrl+V (or Right-Click -> Paste) to attach your photo!");
        pasteReminder.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;"); 
        pasteReminder.setWrapText(true);
        
        Button openWhatsAppButton = new Button("Open WhatsApp Web");
        openWhatsAppButton.getStyleClass().add("primary-button");
        openWhatsAppButton.setOnAction(e -> openWhatsAppHelper());
        
        box.getChildren().addAll(instructionLabel, templateRow, whatsappMessageArea, pasteReminder, openWhatsAppButton);
        return box;
    }

    private VBox buildEmailContent() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));

        // 1. Basic Email Fields
        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(12); basicGrid.setVgap(10);
        basicGrid.add(new Label("From:"), 0, 0); basicGrid.add(fromField, 1, 0);
        basicGrid.add(new Label("To:"), 0, 1); basicGrid.add(toField, 1, 1);
        basicGrid.add(new Label("Subject:"), 0, 2); basicGrid.add(subjectField, 1, 2);
        basicGrid.add(new Label("Body:"), 0, 3); basicGrid.add(bodyArea, 1, 3);
        
        GridPane.setHgrow(fromField, Priority.ALWAYS);
        GridPane.setHgrow(toField, Priority.ALWAYS);
        GridPane.setHgrow(subjectField, Priority.ALWAYS);
        GridPane.setHgrow(bodyArea, Priority.ALWAYS);

// 2. Auth Settings & App Password Warning
        VBox authBox = new VBox(8);
        authBox.setStyle("-fx-background-color: #fff3cd; -fx-padding: 10; -fx-border-color: #ffe69c; -fx-border-radius: 5;");
        Label warningLabel = new Label("Important: Use an App Password");
        warningLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #856404;");
        Label warningDesc = new Label("Do not use your normal Gmail password. Create a 16-digit 'App Password' in your Google Account Security settings.");
        warningDesc.setWrapText(true);
        
        GridPane authGrid = new GridPane();
        authGrid.setHgap(12); authGrid.setVgap(10);
        authGrid.add(new Label("Email:"), 0, 0); authGrid.add(usernameField, 1, 0);
        authGrid.add(new Label("App Password:"), 0, 1); authGrid.add(passwordField, 1, 1);
        GridPane.setHgrow(usernameField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);
        authBox.getChildren().addAll(warningLabel, warningDesc, authGrid);

        // 3. Advanced SMTP Settings (Hidden by default to prevent confusion)
        GridPane advGrid = new GridPane();
        advGrid.setHgap(12); advGrid.setVgap(10);
        advGrid.add(new Label("SMTP Host:"), 0, 0); advGrid.add(smtpHostField, 1, 0);
        advGrid.add(new Label("Port:"), 2, 0); advGrid.add(smtpPortSpinner, 3, 0);
        advGrid.add(startTlsCheckBox, 1, 1); advGrid.add(sslCheckBox, 2, 1);
        
        // Auto-fill Gmail defaults if empty
        if(smtpHostField.getText().isEmpty()) smtpHostField.setText("smtp.gmail.com");
        startTlsCheckBox.setSelected(true);

        TitledPane advancedPane = new TitledPane(" Advanced SMTP Settings (Click to expand)", advGrid);
        advancedPane.setExpanded(false); // Collapsed by default!

        // 4. Buttons
        Button saveSettingsButton = new Button("Save Settings");
        saveSettingsButton.setOnAction(e -> saveConfig());
        Button sendButton = new Button("Send Email with Attachment");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setOnAction(e -> sendEmail());
        HBox buttons = new HBox(10, saveSettingsButton, sendButton, sendingIndicator);
        buttons.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(basicGrid, authBox, advancedPane, buttons);
        return box;
    }


    //whatsapp has no public java api for direct file injection
    private void openWhatsAppHelper() {
        if (selectedFile == null || !selectedFile.exists()) {
            Dialogs.warn("No File Selected", "Choose an image or video file first.");
            return;
        }
        try {
            // Get text from the new dedicated WhatsApp text area
            String caption = whatsappMessageArea.getText().trim();
            
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(selectedFile));
            content.putString(caption);
            Clipboard.getSystemClipboard().setContent(content);

            String url = "https://wa.me/?text=" + URLEncoder.encode(caption, StandardCharsets.UTF_8);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
            // Simple success dialog since we removed the long hint text
            Dialogs.info("Ready to Paste", "WhatsApp opened. Just paste (Ctrl+V) in the chat to attach the file and message.");
        } catch (Exception ex) {
            Dialogs.error("WhatsApp Helper Error", "Could not open WhatsApp.", ex);
        }
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}

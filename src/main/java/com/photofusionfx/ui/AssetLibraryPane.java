package com.photofusionfx.ui;

import com.photofusionfx.AppContext;
import com.photofusionfx.model.AssetItem;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;   
import com.photofusionfx.util.FileUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.control.Tooltip;
import javafx.scene.control.OverrunStyle;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class AssetLibraryPane extends BorderPane {
    private final AppContext context;
    private final ObservableList<AssetItem> assets = FXCollections.observableArrayList();
    private final ListView<AssetItem> assetList = new ListView<>(assets);
    private final ImageView preview = new ImageView();
    private final Label detailLabel = new Label("No asset selected.");

    public AssetLibraryPane(AppContext context) {
        this.context = context;
        setPadding(new Insets(12));
        setTop(buildToolbar());
        setCenter(buildContent());
        configureList();
        refreshAssets();
    }

    private HBox buildToolbar() {
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshAssets());
        Button importButton = new Button("Import Files into Asset Library");
        importButton.setOnAction(e -> importAssets());
        importButton.getStyleClass().add("primary-button");
        Button copyButton = new Button("Copy Actual File to Clipboard");
        copyButton.setOnAction(e -> copySelectedFile());
        copyButton.getStyleClass().add("success-button");
        Button useLatestButton = new Button("Use as Latest Share File");
        useLatestButton.setOnAction(e -> useSelectedAsLatestExport());
        Button deleteButton = new Button("Delete Asset");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(e -> deleteSelectedAsset());
        Button openFolderButton = new Button("Open Asset Folder");
        openFolderButton.setOnAction(e -> openAssetFolder());

        HBox row = new HBox(10, refreshButton, importButton, copyButton, useLatestButton, deleteButton, spacer(), openFolderButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 12, 0));
        return row;
    }

    private HBox buildContent() {
        preview.setPreserveRatio(true);
        preview.setFitWidth(620);
        preview.setFitHeight(520);
        BorderPane previewPane = new BorderPane(preview);
        previewPane.getStyleClass().add("preview-box");
        previewPane.setPadding(new Insets(12));

        VBox left = new VBox(8, new Label("Reusable objects / files"), assetList);
        left.setMinWidth(300); // Prevents it from getting squished
        left.setMaxWidth(300); // Prevents it from expanding
        left.setPrefWidth(300);
        VBox right = new VBox(8, new Label("Preview"), previewPane, detailLabel);
        VBox.setVgrow(assetList, Priority.ALWAYS);
        VBox.setVgrow(previewPane, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(12, left, right);
    }

    private void configureList() {
        // Switch the selection model configuration if necessary, or keep the default
        assetList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, item) -> updatePreview(item));
        
        // Define the custom graphical cell factory
        assetList.setCellFactory(list -> new ListCell<>() {
            private final ImageView thumbnail = new ImageView();
            private final Label nameLabel = new Label();
            private final Label sizeLabel = new Label();
            private final VBox cardLayout = new VBox(6);

            {
                // Configure thumbnail sizing for "Large Icon" styling
                thumbnail.setFitWidth(80);
                thumbnail.setFitHeight(80);
                thumbnail.setPreserveRatio(true);
                thumbnail.setSmooth(true);

                // Configure typography wrapping and alignment
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                nameLabel.setWrapText(false); // Force it to stay on exactly one line
                nameLabel.setMaxWidth(110); // Constrain width (adjust this slightly if you want it wider/narrower)
                nameLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS); // Shows "2026-05-19...d592.png"
                nameLabel.setAlignment(Pos.CENTER);

                sizeLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

                // Layout components vertically inside the cell card
                cardLayout.setAlignment(Pos.CENTER);
                cardLayout.setPadding(new Insets(6));
                cardLayout.getChildren().addAll(thumbnail, nameLabel, sizeLabel);
            }

            @Override
            protected void updateItem(AssetItem item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    File file = item.getFile();
                    nameLabel.setText(item.getName());
                    nameLabel.setTooltip(new Tooltip(item.getName()));
                    sizeLabel.setText(humanSize(item.getSizeBytes()));

                    // Dynamically generate or load thumbnails based on media types
                    if (FileUtils.isImageFile(file.toPath())) {
                        // Load image backgrounds asynchronously (backgroundLoading = true) to prevent UI lag
                        Image thumbImage = new Image(file.toURI().toString(), 80, 80, true, true, true);
                        thumbnail.setImage(thumbImage);
                    } else if (file.getName().toLowerCase().endsWith(".mp4") || 
                               file.getName().toLowerCase().endsWith(".mov")) {
                        // Supply a fallback systemic icon or video placeholder image
                        thumbnail.setImage(new Image(getClass().getResourceAsStream("/icons/video-placeholder.png"), 80, 80, true, true));
                    } else {
                        // Generic fallback icon
                        thumbnail.setImage(new Image(getClass().getResourceAsStream("/icons/generic-file.png"), 80, 80, true, true));
                    }

                    setGraphic(cardLayout);
                    setText(null); // Explicitly clear old plain text representation
                }
            }
        });
    }

    private void refreshAssets() {
        try {
            assets.setAll(context.getAssetLibraryService().listAssets());
        } catch (Exception ex) {
            Dialogs.error("Asset Error", "Could not load the asset library.", ex);
        }
    }

    private void importAssets() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Files into Asset Library");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported Media", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp", "*.mp4", "*.mov", "*.m4v"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            for (File file : files) {
                if (FileUtils.isSupportedMediaFile(file.toPath())) {
                    context.getAssetLibraryService().importAsset(file);
                }
            }
            refreshAssets();
            Dialogs.info("Import Complete", "Selected files were copied into the asset library.");
        } catch (Exception ex) {
            Dialogs.error("Import Error", "Could not import one or more assets.", ex);
        }
    }

    private void updatePreview(AssetItem item) {
        if (item == null) {
            preview.setImage(null);
            detailLabel.setText("No asset selected.");
            return;
        }
        File file = item.getFile();
        detailLabel.setText(file.getAbsolutePath());
        if (FileUtils.isImageFile(file.toPath())) {
            preview.setImage(new Image(file.toURI().toString(), 620, 520, true, true));
        } else {
            preview.setImage(null);
            detailLabel.setText(file.getAbsolutePath() + "\nVideo/media asset: use it from Share, or import image assets into layer editors.");
        }
    }

    private void copySelectedFile() {
        AssetItem item = assetList.getSelectionModel().getSelectedItem();
        if (item == null) {
            Dialogs.warn("No Asset", "Select an asset first.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putFiles(List.of(item.getFile()));
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.info("Copied", "The actual file object was copied to the clipboard, not only a path string.");
    }

    private void useSelectedAsLatestExport() {
        AssetItem item = assetList.getSelectionModel().getSelectedItem();
        if (item == null) {
            Dialogs.warn("No Asset", "Select an asset first.");
            return;
        }
        context.setLatestExportFile(item.getFile());
        Dialogs.info("Selected", "This asset is now the latest file in the Share tab.");
    }

    private void deleteSelectedAsset() {
        AssetItem item = assetList.getSelectionModel().getSelectedItem();
        if (item == null) {
            Dialogs.warn("No Asset", "Select an asset first.");
            return;
        }
        try {
            Files.deleteIfExists(item.getFile().toPath());
            refreshAssets();
            preview.setImage(null);
            detailLabel.setText("Asset deleted.");
        } catch (Exception ex) {
            Dialogs.error("Delete Error", "Could not delete the selected asset.", ex);
        }
    }

    private void openAssetFolder() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(AppPaths.ASSET_DIR.toFile());
            }
        } catch (Exception ex) {
            Dialogs.error("Open Folder Error", "Could not open the asset-library folder.", ex);
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}

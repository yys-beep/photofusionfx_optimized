package com.photofusionfx.ui;

import com.photofusionfx.AppContext;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.scene.control.ListView;


public class MosaicPane extends BorderPane {
    private final AppContext context;

    private final ComboBox<PhotoItem> targetComboBox = new ComboBox<>();

    // Tile source mode
    private final ComboBox<String> tileSourceModeBox = new ComboBox<>();
    private static final String MODE_ALL = "All library images";
    private static final String MODE_FAV = "Favourites only";
    private static final String MODE_SELECTED = "Selected images";

    private final ListView<PhotoItem> tileListView = new ListView<>();
    private final Label tileCountLabel = new Label();


    // Output + strategy controls
    private final Spinner<Integer> outputWidthSpinner = new Spinner<>();
    private final Spinner<Integer> columnsSpinner = new Spinner<>();
    private final Spinner<Integer> maxUsageSpinner = new Spinner<>();
    private final Spinner<Integer> neighborRadiusSpinner = new Spinner<>();
    private final Slider neighborPenaltySlider = new Slider(0, 300, 90);

    // Default OFF (keeps tile photos authentic)
    private final CheckBox colorBalanceCheck = new CheckBox("Color-balance tiles to match target (tint tiles)");

    private final Label outputInfoLabel = new Label();

    private final ProgressBar progressBar = new ProgressBar(0);
    private final javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(900, 680);
    private BufferedImage currentMosaic;

    public MosaicPane(AppContext context) {
        this.context = context;

        setPadding(new Insets(12));
        SplitPane splitPane = new SplitPane(buildControls(), buildPreview());
        splitPane.setDividerPositions(0.30);
        setCenter(splitPane);

        // Defaults: “near target as possible”
        // - high columns for detail (80)
        // - higher output width so tiles not tiny in output (8000)
        outputWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(800, 20000, 800, 500));
        columnsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(20, 220, 80, 5));
        maxUsageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 20, 1));
        neighborRadiusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 6, 2, 1));
        neighborPenaltySlider.setValue(90);

        outputWidthSpinner.setEditable(true);
        columnsSpinner.setEditable(true);
        maxUsageSpinner.setEditable(true);
        neighborRadiusSpinner.setEditable(true);

        colorBalanceCheck.setSelected(false); // OFF by default (no recolor)

        // Tile modes
        tileSourceModeBox.setItems(FXCollections.observableArrayList(MODE_ALL, MODE_FAV, MODE_SELECTED));
        tileSourceModeBox.getSelectionModel().select(MODE_ALL);

        // Selected list
        tileListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tileListView.setItems(context.getPhotoLibrary());
        tileListView.setPrefHeight(220);

        // Target combo
        targetComboBox.setItems(context.getPhotoLibrary());
        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> targetComboBox.getSelectionModel().select(newValue));
        context.getPhotoLibrary().addListener((javafx.collections.ListChangeListener<? super PhotoItem>) change -> {
            targetComboBox.setItems(context.getPhotoLibrary());
            tileListView.setItems(context.getPhotoLibrary());
            if (targetComboBox.getSelectionModel().getSelectedItem() == null && !context.getPhotoLibrary().isEmpty()) {
                targetComboBox.getSelectionModel().select(context.getPhotoLibrary().getFirst());
            }
            updateOutputInfo();
            updateTileCountLabel();
        });

        if (context.getSelectedPhoto() != null) {
            targetComboBox.getSelectionModel().select(context.getSelectedPhoto());
        } else if (!context.getPhotoLibrary().isEmpty()) {
            targetComboBox.getSelectionModel().select(context.getPhotoLibrary().getFirst());
        }

        // Updates
        targetComboBox.valueProperty().addListener((obs, ov, nv) -> { updateOutputInfo(); updateTileCountLabel(); });
        tileSourceModeBox.valueProperty().addListener((obs, ov, nv) -> { updateSelectedModeVisibility(); updateTileCountLabel(); });
        tileListView.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<? super PhotoItem>) c -> updateTileCountLabel()
        );
        outputWidthSpinner.valueProperty().addListener((obs, ov, nv) -> updateOutputInfo());
        columnsSpinner.valueProperty().addListener((obs, ov, nv) -> updateOutputInfo());

        updateSelectedModeVisibility();
        updateOutputInfo();
        updateTileCountLabel();
    }

    private ScrollPane buildControls() {
        Button generateButton = new Button("Generate Mosaic");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> generateMosaic());
        generateButton.getStyleClass().add("primary-button");

        Button exportButton = new Button("Export Mosaic");
        exportButton.setMaxWidth(Double.MAX_VALUE);
        exportButton.setOnAction(e -> exportMosaic());
        exportButton.getStyleClass().add("success-button");

        Button saveToLibraryButton = new Button("Save to Repository");
        saveToLibraryButton.setMaxWidth(Double.MAX_VALUE);
        saveToLibraryButton.setOnAction(e -> saveMosaicIntoLibrary());
        saveToLibraryButton.getStyleClass().add("success-button");

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        // Stretch
        targetComboBox.setMaxWidth(Double.MAX_VALUE);
        tileSourceModeBox.setMaxWidth(Double.MAX_VALUE);
        outputWidthSpinner.setMaxWidth(Double.MAX_VALUE);
        columnsSpinner.setMaxWidth(Double.MAX_VALUE);
        maxUsageSpinner.setMaxWidth(Double.MAX_VALUE);
        neighborRadiusSpinner.setMaxWidth(Double.MAX_VALUE);
        neighborPenaltySlider.setMaxWidth(Double.MAX_VALUE);

        outputInfoLabel.setWrapText(true);
        outputInfoLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-font-weight: bold;");
        tileCountLabel.setWrapText(true);
        tileCountLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");

        Label notesLabel = new Label(
                "Default is tuned to resemble the target as much as possible:\n" +
                "• Placement Mode: TARGET_MATCH_AVG_RGB_ANTI_REPEAT\n" +
                "• Columns: 80 (high detail)\n" +
                "• Output Width: 8000px\n" +
                "WEBP is skipped (not supported)."
        );
        notesLabel.setWrapText(true);
        notesLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        VBox selectedBox = new VBox(6,
                new Label("Pick tiles (only used in 'Selected images' mode)"),
                tileListView
        );
        VBox.setVgrow(tileListView, Priority.ALWAYS);

// Package inputs with the customized hints you requested
        VBox outputWBox = hintBox("Output Width (px)", outputWidthSpinner, "Higher width = higher resolution and larger tiles.");
        VBox columnsBox = hintBox("Columns", columnsSpinner, "More columns = sharper overall but smaller tiles.");
        VBox maxUsageBox = hintBox("Max usage per tile", maxUsageSpinner, "Lower forces more varied photos.");
        VBox radiusBox = hintBox("Neighbour radius", neighborRadiusSpinner, "Higher reduces nearby repeats.");
        VBox penaltyBox = hintBox("Neighbour penalty", neighborPenaltySlider, "Higher strongly avoids nearby repeats.");
        
        Label colorHint = new Label("ON: Tints to match target cell. OFF: Keeps real photo colors (target may be less accurate).");
        colorHint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        colorHint.setWrapText(true);
        VBox colorBox = new VBox(2, colorBalanceCheck, colorHint);

        // Assemble the final vertical layout (with the double separator removed!)
        VBox controls = new VBox(12,
                new Label("Target Image"), targetComboBox,
                new Separator(),

                new Label("Tile Source Mode"), tileSourceModeBox,
                tileCountLabel,
                selectedBox,

                new Separator(), // Only ONE separator here now!
                
                new Label("Output Options"),
                outputWBox,
                columnsBox,
                outputInfoLabel,

                new Separator(),
                new Label("Anti-repeat Strategy"),
                maxUsageBox,
                radiusBox,
                penaltyBox,
                colorBox,

                new Separator(),
                new Label("Actions"),
                generateButton,
                exportButton,
                saveToLibraryButton,
                progressBar
        );
        controls.setPadding(new Insets(6, 14, 6, 12));

        ScrollPane scrollPane = new ScrollPane(controls);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinWidth(280);
        scrollPane.setPrefWidth(320);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return scrollPane;
    }

    private ScrollPane buildPreview() {
        Pane canvasHolder = new Pane(canvas);
        canvasHolder.setMinSize(360, 300);

        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());

        canvas.widthProperty().addListener((obs, oldValue, newValue) -> drawCanvas());
        canvas.heightProperty().addListener((obs, oldValue, newValue) -> drawCanvas());

        canvasHolder.getStyleClass().add("preview-box");
        canvasHolder.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(canvasHolder);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            canvasHolder.setPrefWidth(Math.max(360, newBounds.getWidth() - 4));
            canvasHolder.setPrefHeight(Math.max(300, newBounds.getHeight() - 4));
        });

        Platform.runLater(this::drawCanvas);
        return scrollPane;
    }

    private void updateSelectedModeVisibility() {
        boolean selectedMode = MODE_SELECTED.equals(tileSourceModeBox.getValue());
        tileListView.setDisable(!selectedMode);
        tileListView.setVisible(selectedMode);
        tileListView.setManaged(selectedMode);
    }

    private void updateOutputInfo() {
        PhotoItem target = targetComboBox.getSelectionModel().getSelectedItem();
        int columns = safeSpinner(columnsSpinner, 80);
        int outputW = safeSpinner(outputWidthSpinner, 800);

        if (target == null) {
            outputInfoLabel.setText("Output: (choose a target image)");
            return;
        }

        int tileSize = Math.max(8, outputW / Math.max(1, columns));

        int rows;
        try {
            BufferedImage targetImage = ImageUtils.read(new File(target.getFilePath()));
            rows = Math.max(1, (int) Math.round((double) targetImage.getHeight() / targetImage.getWidth() * columns));
        } catch (Exception ex) {
            rows = Math.max(1, columns * 9 / 16);
        }

        int outW = columns * tileSize;
        int outH = rows * tileSize;
        outputInfoLabel.setText(String.format("Output: %d x %d px | Grid: %d x %d | Tile: %d px",
                outW, outH, columns, rows, tileSize));
    }

    private void updateTileCountLabel() {
        PhotoItem target = targetComboBox.getSelectionModel().getSelectedItem();
        List<PhotoItem> candidates = computeTileCandidates(target);

        long webpCount = candidates.stream()
                .map(PhotoItem::getFilePath)
                .filter(Objects::nonNull)
                .map(p -> p.toLowerCase(Locale.ROOT))
                .filter(p -> p.endsWith(".webp"))
                .count();

        tileCountLabel.setText(String.format("Tile candidates: %d images (WEBP inside candidates: %d will be skipped)",
                candidates.size(), webpCount));
    }

    private List<PhotoItem> computeTileCandidates(PhotoItem target) {
        String mode = tileSourceModeBox.getValue();

        List<PhotoItem> base;
        if (MODE_SELECTED.equals(mode)) {
            base = tileListView.getSelectionModel().getSelectedItems();
        } else if (MODE_FAV.equals(mode)) {
            base = context.getPhotoLibrary().stream().filter(PhotoItem::isFavorite).toList();
        } else {
            base = context.getPhotoLibrary();
        }

        if (target == null) return List.copyOf(base);
        if (context.getPhotoLibrary().size() <= 1) return List.copyOf(base);

        return base.stream()
                .filter(item -> !Objects.equals(item.getFilePath(), target.getFilePath()))
                .collect(Collectors.toList());
    }

    private void generateMosaic() {
        PhotoItem target = targetComboBox.getSelectionModel().getSelectedItem();
        if (target == null) {
            Dialogs.warn("No Target Image", "Choose a target image for the mosaic first.");
            return;
        }

        List<PhotoItem> tileCandidates = computeTileCandidates(target);
        if (tileCandidates.isEmpty()) {
            Dialogs.warn("No Tiles Available", "No tile images available for the selected tile source mode.");
            return;
        }

        final int columns = safeSpinner(columnsSpinner, 80);
        final int outputW = safeSpinner(outputWidthSpinner, 800);
        final int tileSize = Math.max(8, outputW / Math.max(1, columns));

        final boolean colorBalanceEnabled = colorBalanceCheck.isSelected(); // OFF by default
        final int maxUsage = safeSpinner(maxUsageSpinner, 20);
        final int radius = safeSpinner(neighborRadiusSpinner, 2);
        final double penalty = neighborPenaltySlider.getValue();

        Task<BufferedImage> task = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                BufferedImage targetImage = ImageUtils.read(new File(target.getFilePath()));

                List<BufferedImage> sources = new java.util.ArrayList<>();
                int skipped = 0;

                for (PhotoItem item : tileCandidates) {
                    String path = item.getFilePath();
                    if (path == null) { skipped++; continue; }
                    if (path.toLowerCase(Locale.ROOT).endsWith(".webp")) { skipped++; continue; }
                    try {
                        sources.add(ImageUtils.read(new File(path)));
                    } catch (Exception ex) {
                        skipped++;
                    }
                }

                if (sources.isEmpty()) {
                    throw new IllegalStateException("No usable tile images found. Skipped " + skipped + " unsupported/unreadable images.");
                }

                return context.getMosaicService().generateMosaic(
                        targetImage,
                        sources,
                        columns,
                        tileSize,
                        colorBalanceEnabled,
                        maxUsage,
                        radius,
                        penalty,
                        com.photofusionfx.service.MosaicService.PlacementMode.TARGET_MATCH_AVG_RGB_ANTI_REPEAT,
                        progress -> updateProgress((long) (progress * 1000), 1000)
                );
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);

        task.setOnSucceeded(e -> {
            currentMosaic = task.getValue();
            drawCanvas();
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            Dialogs.error("Mosaic Error", "Could not generate the mosaic image.", task.getException());
        });

        Thread thread = new Thread(task, "mosaic-generator");
        thread.setDaemon(true);
        thread.start();
    }

    private void exportMosaic() {
        if (currentMosaic == null) {
            Dialogs.warn("No Mosaic", "Generate a mosaic first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Mosaic");
        chooser.setInitialDirectory(AppPaths.EXPORT_DIR.toFile());
        String base = targetComboBox.getSelectionModel().getSelectedItem() == null
                ? "mosaic"
                : FileUtils.slugify(FileUtils.baseName(targetComboBox.getSelectionModel().getSelectedItem().getName())) + "-mosaic";
        chooser.setInitialFileName(base + ".png");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Image", "*.jpg", "*.jpeg")
        );
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try {
            ImageUtils.write(currentMosaic, file);
            context.setLatestExportFile(file);
            Dialogs.info("Export Complete", "Mosaic exported successfully.");
        } catch (Exception ex) {
            Dialogs.error("Export Error", "Could not export the mosaic.", ex);
        }
    }

    private void saveMosaicIntoLibrary() {
        if (currentMosaic == null) {
            Dialogs.warn("No Mosaic", "Generate a mosaic first.");
            return;
        }
        try {
            String base = targetComboBox.getSelectionModel().getSelectedItem() == null
                    ? "mosaic"
                    : FileUtils.baseName(targetComboBox.getSelectionModel().getSelectedItem().getName()) + "-mosaic";
            var imported = context.getLibraryService().saveGeneratedImageToLibrary(currentMosaic, base);
            context.addImportedPhotos(List.of(imported));
            context.setLatestExportFile(Path.of(imported.getFilePath()).toFile());
            Dialogs.info("Saved to Library", "Mosaic has been imported into the managed library.");
        } catch (Exception ex) {
            Dialogs.error("Save Error", "Could not save the mosaic into the library.", ex);
        }
    }

    private void drawCanvas() {
        var gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();

        gc.setFill(javafx.scene.paint.Color.rgb(241, 245, 249));
        gc.fillRect(0, 0, cw, ch);

        if (currentMosaic == null) {
            gc.setFill(javafx.scene.paint.Color.rgb(100, 116, 139));
            gc.setFont(javafx.scene.text.Font.font(18));
            gc.fillText("Generate a mosaic to preview it here.", 28, 42);
            return;
        }

        // Fit-to-preview: mosaic may be huge; this will make tiles look tiny unless you zoom.
        double scale = Math.min(cw / currentMosaic.getWidth(), ch / currentMosaic.getHeight());
        if (!Double.isFinite(scale) || scale <= 0) scale = 1.0;

        double drawW = currentMosaic.getWidth() * scale;
        double drawH = currentMosaic.getHeight() * scale;
        double offsetX = (cw - drawW) / 2.0;
        double offsetY = (ch - drawH) / 2.0;

        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillRect(offsetX, offsetY, drawW, drawH);

        gc.drawImage(ImageUtils.toFxImage(currentMosaic), offsetX, offsetY, drawW, drawH);
    }

    private int safeSpinner(Spinner<Integer> spinner, int fallback) {
        try {
            Integer v = spinner.getValue();
            return v == null ? fallback : v;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private VBox hintBox(String title, javafx.scene.Node control, String hint) {
        Label titleLabel = new Label(title);
        Label hintLabel = new Label(hint);
        hintLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        hintLabel.setWrapText(true);
        return new VBox(2, titleLabel, hintLabel, control);
    }
}
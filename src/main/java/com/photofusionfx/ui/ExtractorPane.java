package com.photofusionfx.ui;

//ExtractorPane supports brush, lasso, magic wand selection and object extraction

import com.photofusionfx.AppContext;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExtractorPane extends BorderPane {
    private static final String TOOL_MAGIC_WAND = "Magic Wand / object color";
    private static final String TOOL_BRUSH_ADD = "Brush add selection";
    private static final String TOOL_BRUSH_ERASE = "Brush erase selection";
    private static final String TOOL_LASSO = "Lasso selection";
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 12.0;

    private final AppContext context;
    private final Canvas sourceCanvas = new Canvas(720, 620);
    private final Canvas resultCanvas = new Canvas(720, 620);
    private final Pane sourceCanvasPane = new Pane(sourceCanvas);
    private final Pane resultCanvasPane = new Pane(resultCanvas);
    private final Label selectionLabel = new Label("Selected image: none");
    private final Label seedLabel = new Label("Selection: none");
    private final ComboBox<String> toolBox = new ComboBox<>();
    private final Slider thresholdSlider = new Slider(0.04, 0.40, 0.16);
    private final Slider brushSizeSlider = new Slider(4, 120, 30);
    private final ColorPicker outlineColorPicker = new ColorPicker(Color.WHITE);
    private final Slider outlineWidthSlider = new Slider(0, 30, 0);
    private final CheckBox enhanceCheck = new CheckBox("Basic enhancement after extraction");
    private final CheckBox largeSelectionAreaCheck = new CheckBox("Large selection area");
    private final ColorPicker tintColorPicker = new ColorPicker(Color.TRANSPARENT);
    private final Slider tintStrengthSlider = new Slider(0, 1, 0);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));

    // Zoom & pan
    private final Slider zoomSlider = new Slider(MIN_ZOOM, MAX_ZOOM, 1.0);
    private final Label zoomLabel = new Label("100%");
    private double userZoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private double lastPanMouseX = 0.0;
    private double lastPanMouseY = 0.0;
    private boolean panning = false;

    private BufferedImage originalImage;
    private BufferedImage previewImage;
    private BufferedImage lastExtractedPreview;
    private boolean[] selectionMask;
    private double sourceScale = 1.0;
    private double sourceOffsetX = 0.0;
    private double sourceOffsetY = 0.0;
    private double sourceViewportWidth = 720.0;
    private double sourceViewportHeight = 620.0;
    private double resultScale = 1.0;
    private double resultOffsetX = 0.0;
    private double resultOffsetY = 0.0;
    private double resultViewportWidth = 720.0;
    private double resultViewportHeight = 620.0;
    private SplitPane viewsSplitPane;
    private VBox resultViewBox;
    private final List<double[]> lassoPoints = new ArrayList<>();

    public ExtractorPane(AppContext context) {
        this.context = context;
        setPadding(new Insets(12));
        largeSelectionAreaCheck.setSelected(true);

        configureSlider(thresholdSlider, false);
        configureSlider(brushSizeSlider, true);
        configureSlider(outlineWidthSlider, true);
        configureSlider(tintStrengthSlider, false);
        toolBox.getItems().setAll(TOOL_MAGIC_WAND, TOOL_BRUSH_ADD, TOOL_BRUSH_ERASE, TOOL_LASSO);
        toolBox.getSelectionModel().selectFirst();

        setCenter(buildScrollableLayout());

        // zoom controls
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(1.0);
        zoomSlider.setBlockIncrement(0.25);
        zoomSlider.valueProperty().addListener((obs, ov, nv) -> {
            userZoom = nv.doubleValue();
            zoomLabel.setText(String.format("%d%%", (int) Math.round(userZoom * 100)));
            updateCanvasContentSizes();
            drawCanvases();
        });

        attachCanvasHandlers();
        debounce.setOnFinished(e -> renderExtractionPreview());
        thresholdSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (TOOL_MAGIC_WAND.equals(toolBox.getValue())) {
                debounce.playFromStart();
            }
        });
        outlineColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        outlineWidthSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        enhanceCheck.selectedProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        largeSelectionAreaCheck.selectedProperty().addListener((obs, oldValue, newValue) -> applySelectionAreaMode());
        tintColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        tintStrengthSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());

        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> {
            panX = 0; panY = 0; resetZoom();
            loadSelectedPhoto(newValue);
        });
        loadSelectedPhoto(context.getSelectedPhoto());
    }

    private ScrollPane buildScrollableLayout() {
        VBox content = new VBox(12, buildControls(), buildViews());
        content.setFillWidth(true);
        content.setMinWidth(900);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        return scrollPane;
    }

    private void resetZoom() {
        setZoom(1.0);
        panX = 0;
        panY = 0;
    }

    private void setZoom(double zoom) {
        zoomSlider.setValue(clamp(zoom, zoomSlider.getMin(), zoomSlider.getMax()));
    }

    private VBox buildControls() {
        Button extractButton = new Button("Update Extraction Preview");
        extractButton.setOnAction(e -> renderExtractionPreview());
        extractButton.getStyleClass().add("primary-button");
        Button clearButton = new Button("Clear Selection");
        clearButton.setOnAction(e -> clearSelection());
        Button invertButton = new Button("Invert Selection");
        invertButton.setOnAction(e -> invertSelection());
        Button exportPngButton = new Button("Export PNG");
        exportPngButton.setOnAction(e -> exportExtractedObject());
        exportPngButton.getStyleClass().add("success-button");
        Button saveAssetButton = new Button("Save to Asset Library");
        saveAssetButton.setOnAction(e -> saveToAssetLibrary());
        saveAssetButton.getStyleClass().add("success-button");
        Button copyButton = new Button("Copy Object as File + Image");
        copyButton.setOnAction(e -> copyExtractedObject());
        Button saveToLibraryButton = new Button("Save into Photo Library");
        saveToLibraryButton.setOnAction(e -> saveIntoLibrary());
        saveToLibraryButton.getStyleClass().add("success-button");
        Button fitZoomButton = new Button("Fit");
        fitZoomButton.setOnAction(e -> resetZoom());
        Button zoom200Button = new Button("200%");
        zoom200Button.setOnAction(e -> setZoom(2.0));
        Button zoom400Button = new Button("400%");
        zoom400Button.setOnAction(e -> setZoom(4.0));
        Button zoom800Button = new Button("800%");
        zoom800Button.setOnAction(e -> setZoom(8.0));

        HBox zoomRow = new HBox(8, new Label("Zoom"), zoomSlider, zoomLabel, fitZoomButton, zoom200Button, zoom400Button, zoom800Button);
        zoomRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(zoomSlider, Priority.ALWAYS);

        VBox controls = new VBox(10,
                selectionLabel,
                new Separator(),
                new HBox(12, new Label("Selection tool"), toolBox, new Label("Brush size"), brushSizeSlider),
                zoomRow,
                labelledRow("Magic-wand color threshold", thresholdSlider),
                seedLabel,
                new HBox(10, extractButton, clearButton, invertButton, largeSelectionAreaCheck),
                new Separator(),
                new HBox(12, new Label("Outline"), outlineColorPicker, new Label("Width"), outlineWidthSlider, enhanceCheck),
                new HBox(12, new Label("Tint / scenario color"), tintColorPicker, new Label("Strength"), tintStrengthSlider),
                new HBox(10, exportPngButton, saveAssetButton, copyButton, saveToLibraryButton)
        );
        controls.setPadding(new Insets(0, 0, 12, 0));
        controls.setFillWidth(true);
        return controls;
    }

    private HBox labelledRow(String text, Slider slider) {
        Label value = new Label();
        value.textProperty().bind(slider.valueProperty().asString("%.2f"));
        HBox header = new HBox(10, new Label(text), spacer(), value);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox wrapper = new VBox(6, header, slider);
        HBox row = new HBox(wrapper);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return row;
    }

    private SplitPane buildViews() {
        ScrollPane left = new ScrollPane(wrapCanvas(sourceCanvas, sourceCanvasPane));
        left.setFitToWidth(false);
        left.setFitToHeight(false);
        left.setPannable(true);
        left.getStyleClass().add("preview-box");
        left.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        left.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        ScrollPane right = new ScrollPane(wrapCanvas(resultCanvas, resultCanvasPane));
        right.setFitToWidth(false);
        right.setFitToHeight(false);
        right.setPannable(true);
        right.getStyleClass().add("preview-box");
        right.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        right.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        bindCanvasToViewport(sourceCanvasPane, left, true);
        bindCanvasToViewport(resultCanvasPane, right, false);

        VBox leftBox = new VBox(8, new Label("Source image selection mask: click / brush / lasso"), left);
        VBox rightBox = new VBox(8, new Label("Extracted object preview"), right);
        resultViewBox = rightBox;
        left.setPrefViewportHeight(720);
        left.setMinHeight(560);
        left.setPrefHeight(760);
        right.setPrefViewportHeight(720);
        right.setMinHeight(560);
        right.setPrefHeight(760);
        leftBox.setMinHeight(600);
        leftBox.setPrefHeight(790);
        rightBox.setMinHeight(360);
        leftBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rightBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(left, Priority.ALWAYS);
        VBox.setVgrow(right, Priority.ALWAYS);
        viewsSplitPane = new SplitPane(leftBox, rightBox);
        viewsSplitPane.setDividerPositions(0.72);
        viewsSplitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        applySelectionAreaMode();
        return viewsSplitPane;
    }

    private void applySelectionAreaMode() {
        if (viewsSplitPane == null || resultViewBox == null) {
            return;
        }
        if (!viewsSplitPane.getItems().contains(resultViewBox)) {
            viewsSplitPane.getItems().add(resultViewBox);
        }
        if (largeSelectionAreaCheck.isSelected()) {
            viewsSplitPane.setDividerPositions(0.78);
        } else {
            viewsSplitPane.setDividerPositions(0.72);
        }
        updateCanvasContentSizes();
        drawCanvases();
    }

    private Pane wrapCanvas(Canvas canvas, Pane pane) {
        pane.setMinSize(420, 360);
        canvas.widthProperty().bind(pane.widthProperty());
        canvas.heightProperty().bind(pane.heightProperty());
        canvas.widthProperty().addListener((obs, oldValue, newValue) -> drawCanvases());
        canvas.heightProperty().addListener((obs, oldValue, newValue) -> drawCanvases());
        return pane;
    }

    private void bindCanvasToViewport(Pane pane, ScrollPane scrollPane, boolean source) {
        scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (source) {
                sourceViewportWidth = Math.max(1, newBounds.getWidth() - 4);
                sourceViewportHeight = Math.max(1, newBounds.getHeight() - 4);
            } else {
                resultViewportWidth = Math.max(1, newBounds.getWidth() - 4);
                resultViewportHeight = Math.max(1, newBounds.getHeight() - 4);
            }
            updateCanvasContentSizes();
            drawCanvases();
        });
    }

    private void attachCanvasHandlers() {
        sourceCanvas.setOnMousePressed(event -> {
            if (previewImage == null) {
                return;
            }

            // Pan with RIGHT mouse button
            if (event.getButton() == MouseButton.SECONDARY) {
                panning = true;
                lastPanMouseX = event.getX();
                lastPanMouseY = event.getY();
                return;
            }

            String tool = toolBox.getValue();
            double[] imagePoint = sourceCanvasToImage(event.getX(), event.getY());
            if (imagePoint == null) {
                return;
            }
            if (TOOL_MAGIC_WAND.equals(tool)) {
                addMagicWandSelection((int) Math.round(imagePoint[0]), (int) Math.round(imagePoint[1]));
            } else if (TOOL_BRUSH_ADD.equals(tool) || TOOL_BRUSH_ERASE.equals(tool)) {
                applyBrush(imagePoint[0], imagePoint[1], TOOL_BRUSH_ADD.equals(tool));
            } else if (TOOL_LASSO.equals(tool)) {
                lassoPoints.clear();
                lassoPoints.add(imagePoint);
                drawCanvases();
            }
        });

        sourceCanvas.setOnMouseDragged(event -> {
            if (panning && event.getButton() == MouseButton.SECONDARY) {
                double dx = event.getX() - lastPanMouseX;
                double dy = event.getY() - lastPanMouseY;
                lastPanMouseX = event.getX();
                lastPanMouseY = event.getY();
                // convert pixel movement to image-space pan adjustments (in pixels)
                panX += dx;
                panY += dy;
                drawCanvases();
                return;
            }

            if (previewImage == null) {
                return;
            }
            String tool = toolBox.getValue();
            double[] imagePoint = sourceCanvasToImage(event.getX(), event.getY());
            if (imagePoint == null) {
                return;
            }
            if (TOOL_BRUSH_ADD.equals(tool) || TOOL_BRUSH_ERASE.equals(tool)) {
                applyBrush(imagePoint[0], imagePoint[1], TOOL_BRUSH_ADD.equals(tool));
            } else if (TOOL_LASSO.equals(tool)) {
                lassoPoints.add(imagePoint);
                drawCanvases();
            }
        });

        sourceCanvas.setOnMouseReleased(event -> {
            if (panning && event.getButton() == MouseButton.SECONDARY) {
                panning = false;
                return;
            }
            if (TOOL_LASSO.equals(toolBox.getValue()) && previewImage != null && lassoPoints.size() >= 3) {
                addLassoSelection();
                lassoPoints.clear();
                renderExtractionPreview();
                drawCanvases();
            }
        });

        sourceCanvas.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isShortcutDown()) {
                return;
            }
            double factor = event.getDeltaY() > 0 ? 1.18 : 1.0 / 1.18;
            setZoom(zoomSlider.getValue() * factor);
            event.consume();
        });
    }

    private void loadSelectedPhoto(PhotoItem photo) {
        lassoPoints.clear();
        lastExtractedPreview = null;
        panX = 0; panY = 0;
        if (photo == null) {
            originalImage = null;
            previewImage = null;
            selectionMask = null;
            selectionLabel.setText("Selected image: none");
            seedLabel.setText("Selection: none");
            drawCanvases();
            return;
        }
        try {
            selectionLabel.setText("Selected image: " + photo.getName());
            originalImage = ImageUtils.read(new File(photo.getFilePath()));
            previewImage = ImageUtils.resizeToFit(originalImage, 1200);
            selectionMask = new boolean[previewImage.getWidth() * previewImage.getHeight()];
            seedLabel.setText("Selection: empty");
            updateCanvasContentSizes();
            drawCanvases();
        } catch (Exception ex) {
            Dialogs.error("Load Error", "Could not load the selected image for object extraction.", ex);
        }
    }

    private void addMagicWandSelection(int x, int y) {
        if (previewImage == null || selectionMask == null) {
            return;
        }
        try {
            x = Math.min(previewImage.getWidth() - 1, Math.max(0, x));
            y = Math.min(previewImage.getHeight() - 1, Math.max(0, y));
            boolean[] wandMask = context.getObjectExtractionService().maskByColorSimilarity(previewImage, x, y, thresholdSlider.getValue());
            mergeMask(wandMask, true);
            seedLabel.setText("Magic wand seed: (" + x + ", " + y + ") | selected pixels: " + countSelectedPixels());
            renderExtractionPreview();
        } catch (Exception ex) {
            Dialogs.error("Selection Error", "Could not create color-similarity selection.", ex);
        }
    }

    private void applyBrush(double centerX, double centerY, boolean add) {
        if (previewImage == null || selectionMask == null) {
            return;
        }
        int radius = Math.max(1, (int) Math.round(brushSizeSlider.getValue() / 2.0));
        int minX = Math.max(0, (int) Math.floor(centerX - radius));
        int maxX = Math.min(previewImage.getWidth() - 1, (int) Math.ceil(centerX + radius));
        int minY = Math.max(0, (int) Math.floor(centerY - radius));
        int maxY = Math.min(previewImage.getHeight() - 1, (int) Math.ceil(centerY + radius));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                if (dx * dx + dy * dy <= radius * radius) {
                    selectionMask[y * previewImage.getWidth() + x] = add;
                }
            }
        }
        seedLabel.setText("Brush selection pixels: " + countSelectedPixels());
        // update preview incrementally (debounced also runs on slider changes)
        renderExtractionPreview();
        drawCanvases();
    }

    private void addLassoSelection() {
        double[] xs = new double[lassoPoints.size()];
        double[] ys = new double[lassoPoints.size()];
        for (int i = 0; i < lassoPoints.size(); i++) {
            xs[i] = lassoPoints.get(i)[0];
            ys[i] = lassoPoints.get(i)[1];
        }
        boolean[] lassoMask = context.getObjectExtractionService().lassoMask(previewImage.getWidth(), previewImage.getHeight(), xs, ys, lassoPoints.size());
        mergeMask(lassoMask, true);
        seedLabel.setText("Lasso selected pixels: " + countSelectedPixels());
    }

    private void mergeMask(boolean[] other, boolean add) {
        if (other == null || selectionMask == null) {
            return;
        }
        int limit = Math.min(selectionMask.length, other.length);
        for (int i = 0; i < limit; i++) {
            if (other[i]) {
                selectionMask[i] = add;
            }
        }
    }

    private void clearSelection() {
        if (selectionMask != null) {
            java.util.Arrays.fill(selectionMask, false);
        }
        lastExtractedPreview = null;
        seedLabel.setText("Selection: empty");
        drawCanvases();
    }

    private void invertSelection() {
        if (selectionMask == null) {
            return;
        }
        for (int i = 0; i < selectionMask.length; i++) {
            selectionMask[i] = !selectionMask[i];
        }
        seedLabel.setText("Selection inverted | selected pixels: " + countSelectedPixels());
        renderExtractionPreview();
        drawCanvases();
    }

    private void renderExtractionPreview() {
        if (previewImage == null || selectionMask == null || countSelectedPixels() == 0) {
            lastExtractedPreview = null;
            drawCanvases();
            return;
        }
        try {
            lastExtractedPreview = context.getObjectExtractionService().extractByMask(
                    previewImage,
                    selectionMask,
                    previewImage.getWidth(),
                    previewImage.getHeight(),
                    true
            );
            lastExtractedPreview = applyExtractionOptions(lastExtractedPreview);
            drawCanvases();
        } catch (Exception ex) {
            Dialogs.error("Extraction Error", "Could not extract the selected object preview.", ex);
        }
    }

    private BufferedImage extractAtOriginalResolution() {
        if (originalImage == null || previewImage == null || selectionMask == null || countSelectedPixels() == 0) {
            throw new IllegalStateException("Create a selection mask first.");
        }
        BufferedImage extracted = context.getObjectExtractionService().extractByMask(
                originalImage,
                selectionMask,
                previewImage.getWidth(),
                previewImage.getHeight(),
                true
        );
        return applyExtractionOptions(extracted);
    }

    private BufferedImage applyExtractionOptions(BufferedImage image) {
        BufferedImage output = image;
        if (enhanceCheck.isSelected()) {
            output = context.getObjectExtractionService().enhanceTransparentObject(output);
        }
        if (tintStrengthSlider.getValue() > 0.001 && tintColorPicker.getValue().getOpacity() > 0.001) {
            output = context.getObjectExtractionService().tintTransparentObject(output, tintColorPicker.getValue(), tintStrengthSlider.getValue());
        }
        if (outlineWidthSlider.getValue() >= 1.0) {
            output = context.getObjectExtractionService().addOutlineToAlpha(output, outlineColorPicker.getValue(), (int) Math.round(outlineWidthSlider.getValue()));
        }
        return output;
    }

    private void exportExtractedObject() {
        try {
            BufferedImage extracted;
            try {
                extracted = extractAtOriginalResolution(); // try full-res first
            } catch (IllegalStateException ise) {
                // no selection or other precondition - try to export the preview image if available
                if (lastExtractedPreview == null) {
                    Dialogs.warn("Nothing to Export", "Create a selection first before exporting.");
                    return;
                } else {
                    Dialogs.info("Exporting Preview", "Full-resolution extraction unavailable — exporting extracted preview image.");
                    extracted = lastExtractedPreview;
                }
            } catch (Exception ex) {
                // fallback to preview on any other extraction error
                if (lastExtractedPreview == null) {
                    throw ex;
                } else {
                    Dialogs.warn("Export Fallback", "Could not create full-resolution extraction; exporting preview instead.");
                    extracted = lastExtractedPreview;
                }
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Extracted Object");
            chooser.setInitialDirectory(AppPaths.EXPORT_DIR.toFile());
            if (!AppPaths.EXPORT_DIR.toFile().exists()) Files.createDirectories(AppPaths.EXPORT_DIR);
            String base = context.getSelectedPhoto() == null ? "object" : FileUtils.slugify(FileUtils.baseName(context.getSelectedPhoto().getName())) + "-object";
            chooser.setInitialFileName(base + ".png");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
            File file = chooser.showSaveDialog(getScene().getWindow());
            if (file == null) {
                return;
            }
            ImageUtils.write(extracted, file);
            context.setLatestExportFile(file);
            Dialogs.info("Export Complete", "Extracted object exported as transparent PNG. The original image was not changed.");
        } catch (Exception ex) {
            Dialogs.error("Export Error", "Could not export the extracted object.", ex);
        }
    }

    private void saveToAssetLibrary() {
        try {
            BufferedImage extracted = extractAtOriginalResolution();
            File asset = writeExtractedToAssetFolder(extracted);
            context.setLatestExportFile(asset);
            Dialogs.info("Saved to Asset Library", "The extracted object is now available from the asset-library folder and can be reused as a layer.");
        } catch (Exception ex) {
            // fallback to preview if full-res fails
            try {
                if (lastExtractedPreview != null) {
                    File asset = writeExtractedToAssetFolder(lastExtractedPreview);
                    context.setLatestExportFile(asset);
                    Dialogs.info("Saved (preview)", "Saved preview extracted object to asset library (full-res failed).");
                } else {
                    throw ex;
                }
            } catch (Exception inner) {
                Dialogs.error("Asset Save Error", "Could not save the extracted object into the asset library.", inner);
            }
        }
    }

    private void copyExtractedObject() {
        try {
            BufferedImage extracted = extractAtOriginalResolution();
            File stagedFile = context.getAssetLibraryService().stageClipboardImage(extracted, "copied-object");
            ClipboardContent content = new ClipboardContent();
            content.putImage(ImageUtils.toFxImage(extracted));
            content.putFiles(List.of(stagedFile));
            Clipboard.getSystemClipboard().setContent(content);
            context.setLatestExportFile(stagedFile);
            Dialogs.info("Copied", "The extracted object was copied as image data and as an actual PNG file. It can be pasted into another project or added as a layer.");
        } catch (Exception ex) {
            // fallback to preview
            try {
                if (lastExtractedPreview != null) {
                    File stagedFile = context.getAssetLibraryService().stageClipboardImage(lastExtractedPreview, "copied-object-preview");
                    ClipboardContent content = new ClipboardContent();
                    content.putImage(ImageUtils.toFxImage(lastExtractedPreview));
                    content.putFiles(List.of(stagedFile));
                    Clipboard.getSystemClipboard().setContent(content);
                    context.setLatestExportFile(stagedFile);
                    Dialogs.info("Copied (preview)", "Copied preview extracted object; full-res copy failed.");
                } else {
                    throw ex;
                }
            } catch (Exception inner) {
                Dialogs.error("Copy Error", "Could not copy the extracted object.", inner);
            }
        }
    }

    private void saveIntoLibrary() {
        try {
            BufferedImage extracted = extractAtOriginalResolution();
            String base = context.getSelectedPhoto() == null ? "object" : FileUtils.baseName(context.getSelectedPhoto().getName()) + "-object";
            var imported = context.getLibraryService().saveGeneratedImageToLibrary(extracted, base);
            context.addImportedPhotos(List.of(imported));
            context.setLatestExportFile(Path.of(imported.getFilePath()).toFile());
            Dialogs.info("Saved to Library", "The extracted object has been imported into the managed photo library.");
        } catch (Exception ex) {
            // fallback to preview
            try {
                if (lastExtractedPreview != null) {
                    String base = context.getSelectedPhoto() == null ? "object" : FileUtils.baseName(context.getSelectedPhoto().getName()) + "-object";
                    var imported = context.getLibraryService().saveGeneratedImageToLibrary(lastExtractedPreview, base);
                    context.addImportedPhotos(List.of(imported));
                    context.setLatestExportFile(Path.of(imported.getFilePath()).toFile());
                    Dialogs.info("Saved (preview)", "Saved preview extracted object into library (full-res failed).");
                } else {
                    throw ex;
                }
            } catch (Exception inner) {
                Dialogs.error("Save Error", "Could not save the extracted object into the library.", inner);
            }
        }
    }

    private File writeExtractedToAssetFolder(BufferedImage extracted) throws Exception {
        Files.createDirectories(AppPaths.EXTRACTED_OBJECT_DIR);
        String base = context.getSelectedPhoto() == null ? "object" : FileUtils.slugify(FileUtils.baseName(context.getSelectedPhoto().getName())) + "-object";
        String timestamp = LocalDateTime.now().toString().replace(':', '-');
        Path output = AppPaths.EXTRACTED_OBJECT_DIR.resolve(timestamp + "_" + base + "_" + UUID.randomUUID() + ".png");
        ImageUtils.write(extracted, output.toFile());
        return output.toFile();
    }

    private void drawCanvases() {
        updateCanvasContentSizes();
        drawSourceCanvas();
        drawResultCanvas();
    }

    private void updateCanvasContentSizes() {
        updateSourceContentSize();
        updateResultContentSize();
    }

    private void updateSourceContentSize() {
        double width = Math.max(420, sourceViewportWidth);
        double height = Math.max(360, sourceViewportHeight);
        if (previewImage != null) {
            double baseScale = Math.min(sourceViewportWidth / previewImage.getWidth(), sourceViewportHeight / previewImage.getHeight());
            double drawW = previewImage.getWidth() * baseScale * userZoom;
            double drawH = previewImage.getHeight() * baseScale * userZoom;
            width = Math.max(width, drawW + 80);
            height = Math.max(height, drawH + 80);
        }
        sourceCanvasPane.setPrefSize(width, height);
    }

    private void updateResultContentSize() {
        double width = Math.max(420, resultViewportWidth);
        double height = Math.max(360, resultViewportHeight);
        if (lastExtractedPreview != null) {
            double baseScale = Math.min(resultViewportWidth / lastExtractedPreview.getWidth(), resultViewportHeight / lastExtractedPreview.getHeight());
            double drawW = lastExtractedPreview.getWidth() * baseScale;
            double drawH = lastExtractedPreview.getHeight() * baseScale;
            width = Math.max(width, drawW + 80);
            height = Math.max(height, drawH + 80);
        }
        resultCanvasPane.setPrefSize(width, height);
    }

    private void drawSourceCanvas() {
        GraphicsContext gc = sourceCanvas.getGraphicsContext2D();
        double cw = sourceCanvas.getWidth();
        double ch = sourceCanvas.getHeight();
        gc.setFill(Color.rgb(241, 245, 249));
        gc.fillRect(0, 0, cw, ch);
        if (previewImage == null) {
            gc.setFill(Color.rgb(100, 116, 139));
            gc.setFont(Font.font(18));
            gc.fillText("Select an image in Repository first.", 28, 42);
            return;
        }

        sourceScale = Math.min(sourceViewportWidth / previewImage.getWidth(), sourceViewportHeight / previewImage.getHeight());
        // effective scale includes user zoom
        double effectiveScale = Math.max(0.0001, sourceScale * userZoom);
        double drawW = previewImage.getWidth() * effectiveScale;
        double drawH = previewImage.getHeight() * effectiveScale;

        // center and then apply pan offset (pan is in pixels on canvas)
        sourceOffsetX = (cw - drawW) / 2.0 + panX;
        sourceOffsetY = (ch - drawH) / 2.0 + panY;

        gc.drawImage(ImageUtils.toFxImage(previewImage), sourceOffsetX, sourceOffsetY, drawW, drawH);

        if (selectionMask != null) {
            Image overlay = ImageUtils.toFxImage(maskOverlayImage());
            gc.drawImage(overlay, sourceOffsetX, sourceOffsetY, drawW, drawH);
        }
        if (!lassoPoints.isEmpty()) {
            gc.setStroke(Color.rgb(250, 204, 21));
            gc.setLineWidth(2);
            for (int i = 1; i < lassoPoints.size(); i++) {
                double[] p0 = lassoPoints.get(i - 1);
                double[] p1 = lassoPoints.get(i);
                gc.strokeLine(sourceOffsetX + p0[0] * effectiveScale, sourceOffsetY + p0[1] * effectiveScale,
                        sourceOffsetX + p1[0] * effectiveScale, sourceOffsetY + p1[1] * effectiveScale);
            }
        }
    }

    private BufferedImage maskOverlayImage() {
        BufferedImage overlay = new BufferedImage(previewImage.getWidth(), previewImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int selected = 0x660096FF;
        for (int y = 0; y < previewImage.getHeight(); y++) {
            for (int x = 0; x < previewImage.getWidth(); x++) {
                if (selectionMask[y * previewImage.getWidth() + x]) {
                    overlay.setRGB(x, y, selected);
                }
            }
        }
        return overlay;
    }

    private void drawResultCanvas() {
        GraphicsContext gc = resultCanvas.getGraphicsContext2D();
        double cw = resultCanvas.getWidth();
        double ch = resultCanvas.getHeight();
        gc.setFill(Color.rgb(241, 245, 249));
        gc.fillRect(0, 0, cw, ch);
        if (lastExtractedPreview == null) {
            gc.setFill(Color.rgb(100, 116, 139));
            gc.setFont(Font.font(18));
            gc.fillText("Create a mask to preview the transparent extracted object.", 28, 42);
            return;
        }
        resultScale = Math.min(resultViewportWidth / lastExtractedPreview.getWidth(), resultViewportHeight / lastExtractedPreview.getHeight());
        double drawW = lastExtractedPreview.getWidth() * resultScale;
        double drawH = lastExtractedPreview.getHeight() * resultScale;
        resultOffsetX = (cw - drawW) / 2.0;
        resultOffsetY = (ch - drawH) / 2.0;
        drawCheckerboard(gc, resultOffsetX, resultOffsetY, drawW, drawH);
        gc.drawImage(ImageUtils.toFxImage(lastExtractedPreview), resultOffsetX, resultOffsetY, drawW, drawH);
    }

    private void drawCheckerboard(GraphicsContext gc, double x, double y, double w, double h) {
        double cell = 18;
        for (int row = 0; row * cell < h; row++) {
            for (int col = 0; col * cell < w; col++) {
                gc.setFill((row + col) % 2 == 0 ? Color.rgb(226, 232, 240) : Color.WHITE);
                gc.fillRect(x + col * cell, y + row * cell, Math.min(cell, w - col * cell), Math.min(cell, h - row * cell));
            }
        }
    }

    /**
     * Map canvas coordinates to image coordinates taking zoom & pan into account.
     */
    private double[] sourceCanvasToImage(double canvasX, double canvasY) {
        if (previewImage == null) {
            return null;
        }
        double cw = sourceCanvas.getWidth();
        double ch = sourceCanvas.getHeight();
        sourceScale = Math.min(sourceViewportWidth / previewImage.getWidth(), sourceViewportHeight / previewImage.getHeight());
        double effectiveScale = Math.max(0.0001, sourceScale * userZoom);
        double drawW = previewImage.getWidth() * effectiveScale;
        double drawH = previewImage.getHeight() * effectiveScale;
        double offsetX = (cw - drawW) / 2.0 + panX;
        double offsetY = (ch - drawH) / 2.0 + panY;

        double x = (canvasX - offsetX) / effectiveScale;
        double y = (canvasY - offsetY) / effectiveScale;
        if (x < 0 || y < 0 || x >= previewImage.getWidth() || y >= previewImage.getHeight()) {
            return null;
        }
        return new double[]{x, y};
    }

    private int countSelectedPixels() {
        if (selectionMask == null) {
            return 0;
        }
        int count = 0;
        for (boolean selected : selectionMask) {
            if (selected) {
                count++;
            }
        }
        return count;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void configureSlider(Slider slider, boolean integerLabels) {
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setBlockIncrement(integerLabels ? 1 : 0.01);
        slider.setMajorTickUnit((slider.getMax() - slider.getMin()) / 4.0);
    }
}

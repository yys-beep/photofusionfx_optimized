package com.photofusionfx.ui;

//ExtractorPane supports brush, lasso, magic wand selection and object extraction

import com.photofusionfx.AppContext;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import java.util.Locale;
import java.util.Optional;
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
    private final javafx.scene.control.ToggleGroup toolGroup = new javafx.scene.control.ToggleGroup();
    private final Slider thresholdSlider = new Slider(0.04, 0.40, 0.16);
    private final Slider brushSizeSlider = new Slider(4, 120, 30);
    private final ColorPicker outlineColorPicker = new ColorPicker(Color.WHITE);
    private final Slider outlineWidthSlider = new Slider(0, 30, 0);
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

        // Desired default widths (match screenshot, but user can resize)
        final double LEFT_DEFAULT_WIDTH = 260;
        final double RIGHT_DEFAULT_WIDTH = 320;

        // 1) LEFT PANEL (Tools & Selection)
        ScrollPane leftScroll = new ScrollPane(buildLeftPanel());
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setStyle("-fx-background-color: transparent;");
        leftScroll.setPrefWidth(LEFT_DEFAULT_WIDTH);
        leftScroll.setMinWidth(200); // allow shrink, but not too small
        leftScroll.setMaxWidth(Double.MAX_VALUE); // allow expand

        // 2) CENTER CANVAS
        SplitPane canvasArea = buildViews();

        // 3) RIGHT PANEL (Properties & Export)
        ScrollPane rightScroll = new ScrollPane(buildRightPanel());
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setStyle("-fx-background-color: transparent;");
        rightScroll.setPrefWidth(RIGHT_DEFAULT_WIDTH);
        rightScroll.setMinWidth(240);
        rightScroll.setMaxWidth(Double.MAX_VALUE);

        // MAIN SPLIT PANE
        SplitPane mainSplit = new SplitPane(leftScroll, canvasArea, rightScroll);
        setCenter(mainSplit);

        // Set divider positions ONCE after layout, so left/right start at the pixel widths above.
        // After that, user divider dragging is respected.
        Platform.runLater(() -> setDefaultMainSplit(mainSplit, LEFT_DEFAULT_WIDTH, RIGHT_DEFAULT_WIDTH));

        // TOP TOOLBAR
        Button toggleLeftBtn = new Button("👁 Hide Toolbox");
        Button toggleRightBtn = new Button("👁 Hide Properties");

        toggleLeftBtn.setOnAction(e -> {
            if (mainSplit.getItems().contains(leftScroll)) {
                mainSplit.getItems().remove(leftScroll);
                toggleLeftBtn.setText("👁 Show Toolbox");
            } else {
                mainSplit.getItems().add(0, leftScroll);
                Platform.runLater(() -> setDefaultMainSplit(mainSplit, LEFT_DEFAULT_WIDTH, RIGHT_DEFAULT_WIDTH));
                toggleLeftBtn.setText("👁 Hide Toolbox");
            }
        });

        toggleRightBtn.setOnAction(e -> {
            if (mainSplit.getItems().contains(rightScroll)) {
                mainSplit.getItems().remove(rightScroll);
                toggleRightBtn.setText("👁 Show Properties");
            } else {
                mainSplit.getItems().add(rightScroll);
                Platform.runLater(() -> setDefaultMainSplit(mainSplit, LEFT_DEFAULT_WIDTH, RIGHT_DEFAULT_WIDTH));
                toggleRightBtn.setText("👁 Hide Properties");
            }
        });

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topToolbar = new HBox(toggleLeftBtn, topSpacer, toggleRightBtn);
        topToolbar.setAlignment(Pos.CENTER);
        topToolbar.setPadding(new Insets(0, 0, 10, 0));
        setTop(topToolbar);

        // BOTTOM TOOLBAR (ZOOM CONTROLS)
        Button fitZoomButton = new Button("Fit");
        fitZoomButton.setOnAction(e -> resetZoom());
        Button zoom200Button = new Button("200%");
        zoom200Button.setOnAction(e -> setZoom(2.0));
        Button zoom400Button = new Button("400%");
        zoom400Button.setOnAction(e -> setZoom(4.0));

        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(1.0);
        zoomSlider.setBlockIncrement(0.25);
        zoomSlider.setPrefWidth(400);
        zoomSlider.valueProperty().addListener((obs, ov, nv) -> {
            userZoom = nv.doubleValue();
            zoomLabel.setText(String.format("%d%%", (int) Math.round(userZoom * 100)));
            updateCanvasContentSizes();
            drawCanvases();
        });

        HBox zoomBar = new HBox(15, new Label("Zoom:"), zoomSlider, zoomLabel, fitZoomButton, zoom200Button, zoom400Button);
        zoomBar.setAlignment(Pos.CENTER);
        zoomBar.setPadding(new Insets(15, 10, 5, 10));
        setBottom(zoomBar);

        attachCanvasHandlers();
        debounce.setOnFinished(e -> renderExtractionPreview());
        thresholdSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (TOOL_MAGIC_WAND.equals(getActiveTool())) {
                debounce.playFromStart();
            }
        });
        outlineColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        outlineWidthSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        largeSelectionAreaCheck.selectedProperty().addListener((obs, oldValue, newValue) -> applySelectionAreaMode());
        tintColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        tintStrengthSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());

        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> {
            panX = 0;
            panY = 0;
            resetZoom();
            loadSelectedPhoto(newValue);
        });
        loadSelectedPhoto(context.getSelectedPhoto());
    }

    /**
     * Sets divider positions so left is ~leftWidthPx and right is ~rightWidthPx.
     * Runs once on startup (and when re-showing panes), but does NOT keep forcing widths,
     * so the user can drag resize freely afterwards.
     */
    private void setDefaultMainSplit(SplitPane mainSplit, double leftWidthPx, double rightWidthPx) {
        double totalW = mainSplit.getWidth();
        if (totalW <= 0) return;

        // divider1: leftWidth / totalWidth
        double d1 = leftWidthPx / totalW;

        // divider2: (totalWidth - rightWidth) / totalWidth
        double d2 = (totalW - rightWidthPx) / totalW;

        // clamp
        d1 = Math.max(0.05, Math.min(0.90, d1));
        d2 = Math.max(d1 + 0.05, Math.min(0.95, d2));

        mainSplit.setDividerPositions(d1, d2);
    }

    private VBox buildLeftPanel() {
        Button clearButton = new Button("Clear Selection");
        clearButton.setOnAction(e -> clearSelection());

        Button invertButton = new Button("Invert Selection");
        invertButton.setOnAction(e -> invertSelection());

        javafx.scene.control.ToggleButton wandBtn = new javafx.scene.control.ToggleButton("Magic Wand");
        wandBtn.setUserData(TOOL_MAGIC_WAND);
        wandBtn.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.control.ToggleButton brushAddBtn = new javafx.scene.control.ToggleButton("Brush Add");
        brushAddBtn.setUserData(TOOL_BRUSH_ADD);
        brushAddBtn.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.control.ToggleButton brushEraseBtn = new javafx.scene.control.ToggleButton("Brush Erase");
        brushEraseBtn.setUserData(TOOL_BRUSH_ERASE);
        brushEraseBtn.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.control.ToggleButton lassoBtn = new javafx.scene.control.ToggleButton("Lasso");
        lassoBtn.setUserData(TOOL_LASSO);
        lassoBtn.setMaxWidth(Double.MAX_VALUE);

        wandBtn.setToggleGroup(toolGroup);
        brushAddBtn.setToggleGroup(toolGroup);
        brushEraseBtn.setToggleGroup(toolGroup);
        lassoBtn.setToggleGroup(toolGroup);
        wandBtn.setSelected(true);

        VBox toolButtons = new VBox(5, new Label("Active Tool:"), wandBtn, brushAddBtn, brushEraseBtn, lassoBtn);

        VBox controls = new VBox(15,
                selectionLabel,
                new Separator(),
                new Label("Selection Tools"),
                toolButtons,
                sliderRow("Brush Size", brushSizeSlider),
                sliderRow("Wand Threshold", thresholdSlider),
                new Separator(),
                new Label("Selection Status"),
                seedLabel,
                new HBox(10, clearButton, invertButton)
        );
        controls.setPadding(new Insets(4, 16, 4, 16));
        return controls;
    }

    private VBox buildRightPanel() {
        Button exportPngButton = new Button("Export PNG");
        exportPngButton.setOnAction(e -> exportExtractedObject());
        exportPngButton.getStyleClass().add("success-button");
        exportPngButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(exportPngButton, Priority.ALWAYS);

        Button saveAssetButton = new Button("Save to Asset Library");
        saveAssetButton.setOnAction(e -> saveToAssetLibrary());
        saveAssetButton.getStyleClass().add("success-button");
        saveAssetButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(saveAssetButton, Priority.ALWAYS);

        Button copyButton = new Button("Copy Object File");
        copyButton.setOnAction(e -> copyExtractedObject());
        copyButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(copyButton, Priority.ALWAYS);

        Button saveToLibraryButton = new Button("Save to Repository");
        saveToLibraryButton.setOnAction(e -> saveIntoLibrary());
        saveToLibraryButton.getStyleClass().add("success-button");
        saveToLibraryButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(saveToLibraryButton, Priority.ALWAYS);

        Label headerLabel = new Label("Extraction Options");
        headerLabel.setStyle("-fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> {
            outlineColorPicker.setValue(Color.WHITE);
            outlineWidthSlider.setValue(0);
            tintColorPicker.setValue(Color.TRANSPARENT);
            tintStrengthSlider.setValue(0);
            renderExtractionPreview();
        });

        HBox optionsHeader = new HBox(headerLabel, spacer, resetBtn);
        optionsHeader.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(15,
                optionsHeader,
                new Label("Extraction Options"),
                labelledControl("Outline Color", outlineColorPicker),
                sliderRow("Outline Width", outlineWidthSlider),
                new Separator(),
                labelledControl("Tint / Scenario Color", tintColorPicker),
                sliderRow("Tint Strength", tintStrengthSlider),
                new Separator(),
                largeSelectionAreaCheck,
                new Separator(),
                new Label("Export & Save Actions"),
                new HBox(10, saveAssetButton, saveToLibraryButton),
                new HBox(10, copyButton, exportPngButton)
        );
        controls.setPadding(new Insets(4, 16, 4, 16));
        return controls;
    }

    private VBox sliderRow(String title, Slider slider) {
        Label valueLabel = new Label();
        String format = title.equals("Wand Threshold") || title.equals("Tint Strength") ? "%.2f" : "%.0f";
        valueLabel.textProperty().bind(slider.valueProperty().asString(format));
        valueLabel.setMinWidth(Region.USE_PREF_SIZE);

        Label titleLabel = new Label(title);
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleLabel, spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, header, slider);
    }

    private VBox labelledControl(String title, javafx.scene.Node node) {
        Label titleLabel = new Label(title);
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);
        return new VBox(6, titleLabel, node);
    }

    private void resetZoom() {
        setZoom(1.0);
        panX = 0;
        panY = 0;
    }

    private void setZoom(double zoom) {
        zoomSlider.setValue(clamp(zoom, zoomSlider.getMin(), zoomSlider.getMax()));
    }

    private SplitPane buildViews() {
        ScrollPane left = new ScrollPane(wrapCanvas(sourceCanvas, sourceCanvasPane));
        left.setFitToWidth(false);
        left.setFitToHeight(false);
        left.setPannable(false);
        left.getStyleClass().add("preview-box");
        left.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        left.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        ScrollPane right = new ScrollPane(wrapCanvas(resultCanvas, resultCanvasPane));
        right.setFitToWidth(false);
        right.setFitToHeight(false);
        right.setPannable(false);
        right.getStyleClass().add("preview-box");
        right.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        right.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        bindCanvasToViewport(sourceCanvasPane, left, true);
        bindCanvasToViewport(resultCanvasPane, right, false);

        VBox leftBox = new VBox(8, new Label("Source image selection mask: click / brush / lasso (Right-Click to Pan)"), left);
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
            if (previewImage == null) return;

            if (event.getButton() == MouseButton.SECONDARY) {
                panning = true;
                lastPanMouseX = event.getX();
                lastPanMouseY = event.getY();
                return;
            }

            String tool = getActiveTool();
            double[] imagePoint = sourceCanvasToImage(event.getX(), event.getY());
            if (imagePoint == null) return;

            if (TOOL_MAGIC_WAND.equals(tool)) {
                addMagicWandSelection((int) Math.round(imagePoint[0]), (int) Math.round(imagePoint[1]));
            } else if (TOOL_BRUSH_ADD.equals(tool) || TOOL_BRUSH_ERASE.equals(tool)) {
                applyBrush(imagePoint[0], imagePoint[1], TOOL_BRUSH_ADD.equals(tool));
            } else if (TOOL_LASSO.equals(tool)) {
                lassoPoints.clear();
                lassoPoints.add(imagePoint);
                drawCanvases();
            }
            event.consume();
        });

        sourceCanvas.setOnMouseDragged(event -> {
            if (panning && event.getButton() == MouseButton.SECONDARY) {
                double dx = event.getX() - lastPanMouseX;
                double dy = event.getY() - lastPanMouseY;
                lastPanMouseX = event.getX();
                lastPanMouseY = event.getY();
                panX += dx;
                panY += dy;
                drawCanvases();
                return;
            }

            if (previewImage == null) return;

            String tool = getActiveTool();
            double[] imagePoint = sourceCanvasToImage(event.getX(), event.getY());
            if (imagePoint == null) return;

            if (TOOL_BRUSH_ADD.equals(tool) || TOOL_BRUSH_ERASE.equals(tool)) {
                applyBrush(imagePoint[0], imagePoint[1], TOOL_BRUSH_ADD.equals(tool));
            } else if (TOOL_LASSO.equals(tool)) {
                lassoPoints.add(imagePoint);
                drawCanvases();
            }

            event.consume();
        });

        sourceCanvas.setOnMouseReleased(event -> {
            if (panning && event.getButton() == MouseButton.SECONDARY) {
                panning = false;
                return;
            }
            if (TOOL_LASSO.equals(getActiveTool()) && previewImage != null && lassoPoints.size() >= 3) {
                addLassoSelection();
                lassoPoints.clear();
                renderExtractionPreview();
                drawCanvases();
            }
        });

        sourceCanvas.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isShortcutDown()) return;
            double factor = event.getDeltaY() > 0 ? 1.18 : 1.0 / 1.18;
            setZoom(zoomSlider.getValue() * factor);
            event.consume();
        });
    }

    private void loadSelectedPhoto(PhotoItem photo) {
        lassoPoints.clear();
        lastExtractedPreview = null;
        panX = 0;
        panY = 0;

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
        if (previewImage == null || selectionMask == null) return;
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
        if (previewImage == null || selectionMask == null) return;

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
        if (other == null || selectionMask == null) return;
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
        if (selectionMask == null) return;
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
                extracted = extractAtOriginalResolution();
            } catch (IllegalStateException ise) {
                if (lastExtractedPreview == null) {
                    Dialogs.warn("Nothing to Export", "Create a selection first before exporting.");
                    return;
                } else {
                    Dialogs.info("Exporting Preview", "Full-resolution extraction unavailable — exporting extracted preview image.");
                    extracted = lastExtractedPreview;
                }
            } catch (Exception ex) {
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
            if (file == null) return;

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
            File rawFile = writeExtractedToAssetFolder(extracted);
            File managedAsset = context.getAssetLibraryService().importAsset(rawFile);
            context.setLatestExportFile(managedAsset);
            Dialogs.info("Saved to Asset Library", "The extracted object is now available from the asset library and can be reused as a layer.");
        } catch (Exception ex) {
            try {
                if (lastExtractedPreview != null) {
                    File rawFile = writeExtractedToAssetFolder(lastExtractedPreview);
                    File managedAsset = context.getAssetLibraryService().importAsset(rawFile);
                    context.setLatestExportFile(managedAsset);
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
        double effectiveScale = Math.max(0.0001, sourceScale * userZoom);
        double drawW = previewImage.getWidth() * effectiveScale;
        double drawH = previewImage.getHeight() * effectiveScale;

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

    private double[] sourceCanvasToImage(double canvasX, double canvasY) {
        if (previewImage == null) return null;
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
        if (selectionMask == null) return 0;
        int count = 0;
        for (boolean selected : selectionMask) if (selected) count++;
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

    private String getActiveTool() {
        if (toolGroup.getSelectedToggle() != null) {
            return (String) toolGroup.getSelectedToggle().getUserData();
        }
        return TOOL_MAGIC_WAND;
    }

}

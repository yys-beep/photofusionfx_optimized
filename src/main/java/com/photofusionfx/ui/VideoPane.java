package com.photofusionfx.ui;

//VideoPane supports text, shape and image layers draggable over video preview

import com.photofusionfx.AppContext;
import com.photofusionfx.model.LayerType;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.model.ProjectLayer;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.geometry.Orientation;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.geometry.Orientation;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.SplitPane;

/**
 * VideoPane — fully reworked layout with graphical layer editing support.
 * 
 * Features:
 *  • Entire panel wrapped in a ScrollPane so nothing is ever hidden.
 *  • Controls split into clearly-labelled collapsible TitledPanes.
 *  • Button labels shortened and made consistent — no more truncation.
 *  • Sequence management, layer editor, playback controls each in their own section.
 *  • Progress bar and status label are always visible during rendering.
 *  • Drag-and-drop layers to move them around canvas.
 *  • Live layer properties panel for editing position, size, colors, opacity.
 *  • Full-screen mode with proper exit and video size restoration.
 *  • All existing logic preserved exactly.
 */
public class VideoPane extends BorderPane {

    // ── Context & data ──────────────────────────────────────────────────────
    private final AppContext context;
    private final FilteredList<PhotoItem> filteredPhotos;
    private final ObservableList<PhotoItem> sequenceItems = FXCollections.observableArrayList();

    // ── Image lists ──────────────────────────────────────────────────────────
    private final ListView<PhotoItem> availableList  = new ListView<>();
    private final ListView<PhotoItem> sequenceList   = new ListView<>(sequenceItems);
    private final ComboBox<String>    photoFilterBox = new ComboBox<>();

    // ── Video layer canvas & controls ────────────────────────────────────────
    private static final int VIDEO_LAYER_REFERENCE_WIDTH  = 1280;
    private static final int VIDEO_LAYER_REFERENCE_HEIGHT = 720;
    private TabPane centerTabPane;

    private final ObservableList<ProjectLayer> videoLayers    = FXCollections.observableArrayList();
    private final ListView<ProjectLayer>        videoLayerList = new ListView<>(videoLayers);
    private final Canvas     videoLayerCanvas       = new Canvas(480, 270);
    private final Slider     layerPreviewZoomSlider = new Slider(0.5, 3.0, 1.0);
    private final Label      layerPreviewZoomLabel  = new Label("100%");
    private final TextField  videoLayerTextField    = new TextField("Video text");
    private final ComboBox<String> videoFontFamilyBox   = new ComboBox<>();
    private final Slider     videoFontSizeSlider    = new Slider(10, 800, 54);
    private final ColorPicker videoLayerFillPicker  = new ColorPicker(Color.WHITE);
    private final ColorPicker videoLayerStrokePicker= new ColorPicker(Color.BLACK);
    private final Slider     videoLayerStrokeSlider = new Slider(0, 20, 0);
    private final Slider     videoLayerOpacitySlider= new Slider(0, 1, 1);

    // Layer properties panel controls
    private final Spinner<Integer> layerPropXSpinner = new Spinner<>(0, 2560, 0);
    private final Spinner<Integer> layerPropYSpinner = new Spinner<>(0, 1440, 0);
    private final Spinner<Integer> layerPropWSpinner = new Spinner<>(10, 2560, 100);
    private final Spinner<Integer> layerPropHSpinner = new Spinner<>(10, 1440, 100);
    private final CheckBox layerVisibilityCheck = new CheckBox("Visible");

    private double videoLayerScale   = 1.0;
    private double videoLayerOffsetX = 0.0;
    private double videoLayerOffsetY = 0.0;
    private ProjectLayer draggedVideoLayer;
    private double draggedVideoLayerOffsetX;
    private double draggedVideoLayerOffsetY;
    private boolean syncingLayerProperties = false;

    // ── Video encode settings ─────────────────────────────────────────────────
    private final Spinner<Integer> secondsSpinner = new Spinner<>();
    private final Spinner<Integer> fpsSpinner     = new Spinner<>();
    private final ComboBox<String> resolutionBox  = new ComboBox<>();

    // ── Progress / status ─────────────────────────────────────────────────────
    private final ProgressBar progressBar   = new ProgressBar(0);
    private final Label        renderStatus  = new Label("Ready");

    // ── Media player ─────────────────────────────────────────────────────────
    private final MediaView mediaView       = new MediaView();
    private final Slider    seekSlider      = new Slider(0, 1000, 0);
    private final Label     timeLabel       = new Label("00:00 / 00:00");
    private final Button    playPauseButton = new Button("▶  Play");
    private final Button    skipBackButton  = new Button("◀  -5s");
    private final Button    skipFwdButton   = new Button("+5s  ▶");
    private final Button    fullScrButton   = new Button("⛶  Full Screen");

    // ── Action buttons ────────────────────────────────────────────────────────
    private final Button previewButton = new Button("▶  Generate Preview");
    private final Button exportButton  = new Button("💾  Save Video");

    private MediaPlayer mediaPlayer;
    private Duration    mediaDuration   = Duration.UNKNOWN;
    private File        tempPreviewFile = null;
    private BorderPane  playerPane;
    private VBox        playerBox;
    private Stage       fullScreenStage;

    // near other fields
    private WritableImage currentVideoFrame = null;
    
    public VideoPane(AppContext context) {
        this.context       = context;
        this.filteredPhotos = new FilteredList<>(context.getPhotoLibrary(), PhotoItem::isFavorite);

        configureVideoLayerControls();
        initialiseSpinnersAndCombos();
        initialiseMediaButtons();

// 1. RIGHT SIDEBAR CONTAINER
        VBox rightSidebar = new VBox();
        rightSidebar.setPadding(new Insets(10));
        
        ScrollPane rightScroll = new ScrollPane(rightSidebar);
        rightScroll.setFitToWidth(true);
        rightScroll.setMinWidth(300);
        rightScroll.setStyle("-fx-background-color: transparent;");

        // Define Sidebars content
        VBox playerSidebar = new VBox(10, buildRenderSettingsSection(), buildLayerPropertiesPanel());
        VBox editorSidebar = buildEditorSidebar(); 
        
        // Set default view to Editor (Add the whole box, not just its children)
        rightSidebar.getChildren().add(editorSidebar); 

        // 2. CENTER AREA: Visuals
        centerTabPane = new TabPane();
        centerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        Tab editorTab = new Tab("🖌 Layout Editor", buildVideoLayerEditor());
        Tab playerTab = new Tab("▶ Video Player", buildPlayerSection());
        centerTabPane.getTabs().addAll(editorTab, playerTab);
        
// --- FIXED: DYNAMIC SIDEBAR LISTENER ---
        centerTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            rightSidebar.getChildren().clear(); // Clear the current view
            if (newTab == playerTab) {
                rightSidebar.getChildren().add(playerSidebar); // Show player tools
            } else {
                rightSidebar.getChildren().add(editorSidebar); // Show editor tools
                
                // --- NEW FIX: Clear the leftover video frame and redraw the timeline image! ---
                currentVideoFrame = null;
                drawVideoLayerCanvas();
            }
        });

        VBox centerContainer = new VBox(centerTabPane);
        centerContainer.setPadding(new Insets(10, 15, 10, 15));
        VBox.setVgrow(centerTabPane, Priority.ALWAYS);

        // 3. BOTTOM AREA: Sequence Timeline
        SplitPane bottomTimeline = buildSequenceSection();

        SplitPane verticalSplit = new SplitPane(centerContainer, bottomTimeline);
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.setDividerPositions(0.75); 

        SplitPane mainSplit = new SplitPane(verticalSplit, rightScroll);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.75); 

        setCenter(mainSplit);

        // Auto-populate
        context.getPhotoLibrary().addListener(
            (javafx.collections.ListChangeListener<? super PhotoItem>) c -> {
                if (sequenceItems.isEmpty() && !filteredPhotos.isEmpty()) loadAllFiltered();
            });
        sequenceItems.addListener(
            (javafx.collections.ListChangeListener<? super PhotoItem>) c -> drawVideoLayerCanvas());
        if (!filteredPhotos.isEmpty()) loadAllFiltered();
    }



// ─── Section 1: Sequence management (Horizontal Filmstrip) ──────────────
    private SplitPane buildSequenceSection() {
        // --- 1. NEW FILTER LOGIC ---
        Label filterLabel = new Label("Show:");
        photoFilterBox.getItems().setAll("Favourite Photos", "Annotated Photos", "All Photos");
        photoFilterBox.getSelectionModel().selectFirst();
        photoFilterBox.valueProperty().addListener((obs, old, val) ->
            filteredPhotos.setPredicate(p -> {
                if ("All Photos".equals(val)) return true;
                if ("Annotated Photos".equals(val)) return p.getAnnotation() != null && !p.getAnnotation().isBlank();
                return p.isFavorite();
            })
        );

        availableList.setItems(filteredPhotos);
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        sequenceList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        availableList.setOrientation(Orientation.HORIZONTAL);
        sequenceList.setOrientation(Orientation.HORIZONTAL);
        availableList.setCellFactory(l -> thumbnailCell());
        sequenceList.setCellFactory(l -> thumbnailCell());
        
        // Shorter default heights
        availableList.setPrefHeight(90);
        sequenceList.setPrefHeight(90);

        Button addBtn     = iconBtn("⬇ Add to Timeline", () -> addSelectedToSequence());
        Button loadAllBtn = iconBtn("⬇ Load All", () -> loadAllFiltered());
        Button removeBtn  = iconBtn("⬆ Remove", () -> removeSelectedFromSequence());
        Button upBtn      = iconBtn("◀ Move Left", () -> moveSelected(-1));
        Button downBtn    = iconBtn("Move Right ▶", () -> moveSelected(1));
        Button clearBtn   = iconBtn("🗑 Clear", () -> sequenceItems.clear());

        Region spacer1 = new Region(); HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region(); HBox.setHgrow(spacer2, Priority.ALWAYS);

        // --- 2. WRAP IN TITLED PANES FOR HIDING ---
        HBox topControls = new HBox(10, spacer1, filterLabel, photoFilterBox, loadAllBtn, addBtn);
        topControls.setAlignment(Pos.CENTER_RIGHT);
        VBox topContent = new VBox(8, topControls, availableList);
        topContent.setPadding(new Insets(5));
        TitledPane availablePane = new TitledPane("📷 Available Photos", topContent);

        HBox bottomControls = new HBox(10, spacer2, upBtn, downBtn, removeBtn, clearBtn);
        bottomControls.setAlignment(Pos.CENTER_RIGHT);
        VBox bottomContent = new VBox(8, bottomControls, sequenceList);
        bottomContent.setPadding(new Insets(5));
        TitledPane sequencePane = new TitledPane("🎬 Video Sequence Timeline", bottomContent);

        // --- 3. WRAP IN SPLIT PANE FOR RESIZING ---
        SplitPane timelineSplit = new SplitPane(availablePane, sequencePane);
        timelineSplit.setOrientation(Orientation.VERTICAL);
        timelineSplit.setDividerPositions(0.5); // 50/50 split between the two timelines
        
        return timelineSplit;
    }

    // ─── Section 2: Render settings + action buttons ──────────────────────────
private TitledPane buildRenderSettingsSection() {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        secondsSpinner.setPrefWidth(80);
        fpsSpinner.setPrefWidth(80);
        resolutionBox.setPrefWidth(160);

        grid.add(new Label("Secs/image:"), 0, 0); grid.add(secondsSpinner, 1, 0);
        grid.add(new Label("Frame rate:"), 0, 1); grid.add(fpsSpinner, 1, 1);
        grid.add(new Label("Resolution:"), 0, 2); grid.add(resolutionBox, 0, 3, 2, 1);

        renderStatus.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        previewButton.getStyleClass().add("primary-button");
        exportButton.getStyleClass().add("success-button");
        exportButton.setDisable(true);
        previewButton.setOnAction(e -> generatePreview());
        exportButton.setOnAction(e -> exportVideo());
        
        previewButton.setMaxWidth(Double.MAX_VALUE);
        exportButton.setMaxWidth(Double.MAX_VALUE);
        
        // Render status label is now right below the buttons!
        VBox actionRow = new VBox(8, previewButton, exportButton, renderStatus); 

        VBox content = new VBox(15, grid, actionRow);
        content.setPadding(new Insets(10));
        
        TitledPane tp = new TitledPane("⚙ Render Settings", content);
        tp.setExpanded(true);
        return tp;
    }


// ─── Section 5: Preview player ────────────────────────────────────────────
    private VBox buildPlayerSection() {
        Label hint = new Label("💡 Generate a preview from the sidebar. When finished, use the controls below to review your video.");
        hint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        buildPlayerPane();
        playerBox = new VBox(12, hint, playerPane);
        playerBox.setPadding(new Insets(15)); // Add padding so it looks great inside the Tab
        VBox.setVgrow(playerPane, Priority.ALWAYS);

        playerPane.setMinHeight(320);
        playerPane.setPrefHeight(380);
        return playerBox;
    }

// ─── Section 4: Draggable graphical layers (Canvas Only) ────────────────
    private VBox buildVideoLayerEditor() {
        StackPane canvasContainer = new StackPane(videoLayerCanvas);
        canvasContainer.getStyleClass().add("preview-box");
        canvasContainer.setMinHeight(150); 
        canvasContainer.setMinWidth(250);

        videoLayerCanvas.widthProperty().bind(canvasContainer.widthProperty());
        videoLayerCanvas.heightProperty().bind(canvasContainer.heightProperty());
        videoLayerCanvas.widthProperty().addListener((obs, ov, nv) -> drawVideoLayerCanvas());
        videoLayerCanvas.heightProperty().addListener((obs, ov, nv) -> drawVideoLayerCanvas());

        VBox box = new VBox(canvasContainer);
        VBox.setVgrow(canvasContainer, Priority.ALWAYS);
        box.setPadding(new Insets(10));
        
        drawVideoLayerCanvas();
        return box;
    }

// ─── Section 4: Layer Properties (Sidebar) ───────────────────────────────
    private TitledPane buildLayerPropertiesPanel() {
        layerPropXSpinner.setEditable(true);
        layerPropYSpinner.setEditable(true);
        layerPropWSpinner.setEditable(true);
        layerPropHSpinner.setEditable(true);
        layerVisibilityCheck.setSelected(true);

        layerPropXSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropYSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropWSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropHSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerVisibilityCheck.selectedProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("X:"), 0, 0); grid.add(layerPropXSpinner, 1, 0);
        grid.add(new Label("Y:"), 0, 1); grid.add(layerPropYSpinner, 1, 1);
        grid.add(new Label("Width:"), 0, 2); grid.add(layerPropWSpinner, 1, 2);
        grid.add(new Label("Height:"), 0, 3); grid.add(layerPropHSpinner, 1, 3);
        grid.add(layerVisibilityCheck, 0, 4, 2, 1);

        VBox content = new VBox(10, grid);
        content.setPadding(new Insets(10));
        
        TitledPane tp = new TitledPane("🛠 Layer Properties", content);
        tp.setExpanded(true); 
        return tp;
    }

    private void updateLayerFromProperties() {
        if (syncingLayerProperties) {
            return;
        }
        ProjectLayer layer = videoLayerList.getSelectionModel().getSelectedItem();
        if (layer == null) return;

        layer.setX(layerPropXSpinner.getValue());
        layer.setY(layerPropYSpinner.getValue());
        layer.setWidth(layerPropWSpinner.getValue());
        layer.setHeight(layerPropHSpinner.getValue());
        layer.setVisible(layerVisibilityCheck.isSelected());

        videoLayerList.refresh();
        drawVideoLayerCanvas();
    }

    private void updateLayerPropertiesFromSelection(ProjectLayer layer) {
        syncingLayerProperties = true;
        try {
            if (layer == null) {
                layerPropXSpinner.getValueFactory().setValue(0);
                layerPropYSpinner.getValueFactory().setValue(0);
                layerPropWSpinner.getValueFactory().setValue(0);
                layerPropHSpinner.getValueFactory().setValue(0);
                layerVisibilityCheck.setSelected(true);
                return;
            }

            if (layer.getType() == LayerType.TEXT) {
                updateTextLayerBounds(layer);
            }
            layerPropXSpinner.getValueFactory().setValue((int) layer.getX());
            layerPropYSpinner.getValueFactory().setValue((int) layer.getY());
            layerPropWSpinner.getValueFactory().setValue((int) layer.getWidth());
            layerPropHSpinner.getValueFactory().setValue((int) layer.getHeight());
            layerVisibilityCheck.setSelected(layer.isVisible());
        } finally {
            syncingLayerProperties = false;
        }
    }


    private void buildPlayerPane() {
        mediaView.setPreserveRatio(true);

        seekSlider.valueChangingProperty().addListener((obs, ov, changing) -> {
            if (!changing) seekToSliderPosition();
        });
        seekSlider.setOnMouseReleased(e -> seekToSliderPosition());

        playPauseButton.getStyleClass().add("button");
        skipBackButton.getStyleClass().add("button");
        skipFwdButton.getStyleClass().add("button");
        fullScrButton.getStyleClass().add("button");

        playPauseButton.setMinWidth(100);
        skipBackButton.setMinWidth(80);
        skipFwdButton.setMinWidth(80);
        fullScrButton.setMinWidth(120);

        playPauseButton.setDisable(true);
        skipBackButton.setDisable(true);
        skipFwdButton.setDisable(true);
        fullScrButton.setDisable(true);
        seekSlider.setDisable(true);

        playPauseButton.setOnAction(e -> togglePlayback());
        skipBackButton.setOnAction(e -> skipTime(-5));
        skipFwdButton.setOnAction(e -> skipTime(5));
        fullScrButton.setOnAction(e -> toggleFullScreen());

        // --- THIS IS THE HBOX THAT WAS MISSING ---
        HBox controls = new HBox(8, skipBackButton, playPauseButton, skipFwdButton, seekSlider, timeLabel, fullScrButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);
        controls.setPadding(new Insets(10, 0, 0, 0));
        // -----------------------------------------

        Label placeholder = new Label("No preview yet.\nClick Generate Preview to render the video.");
        placeholder.setTextAlignment(TextAlignment.CENTER);
        placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        StackPane videoContainer = new StackPane(placeholder, mediaView);
        // MATCHES CANVAS STYLING EXACTLY
        videoContainer.getStyleClass().add("preview-box");
        videoContainer.setMinHeight(150);
        videoContainer.setMinWidth(250);

        mediaView.visibleProperty().bind(mediaView.mediaPlayerProperty().isNotNull());
        placeholder.visibleProperty().bind(mediaView.mediaPlayerProperty().isNull());

        mediaView.fitWidthProperty().bind(videoContainer.widthProperty());
        mediaView.fitHeightProperty().bind(videoContainer.heightProperty());

        playerPane = new BorderPane();
        playerPane.setCenter(videoContainer);
        playerPane.setBottom(controls); // Now Java knows what 'controls' is!
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Initialisers
    // ═════════════════════════════════════════════════════════════════════════
    private void initialiseSpinnersAndCombos() {
        secondsSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 3));
        fpsSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(12, 30, 24));
        secondsSpinner.setEditable(true);
        fpsSpinner.setEditable(true);

        resolutionBox.getItems().addAll(
            "640×360  (360p)",
            "1280×720  (720p)  ← default",
            "1920×1080  (1080p)");
        resolutionBox.getSelectionModel().select(1);
    }

    private void initialiseMediaButtons() {
        // Already handled in buildPlayerPane()
    }

    private void configureVideoLayerControls() {
        videoFontFamilyBox.getItems().setAll(Font.getFamilies());
        videoFontFamilyBox.getSelectionModel().select(Font.getDefault().getFamily());

        videoFontSizeSlider.setShowTickLabels(true);
        videoFontSizeSlider.setShowTickMarks(true);
        videoFontSizeSlider.setMajorTickUnit(40);

        videoLayerStrokeSlider.setShowTickLabels(true);
        videoLayerStrokeSlider.setShowTickMarks(true);
        videoLayerStrokeSlider.setMajorTickUnit(5);

        videoLayerOpacitySlider.setShowTickLabels(true);
        videoLayerOpacitySlider.setShowTickMarks(true);
        videoLayerOpacitySlider.setMajorTickUnit(0.25);

        videoLayerList.setPrefHeight(115);
        videoLayerList.setCellFactory(list -> new ListCell<>() {
            {
                setOnDragDetected(event -> {
                    ProjectLayer item = getItem();
                    if (item == null || isEmpty()) {
                        return;
                    }
                    var dragboard = startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(item.getId());
                    dragboard.setContent(content);
                    event.consume();
                });

                setOnDragOver(event -> {
                    if (event.getGestureSource() != this
                            && event.getDragboard().hasString()
                            && findVideoLayerById(event.getDragboard().getString()) != null) {
                        event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                    }
                    event.consume();
                });

                setOnDragDropped(event -> {
                    var dragboard = event.getDragboard();
                    ProjectLayer dragged = dragboard.hasString() ? findVideoLayerById(dragboard.getString()) : null;
                    if (dragged == null) {
                        event.setDropCompleted(false);
                        event.consume();
                        return;
                    }
                    int targetIndex = isEmpty() ? videoLayers.size() - 1 : getIndex();
                    moveVideoLayerToIndex(dragged, targetIndex);
                    event.setDropCompleted(true);
                    event.consume();
                });
            }

            @Override protected void updateItem(ProjectLayer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        videoLayerList.getSelectionModel().selectedItemProperty()
            .addListener((obs, ov, layer) -> {
                if (layer != null) {
                    videoLayerTextField.setText(layer.getText() == null ? "" : layer.getText());
                    videoFontFamilyBox.getSelectionModel().select(layer.getFontFamily());
                    if (layer.getType() == LayerType.TEXT) {
                        videoFontSizeSlider.setValue(layer.getFontSize());
                    } else {
                        videoFontSizeSlider.setValue(layer.getWidth());
                    }
                    videoLayerFillPicker.setValue(layer.getFillColor());
                    videoLayerStrokePicker.setValue(layer.getStrokeColor());
                    videoLayerStrokeSlider.setValue(layer.getStrokeWidth());
                    videoLayerOpacitySlider.setValue(layer.getOpacity());
                    
                    // Update properties panel
                    updateLayerPropertiesFromSelection(layer);
                }
                drawVideoLayerCanvas();
            });

        videoLayers.addListener(
            (javafx.collections.ListChangeListener<? super ProjectLayer>) c -> drawVideoLayerCanvas());

        // Drag-and-drop on canvas - just move, no resize handles
        videoLayerCanvas.setOnMousePressed(event -> {
            ProjectLayer hit = findVideoLayerAt(event.getX(), event.getY());
            draggedVideoLayer = hit;
            
            if (hit != null) {
                videoLayerList.getSelectionModel().select(hit);
                double[] pt = videoCanvasToReference(event.getX(), event.getY());
                draggedVideoLayerOffsetX = pt[0] - hit.getX();
                draggedVideoLayerOffsetY = pt[1] - hit.getY();
            }
            drawVideoLayerCanvas();
        });

        videoLayerCanvas.setOnMouseDragged(event -> {
            if (draggedVideoLayer == null) return;

            double[] pt = videoCanvasToReference(event.getX(), event.getY());
            draggedVideoLayer.setX(pt[0] - draggedVideoLayerOffsetX);
            draggedVideoLayer.setY(pt[1] - draggedVideoLayerOffsetY);

            updateLayerPropertiesFromSelection(draggedVideoLayer);
            videoLayerList.refresh();
            drawVideoLayerCanvas();
        });

        videoLayerCanvas.setOnMouseReleased(event -> {
            draggedVideoLayer = null;
        });

        sequenceList.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> drawVideoLayerCanvas());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layer add / edit / delete
    // ═════════════════════════════════════════════════════════════════════════
    private void addVideoTextLayer() {
        ProjectLayer layer = ProjectLayer.text(
            videoLayerTextField.getText(),
            videoFontFamilyBox.getValue(),
            videoFontSizeSlider.getValue(),
            videoLayerFillPicker.getValue(),
            VIDEO_LAYER_REFERENCE_WIDTH  * 0.10,
            VIDEO_LAYER_REFERENCE_HEIGHT * 0.12);
        layer.setStrokeColor(videoLayerStrokePicker.getValue());
        layer.setStrokeWidth(videoLayerStrokeSlider.getValue());
        layer.setOpacity(videoLayerOpacitySlider.getValue());
        updateTextLayerBounds(layer);
        videoLayers.add(layer);
        videoLayerList.getSelectionModel().select(layer);
        drawVideoLayerCanvas();
    }

    private void addVideoShapeLayer(LayerType type) {
        ProjectLayer layer = ProjectLayer.shape(
            type,
            VIDEO_LAYER_REFERENCE_WIDTH  * 0.14,
            VIDEO_LAYER_REFERENCE_HEIGHT * 0.18,
            VIDEO_LAYER_REFERENCE_WIDTH  * 0.28,
            VIDEO_LAYER_REFERENCE_HEIGHT * 0.16,
            videoLayerFillPicker.getValue(),
            videoLayerStrokePicker.getValue(),
            videoLayerStrokeSlider.getValue());
        layer.setOpacity(videoLayerOpacitySlider.getValue());
        videoLayers.add(layer);
        videoLayerList.getSelectionModel().select(layer);
        drawVideoLayerCanvas();
    }

    private void addVideoImageLayerFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Image Asset for Video Layer");
        File initDir = AppPaths.ASSET_DIR.toFile().exists()
            ? AppPaths.ASSET_DIR.toFile() : AppPaths.EXPORT_DIR.toFile();
        chooser.setInitialDirectory(initDir);
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png","*.jpg","*.jpeg","*.bmp","*.gif","*.webp"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) addVideoImageLayer(file);
    }

    private void pasteVideoClipboardLayer() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        try {
            if (clipboard.hasFiles()) {
                File first = clipboard.getFiles().stream()
                    .filter(f -> FileUtils.isImageFile(f.toPath()))
                    .findFirst().orElse(null);
                if (first != null) {
                    addVideoImageLayer(context.getAssetLibraryService().importAsset(first));
                    return;
                }
            }
            if (clipboard.hasImage()) {
                BufferedImage img  = ImageUtils.fromFxImage(clipboard.getImage());
                File staged = context.getAssetLibraryService().stageClipboardImage(img, "video-pasted-layer");
                addVideoImageLayer(context.getAssetLibraryService().importAsset(staged));
                return;
            }
            Dialogs.warn("Clipboard Empty", "Clipboard does not contain an image.");
        } catch (Exception ex) {
            Dialogs.error("Paste Error", "Could not paste clipboard image as a video layer.", ex);
        }
    }

    private void addVideoImageLayer(File file) {
        try {
            BufferedImage img = ImageUtils.read(file);
            double tw = VIDEO_LAYER_REFERENCE_WIDTH * 0.22;
            double th = tw * img.getHeight() / (double) img.getWidth();
            ProjectLayer layer = ProjectLayer.image(
                file.getAbsolutePath(),
                VIDEO_LAYER_REFERENCE_WIDTH  * 0.10,
                VIDEO_LAYER_REFERENCE_HEIGHT * 0.10,
                tw, th);
            layer.setName("Asset: " + file.getName());
            layer.setOpacity(videoLayerOpacitySlider.getValue());
            videoLayers.add(layer);
            videoLayerList.getSelectionModel().select(layer);
            drawVideoLayerCanvas();
        } catch (Exception ex) {
            Dialogs.error("Layer Error", "Could not add the image asset.", ex);
        }
    }

    private void applyVideoLayerControls() {
        ProjectLayer layer = videoLayerList.getSelectionModel().getSelectedItem();
        if (layer == null) { Dialogs.warn("No Layer Selected", "Select a layer first."); return; }
        
        if (layer.getType() == LayerType.TEXT) {
            layer.setText(videoLayerTextField.getText());
            layer.setFontFamily(videoFontFamilyBox.getValue());
            layer.setFontSize(videoFontSizeSlider.getValue());
            updateTextLayerBounds(layer);
        } else {
            // --- NEW: Scale shapes and images proportionally using the slider ---
            double oldW = Math.max(1, layer.getWidth());
            double newW = videoFontSizeSlider.getValue();
            double ratio = layer.getHeight() / oldW;
            layer.setWidth(newW);
            layer.setHeight(newW * ratio);
        }
        
        layer.setFillColor(videoLayerFillPicker.getValue());
        layer.setStrokeColor(videoLayerStrokePicker.getValue());
        layer.setStrokeWidth(videoLayerStrokeSlider.getValue());
        layer.setOpacity(videoLayerOpacitySlider.getValue());
        videoLayerList.refresh();
        drawVideoLayerCanvas();
    }

    private void deleteVideoLayer() {
        ProjectLayer layer = videoLayerList.getSelectionModel().getSelectedItem();
        if (layer != null) { videoLayers.remove(layer); drawVideoLayerCanvas(); }
    }

    private void moveSelectedVideoLayer(int direction) {
        ProjectLayer layer = videoLayerList.getSelectionModel().getSelectedItem();
        if (layer == null) {
            Dialogs.warn("No Layer Selected", "Select a layer first.");
            return;
        }
        int currentIndex = videoLayers.indexOf(layer);
        int targetIndex = currentIndex + direction;
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= videoLayers.size()) {
            return;
        }
        moveVideoLayerToIndex(layer, targetIndex);
    }

    private void moveVideoLayerToIndex(ProjectLayer layer, int targetIndex) {
        int currentIndex = videoLayers.indexOf(layer);
        if (currentIndex < 0) {
            return;
        }
        targetIndex = Math.max(0, Math.min(videoLayers.size() - 1, targetIndex));
        if (currentIndex == targetIndex) {
            videoLayerList.getSelectionModel().select(layer);
            return;
        }
        videoLayers.remove(currentIndex);
        videoLayers.add(targetIndex, layer);
        videoLayerList.getSelectionModel().select(layer);
        videoLayerList.refresh();
        drawVideoLayerCanvas();
    }

    private ProjectLayer findVideoLayerById(String id) {
        if (id == null) {
            return null;
        }
        return videoLayers.stream()
            .filter(layer -> id.equals(layer.getId()))
            .findFirst()
            .orElse(null);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Canvas drawing
    // ═════════════════════════════════════════════════════════════════════════
    private void drawVideoLayerCanvas() {
        GraphicsContext gc = videoLayerCanvas.getGraphicsContext2D();
        double cw = videoLayerCanvas.getWidth();
        double ch = videoLayerCanvas.getHeight();
        gc.setFill(Color.rgb(15, 23, 42));
        gc.fillRect(0, 0, cw, ch);

        videoLayerScale = Math.min(cw / VIDEO_LAYER_REFERENCE_WIDTH, ch / VIDEO_LAYER_REFERENCE_HEIGHT);
        double drawW = VIDEO_LAYER_REFERENCE_WIDTH * videoLayerScale;
        double drawH = VIDEO_LAYER_REFERENCE_HEIGHT * videoLayerScale;
        videoLayerOffsetX = (cw - drawW) / 2.0;
        videoLayerOffsetY = (ch - drawH) / 2.0;

        if (currentVideoFrame != null) {
            gc.drawImage(currentVideoFrame, videoLayerOffsetX, videoLayerOffsetY, drawW, drawH);
        } else if (!sequenceItems.isEmpty()) {
            try {
                // Get the image the user clicked, or fallback to the first one
                PhotoItem selected = sequenceList.getSelectionModel().getSelectedItem();
                if (selected == null) selected = sequenceItems.getFirst();
                
                Image previewFrame = new Image(new File(selected.getFilePath()).toURI().toString(), false);
                double scale = Math.min(drawW / previewFrame.getWidth(), drawH / previewFrame.getHeight());
                double imageW = previewFrame.getWidth() * scale;
                double imageH = previewFrame.getHeight() * scale;
                gc.drawImage(previewFrame,
                    videoLayerOffsetX + (drawW - imageW) / 2.0,
                    videoLayerOffsetY + (drawH - imageH) / 2.0,
                    imageW,
                    imageH);
            } catch (Exception ex) {
                drawEmptyVideoLayerPreview(gc, drawW, drawH);
            }
        }

        gc.setStroke(Color.rgb(148, 163, 184));
        gc.setLineWidth(1);
        gc.strokeRect(videoLayerOffsetX, videoLayerOffsetY, drawW, drawH);
        gc.setFill(Color.rgb(226, 232, 240));
        gc.setFont(Font.font(11));
        gc.fillText("1280x720 video preview - drag layers to reposition them", videoLayerOffsetX + 10, videoLayerOffsetY + drawH - 12);

        for (ProjectLayer layer : videoLayers) {
            drawVideoLayer(gc, layer);
        }

        ProjectLayer sel = videoLayerList.getSelectionModel().getSelectedItem();
        if (sel != null) {
            double[] bounds = layerSelectionBounds(sel);
            gc.setStroke(Color.rgb(96, 165, 250));
            gc.setLineWidth(2);
            gc.setLineDashes(7, 5);
            gc.strokeRect(bounds[0], bounds[1], bounds[2], bounds[3]);
            gc.setLineDashes();
        }
    }

    private void drawEmptyVideoLayerPreview(GraphicsContext gc, double drawW, double drawH) {
        gc.setFill(Color.rgb(2, 6, 23));
        gc.fillRect(videoLayerOffsetX, videoLayerOffsetY, drawW, drawH);
        gc.setFill(Color.rgb(148, 163, 184));
        gc.setFont(Font.font(11));
        gc.fillText("Generate Preview to show the video frame here.", videoLayerOffsetX + 10, videoLayerOffsetY + 18);
    }

    private void drawVideoLayer(GraphicsContext gc, ProjectLayer layer) {
        if (layer == null || !layer.isVisible()) return;
        double x = videoLayerOffsetX + layer.getX() * videoLayerScale;
        double y = videoLayerOffsetY + layer.getY() * videoLayerScale;
        double w = layer.getWidth()  * videoLayerScale;
        double h = layer.getHeight() * videoLayerScale;
        gc.save();
        gc.setGlobalAlpha(layer.getOpacity());
        switch (layer.getType()) {
            case TEXT -> {
                gc.setFont(Font.font(layer.getFontFamily(), layer.getFontSize() * videoLayerScale));
                if (layer.getStrokeWidth() > 0) {
                    gc.setStroke(layer.getStrokeColor());
                    gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * videoLayerScale));
                    gc.strokeText(layer.getText(), x, y + layer.getFontSize() * videoLayerScale);
                }
                gc.setFill(layer.getFillColor());
                gc.fillText(layer.getText(), x, y + layer.getFontSize() * videoLayerScale);
            }
            case IMAGE -> {
                try {
                    gc.drawImage(new Image(new File(layer.getSourcePath()).toURI().toString(), false), x, y, w, h);
                } catch (Exception ignored) {
                    gc.setFill(Color.rgb(220, 38, 38, 0.40));
                    gc.fillRect(x, y, w, h);
                }
            }
            case RECTANGLE -> {
                gc.setFill(layer.getFillColor()); gc.fillRect(x, y, w, h);
                if (layer.getStrokeWidth() > 0) {
                    gc.setStroke(layer.getStrokeColor());
                    gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * videoLayerScale));
                    gc.strokeRect(x, y, w, h);
                }
            }
            case ELLIPSE -> {
                gc.setFill(layer.getFillColor()); gc.fillOval(x, y, w, h);
                if (layer.getStrokeWidth() > 0) {
                    gc.setStroke(layer.getStrokeColor());
                    gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * videoLayerScale));
                    gc.strokeOval(x, y, w, h);
                }
            }
        }
        gc.restore();
    }

    private double[] layerSelectionBounds(ProjectLayer layer) {
        if (layer.getType() == LayerType.TEXT) {
            String textValue = layer.getText() == null ? "" : layer.getText();
            double scaledFontSize = layer.getFontSize() * videoLayerScale;
            javafx.scene.text.Text textNode = new javafx.scene.text.Text(textValue.isBlank() ? " " : textValue);
            textNode.setFont(Font.font(layer.getFontFamily(), scaledFontSize));
            var textBounds = textNode.getLayoutBounds();
            double baselineX = videoLayerOffsetX + layer.getX() * videoLayerScale;
            double baselineY = videoLayerOffsetY + layer.getY() * videoLayerScale + scaledFontSize;
            double pad = Math.max(4, layer.getStrokeWidth() * videoLayerScale + 3);
            return new double[]{
                baselineX + textBounds.getMinX() - pad,
                baselineY + textBounds.getMinY() - pad,
                Math.max(1, textBounds.getWidth() + pad * 2),
                Math.max(1, textBounds.getHeight() + pad * 2)
            };
        }
        return new double[]{
            videoLayerOffsetX + layer.getX() * videoLayerScale,
            videoLayerOffsetY + layer.getY() * videoLayerScale,
            layer.getWidth() * videoLayerScale,
            layer.getHeight() * videoLayerScale
        };
    }

    private void updateTextLayerBounds(ProjectLayer layer) {
        if (layer == null || layer.getType() != LayerType.TEXT) {
            return;
        }
        String textValue = layer.getText() == null ? "" : layer.getText();
        javafx.scene.text.Text textNode = new javafx.scene.text.Text(textValue.isBlank() ? " " : textValue);
        textNode.setFont(Font.font(layer.getFontFamily(), layer.getFontSize()));
        var bounds = textNode.getLayoutBounds();
        double padding = Math.max(8, layer.getStrokeWidth() * 2 + 6);
        layer.setWidth(Math.max(40, bounds.getWidth() + padding * 2));
        layer.setHeight(Math.max(layer.getFontSize(), bounds.getHeight() + padding * 2));
    }

    private ProjectLayer findVideoLayerAt(double cx, double cy) {
        double[] pt = videoCanvasToReference(cx, cy);
        double x = pt[0], y = pt[1];
        for (int i = videoLayers.size() - 1; i >= 0; i--) {
            ProjectLayer l = videoLayers.get(i);
            if (x >= l.getX() && x <= l.getX() + l.getWidth()
             && y >= l.getY() && y <= l.getY() + l.getHeight()) return l;
        }
        return null;
    }

    private double[] videoCanvasToReference(double cx, double cy) {
        if (videoLayerScale <= 0) return new double[]{0, 0};
        return new double[]{
            (cx - videoLayerOffsetX) / videoLayerScale,
            (cy - videoLayerOffsetY) / videoLayerScale};
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Sequence management
    // ═════════════════════════════════════════════════════════════════════════
    private void addSelectedToSequence() {
        sequenceItems.addAll(availableList.getSelectionModel().getSelectedItems());
    }

    private void removeSelectedFromSequence() {
        List<Integer> idx = new ArrayList<>(sequenceList.getSelectionModel().getSelectedIndices());
        idx.sort(Collections.reverseOrder());
        for (int i : idx) sequenceItems.remove(i);
        sequenceList.getSelectionModel().clearSelection();
    }

    private void moveSelected(int delta) {
        int index = sequenceList.getSelectionModel().getSelectedIndex();
        if (index < 0) return;
        int target = index + delta;
        if (target < 0 || target >= sequenceItems.size()) return;
        PhotoItem item = sequenceItems.remove(index);
        sequenceItems.add(target, item);
        sequenceList.getSelectionModel().select(target);
    }

    private void loadAllFiltered() {
        sequenceItems.setAll(filteredPhotos);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Preview generation
    // ═════════════════════════════════════════════════════════════════════════
    private void generatePreview() {
        if (sequenceItems.isEmpty()) {
            Dialogs.warn("Empty Sequence", "Add images to the video sequence first.");
            return;
        }
        int parsedWidth = Integer.parseInt(
            resolutionBox.getValue().split("[×x]")[0].trim());
        List<ProjectLayer> snapshot = videoLayers.stream().map(ProjectLayer::copy).toList();

        previewButton.setDisable(true);
        exportButton.setDisable(true);
        progressBar.setVisible(true);
        renderStatus.setText("Rendering…");

        Task<File> task = new Task<>() {
            @Override protected File call() throws Exception {
                File tmp = File.createTempFile("photofusion-preview-", ".mp4");
                tmp.deleteOnExit();
                context.getVideoService().renderVideo(
                    sequenceItems,
                    "",
                    secondsSpinner.getValue(),
                    fpsSpinner.getValue(),
                    parsedWidth,
                    tmp,
                    snapshot,
                    VIDEO_LAYER_REFERENCE_WIDTH,
                    VIDEO_LAYER_REFERENCE_HEIGHT,
                    progress -> updateProgress((long)(progress * 1000), 1000));
                return tmp;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            renderStatus.setText("Done — click Save Video to export.");
            tempPreviewFile = task.getValue();
            loadMedia(tempPreviewFile);
            previewButton.setDisable(false);
            exportButton.setDisable(false);

            if (centerTabPane != null) {
                // Index 1 is the Video Player tab
                centerTabPane.getSelectionModel().select(1); 
            }
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            renderStatus.setText("Render failed.");
            previewButton.setDisable(false);
            Dialogs.error("Video Error", "Could not render the video.", task.getException());
        });

        Thread t = new Thread(task, "video-renderer");
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Export
    // ═════════════════════════════════════════════════════════════════════════
    private void exportVideo() {
        if (tempPreviewFile == null || !tempPreviewFile.exists()) {
            Dialogs.warn("No Preview", "Generate a preview first, then save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Video File");
        chooser.setInitialDirectory(AppPaths.EXPORT_DIR.toFile());
        String base = sequenceItems.isEmpty() ? "video-montage"
            : FileUtils.slugify(FileUtils.baseName(sequenceItems.getFirst().getName())) + "-montage";
        chooser.setInitialFileName(base + ".mp4");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
        File output = chooser.showSaveDialog(getScene().getWindow());
        if (output != null) {
            try {
                Files.copy(tempPreviewFile.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                context.setLatestExportFile(output);
                renderStatus.setText("Saved: " + output.getName());
                Dialogs.info("Export Successful", "Video saved to " + output.getName());
            } catch (Exception ex) {
                Dialogs.error("Export Error", "Failed to save the video.", ex);
            }
        }
    }

private void loadMedia(File file) {
        disposePlayer();
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        playPauseButton.setDisable(false);
        seekSlider.setDisable(false);
        skipBackButton.setDisable(false);
        skipFwdButton.setDisable(false);
        fullScrButton.setDisable(false);
        playPauseButton.setText("▶  Play");

        // 1. Setup Ready State
        mediaPlayer.setOnReady(() -> {
            mediaDuration = mediaPlayer.getMedia().getDuration();
            updateTimeLabel(Duration.ZERO);
            captureMediaFrame(); 
        });

        // 2. Setup Time Ticker (Removed the heavy captureMediaFrame call from here!)
        mediaPlayer.currentTimeProperty().addListener((obs, ov, nv) -> {
            if (!seekSlider.isValueChanging() && mediaDuration != null && mediaDuration.greaterThan(Duration.ZERO)) {
                seekSlider.setValue(nv.toMillis() / mediaDuration.toMillis() * 1000.0);
            }
            updateTimeLabel(nv);
        });

        // 3. Fix the Replay Bug (Use STOP instead of PAUSE)
        mediaPlayer.setOnEndOfMedia(() -> {
            playPauseButton.setText("▶  Play");
            mediaPlayer.stop(); // Stop resets the stream properly so it can be played again
        });
    }

    private void togglePlayback() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseButton.setText("▶  Play");
        } else {
            mediaPlayer.play();
            playPauseButton.setText("⏸  Pause");
        }
    }

    private void skipTime(int seconds) {
        if (mediaPlayer == null || mediaDuration == null) return;
        Duration target = mediaPlayer.getCurrentTime().add(Duration.seconds(seconds));
        if (target.lessThan(Duration.ZERO))         target = Duration.ZERO;
        if (target.greaterThan(mediaDuration))      target = mediaDuration;
        mediaPlayer.seek(target);
    }

    private void toggleFullScreen() {
        if (fullScreenStage == null) {
            // ENTER FULL SCREEN
            fullScreenStage = new Stage();
            StackPane root = new StackPane(playerPane);
            root.setStyle("-fx-background-color: black;");
            Scene fsScene = new Scene(root);
            if (getScene() != null && !getScene().getStylesheets().isEmpty())
                fsScene.getStylesheets().addAll(getScene().getStylesheets());
            fullScreenStage.setScene(fsScene);
            fullScreenStage.setFullScreenExitHint("Press ESC to exit full screen");
            fullScreenStage.fullScreenProperty().addListener((obs, ov, isFs) -> {
                if (!isFs) exitFullScreen();
            });
            playerBox.getChildren().remove(playerPane);
            playerPane.setPadding(Insets.EMPTY);
            fullScreenStage.show();
            fullScreenStage.setFullScreen(true);
            fullScrButton.setText("⛶  Exit Full Screen");
        } else {
            fullScreenStage.setFullScreen(false);
        }
    }

    private void exitFullScreen() {
        if (fullScreenStage != null) {
            ((StackPane) fullScreenStage.getScene().getRoot()).getChildren().remove(playerPane);
            playerPane.setPadding(new Insets(12));
            playerBox.getChildren().add(playerPane);
            fullScreenStage.close();
            fullScreenStage = null;
            fullScrButton.setText("⛶  Full Screen");
        }
    }

    private void seekToSliderPosition() {
        if (mediaPlayer == null || mediaDuration == null || !mediaDuration.greaterThan(Duration.ZERO)) return;
        mediaPlayer.seek(mediaDuration.multiply(seekSlider.getValue() / 1000.0));
    }

    private void updateTimeLabel(Duration current) {
        timeLabel.setText(formatDuration(current) + " / "
            + formatDuration(mediaDuration == null ? Duration.ZERO : mediaDuration));
    }

    private String formatDuration(Duration d) {
        if (d == null || d.isUnknown()) return "00:00";
        int s = (int) Math.floor(d.toSeconds());
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void disposePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        currentVideoFrame = null;
        drawVideoLayerCanvas();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════
    private Button iconBtn(String label, Runnable action) {
        Button b = new Button(label);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Label styled(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-title");
        return l;
    }

    private ListCell<PhotoItem> thumbnailCell() {
        return new ListCell<>() {
            private final ImageView imageView = new ImageView();
            private final Label label = new Label();
            private final VBox box = new VBox(5, imageView, label);

            {
                box.setAlignment(Pos.CENTER);
                imageView.setFitWidth(90);
                imageView.setFitHeight(60);
                imageView.setPreserveRatio(true);
                label.setStyle("-fx-font-size: 10px;");
                label.setMaxWidth(90);
                // Add a slight hover effect for better UI feel
                box.setOnMouseEntered(e -> box.setStyle("-fx-opacity: 0.8;"));
                box.setOnMouseExited(e -> box.setStyle("-fx-opacity: 1.0;"));
            }

            @Override 
            protected void updateItem(PhotoItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    label.setText(item.getName());
                    File f = new File(item.getFilePath());
                    if (f.exists()) {
                        // The 'true, true, true' enables smooth background loading 
                        // so your UI never freezes while loading thumbnails!
                        imageView.setImage(new Image(f.toURI().toString(), 90, 60, true, true, true));
                    } else {
                        imageView.setImage(null);
                    }
                    setGraphic(box);
                }
            }
        };
    }

    private void captureMediaFrame() {
        if (mediaView == null || mediaView.getMediaPlayer() == null) return;
        // snapshot must run on JavaFX thread
        Platform.runLater(() -> {
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage img = mediaView.snapshot(params, null);
                if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                    currentVideoFrame = img;
                    drawVideoLayerCanvas();
                }
            } catch (Exception ignored) {
                // If snapshot fails (e.g., during rapid playback) we ignore
            }
        });
    }

// ─── Section: Editor Sidebar (Tools moved to Right Panel) ─────────────────
    private VBox buildEditorSidebar() {
        Label sectionTitle = new Label("🛠 Layout Toolbox");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 1. Add Buttons 
        Button addTextBtn    = iconBtn("＋ Text",      () -> addVideoTextLayer());
        Button addRectBtn    = iconBtn("＋ Rect", () -> addVideoShapeLayer(LayerType.RECTANGLE));
        Button addEllipseBtn = iconBtn("＋ Ellipse",   () -> addVideoShapeLayer(LayerType.ELLIPSE));
        Button addAssetBtn   = iconBtn("＋ Image",     () -> addVideoImageLayerFromFile());
        FlowPane addBox = new FlowPane(8, 8, addTextBtn, addRectBtn, addEllipseBtn, addAssetBtn);

        // 2. Styling Toolbox (Text, Font, Size, Fill)
        GridPane styleGrid = new GridPane();
        styleGrid.setHgap(8); styleGrid.setVgap(10);
        styleGrid.add(new Label("Text:"), 0, 0); styleGrid.add(videoLayerTextField, 1, 0);
        styleGrid.add(new Label("Font:"), 0, 1); styleGrid.add(videoFontFamilyBox, 1, 1);
        styleGrid.add(new Label("Size:"), 0, 2); styleGrid.add(videoFontSizeSlider, 1, 2);
        styleGrid.add(new Label("Fill:"), 0, 3); styleGrid.add(videoLayerFillPicker, 1, 3);
        styleGrid.add(new Label("Stroke:"), 0, 4); styleGrid.add(videoLayerStrokePicker, 1, 4);
        styleGrid.add(new Label("Width:"), 0, 5); styleGrid.add(videoLayerStrokeSlider, 1, 5);
        styleGrid.add(new Label("Opacity:"), 0, 6); styleGrid.add(videoLayerOpacitySlider, 1, 6);
        
        videoLayerTextField.setMaxWidth(Double.MAX_VALUE);
        videoFontFamilyBox.setMaxWidth(Double.MAX_VALUE);
        videoLayerFillPicker.setMaxWidth(Double.MAX_VALUE);
        videoLayerStrokePicker.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(videoLayerTextField, Priority.ALWAYS);

        // 3. Action Buttons (Made Red!)
        Button applyBtn      = iconBtn("✔ Apply",      () -> applyVideoLayerControls());
        Button forwardBtn    = iconBtn("⬆ Fwd",       () -> moveSelectedVideoLayer(1));
        Button backwardBtn   = iconBtn("⬇ Back",      () -> moveSelectedVideoLayer(-1));
        Button deleteBtn     = iconBtn("🗑 Delete",    () -> deleteVideoLayer());
        deleteBtn.getStyleClass().add("danger-button"); // RED BUTTON
        Button clearBtn      = iconBtn("✕ Clear All",  () -> videoLayers.clear());
        clearBtn.getStyleClass().add("danger-button"); // RED BUTTON
        
        FlowPane actionBox = new FlowPane(8, 8, applyBtn, forwardBtn, backwardBtn, deleteBtn, clearBtn);

        // 4. Layer List
        videoLayerList.setPrefHeight(120);
        VBox layerListBox = new VBox(4, new Label("Layers:"), videoLayerList);

        return new VBox(15, sectionTitle, new TitledPane("1. Add Layers", addBox), new TitledPane("2. ToolBox", styleGrid), actionBox, layerListBox);
    }


}

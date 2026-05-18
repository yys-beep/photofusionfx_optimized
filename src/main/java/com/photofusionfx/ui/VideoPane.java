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

    // ── Overlay text ─────────────────────────────────────────────────────────
    private final TextArea overlayTextArea = new TextArea();

    // ── Video layer canvas & controls ────────────────────────────────────────
    private static final int VIDEO_LAYER_REFERENCE_WIDTH  = 1280;
    private static final int VIDEO_LAYER_REFERENCE_HEIGHT = 720;

    private final ObservableList<ProjectLayer> videoLayers    = FXCollections.observableArrayList();
    private final ListView<ProjectLayer>        videoLayerList = new ListView<>(videoLayers);
    private final Canvas     videoLayerCanvas       = new Canvas(480, 270);
    private final Slider     layerPreviewZoomSlider = new Slider(0.5, 3.0, 1.0);
    private final Label      layerPreviewZoomLabel  = new Label("100%");
    private final TextField  videoLayerTextField    = new TextField("Video text");
    private final ComboBox<String> videoFontFamilyBox   = new ComboBox<>();
    private final Slider     videoFontSizeSlider    = new Slider(10, 160, 54);
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

    // ═════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═════════════════════════════════════════════════════════════════════════
    public VideoPane(AppContext context) {
        this.context       = context;
        this.filteredPhotos = new FilteredList<>(context.getPhotoLibrary(), PhotoItem::isFavorite);

        configureVideoLayerControls();
        initialiseSpinnersAndCombos();
        initialiseMediaButtons();

        // Wrap everything in a ScrollPane so the window can always scroll down
        ScrollPane outerScroll = new ScrollPane(buildMainLayout());
        outerScroll.setFitToWidth(true);
        outerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        outerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outerScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        setCenter(outerScroll);
        setPadding(new Insets(12));

        // Auto-populate if library already has favourites
        context.getPhotoLibrary().addListener(
            (javafx.collections.ListChangeListener<? super PhotoItem>) c -> {
                if (sequenceItems.isEmpty() && !filteredPhotos.isEmpty()) loadAllFiltered();
            });
        sequenceItems.addListener(
            (javafx.collections.ListChangeListener<? super PhotoItem>) c -> drawVideoLayerCanvas());
        if (!filteredPhotos.isEmpty()) loadAllFiltered();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Top-level layout builder
    // ═════════════════════════════════════════════════════════════════════════
    private VBox buildMainLayout() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(4, 8, 16, 8));

        root.getChildren().addAll(
            buildSequenceSection(),        // 1. Photo sequence management
            buildRenderSettingsSection(),  // 2. Encode settings + action buttons
            buildOverlayTextSection(),     // 3. Text overlay
            buildLayerEditorSection(),     // 4. Draggable graphical layers
            buildPlayerSection()           // 5. Preview player
        );
        return root;
    }

    // ─── Section 1: Sequence management ──────────────────────────────────────
    private TitledPane buildSequenceSection() {
        // Filter header
        Label filterLabel = new Label("Show:");
        photoFilterBox.getItems().addAll("Favourite Photos", "All Photos");
        photoFilterBox.getSelectionModel().selectFirst();
        photoFilterBox.valueProperty().addListener((obs, old, val) ->
            filteredPhotos.setPredicate("All Photos".equals(val) ? p -> true : PhotoItem::isFavorite));

        HBox filterRow = new HBox(8, filterLabel, photoFilterBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // Lists
        availableList.setItems(filteredPhotos);
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        sequenceList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availableList.setCellFactory(l -> simpleCell());
        sequenceList.setCellFactory(l -> simpleCell());
        availableList.setPrefHeight(180);
        sequenceList.setPrefHeight(180);

        // Transfer buttons (centre column)
        Button addBtn     = iconBtn("Add →",       () -> addSelectedToSequence());
        Button removeBtn  = iconBtn("← Remove",    () -> removeSelectedFromSequence());
        Button upBtn      = iconBtn("⬆ Up",        () -> moveSelected(-1));
        Button downBtn    = iconBtn("⬇ Down",      () -> moveSelected(1));
        Button loadAllBtn = iconBtn("Load All",    () -> loadAllFiltered());
        Button clearBtn   = iconBtn("🗑 Clear",    () -> sequenceItems.clear());

        VBox midButtons = new VBox(6, addBtn, removeBtn, new Separator(), upBtn, downBtn,
                                   new Separator(), loadAllBtn, clearBtn);
        midButtons.setAlignment(Pos.CENTER);
        midButtons.setPadding(new Insets(0, 4, 0, 4));

        VBox leftCol  = new VBox(6, new Label("📷  Available photos"), availableList);
        VBox rightCol = new VBox(6, new Label("🎬  Video sequence (in order)"), sequenceList);
        VBox.setVgrow(availableList, Priority.ALWAYS);
        VBox.setVgrow(sequenceList,  Priority.ALWAYS);
        HBox.setHgrow(leftCol,  Priority.ALWAYS);
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        HBox lists = new HBox(4, leftCol, midButtons, rightCol);
        VBox content = new VBox(8, filterRow, lists);
        content.setPadding(new Insets(10));

        TitledPane tp = new TitledPane("📋  Photo Sequence", content);
        tp.setExpanded(true);
        return tp;
    }

    // ─── Section 2: Render settings + action buttons ──────────────────────────
    private TitledPane buildRenderSettingsSection() {
        Label secsLabel = new Label("Seconds per image:");
        Label fpsLabel  = new Label("Frame rate (FPS):");
        Label resLabel  = new Label("Resolution:");

        secondsSpinner.setPrefWidth(72);
        fpsSpinner.setPrefWidth(72);
        resolutionBox.setPrefWidth(190);

        HBox settingsRow = new HBox(12,
            secsLabel, secondsSpinner,
            fpsLabel,  fpsSpinner,
            resLabel,  resolutionBox);
        settingsRow.setAlignment(Pos.CENTER_LEFT);

        // Progress + status
        progressBar.setPrefWidth(220);
        progressBar.setVisible(false);
        renderStatus.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        HBox progressRow = new HBox(10, progressBar, renderStatus);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        // Action buttons
        previewButton.getStyleClass().add("primary-button");
        exportButton.getStyleClass().add("success-button");
        exportButton.setDisable(true);

        previewButton.setOnAction(e -> generatePreview());
        exportButton.setOnAction(e -> exportVideo());
        previewButton.setMinWidth(180);
        exportButton.setMinWidth(140);

        HBox actionRow = new HBox(10, previewButton, exportButton);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomRow = new HBox(10, progressRow, spacer, actionRow);
        bottomRow.setAlignment(Pos.CENTER);

        VBox content = new VBox(10, settingsRow, bottomRow);
        content.setPadding(new Insets(10));

        TitledPane tp = new TitledPane("⚙  Render Settings", content);
        tp.setExpanded(true);
        return tp;
    }

    // ─── Section 3: Overlay text ──────────────────────────────────────────────
    private TitledPane buildOverlayTextSection() {
        overlayTextArea.setPromptText("Optional poem, title, or caption that appears on every video frame…");
        overlayTextArea.setPrefRowCount(3);
        overlayTextArea.setWrapText(true);

        Label hint = new Label("This text is drawn at the bottom of each frame. Leave blank to use the default title.");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        hint.setWrapText(true);

        VBox content = new VBox(8, hint, overlayTextArea);
        content.setPadding(new Insets(10));

        TitledPane tp = new TitledPane("🔤  Overlay Text / Caption", content);
        tp.setExpanded(false);
        return tp;
    }

    // ─── Section 4: Draggable graphical layers ────────────────────────────────
    private TitledPane buildLayerEditorSection() {
        VBox content = buildVideoLayerEditor();
        content.setPadding(new Insets(2));

        TitledPane tp = new TitledPane("🖼  Graphical Layers  (drag to move)", content);
        tp.setExpanded(false);
        return tp;
    }

    // ─── Section 5: Preview player ────────────────────────────────────────────
    private TitledPane buildPlayerSection() {
        Label hint = new Label("Generate a preview first, then use the controls below to watch and export it.");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        hint.setWrapText(true);

        buildPlayerPane();
        playerBox = new VBox(8, hint, playerPane);
        VBox.setVgrow(playerPane, Priority.ALWAYS);

        playerPane.setMinHeight(320);
        playerPane.setPrefHeight(360);

        TitledPane tp = new TitledPane("▶  Video Preview & Playback", playerBox);
        tp.setExpanded(true);
        return tp;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layer editor with properties panel
    // ═════════════════════════════════════════════════════════════════════════
    private VBox buildVideoLayerEditor() {
        videoLayerTextField.setPromptText("Text for text layers…");

        // ── Add-layer buttons ──
        Button addTextBtn    = iconBtn("＋ Text",      () -> addVideoTextLayer());
        Button addRectBtn    = iconBtn("＋ Rectangle", () -> addVideoShapeLayer(LayerType.RECTANGLE));
        Button addEllipseBtn = iconBtn("＋ Ellipse",   () -> addVideoShapeLayer(LayerType.ELLIPSE));
        Button addAssetBtn   = iconBtn("＋ Image",     () -> addVideoImageLayerFromFile());
        Button pasteBtn      = iconBtn("📋 Paste",     () -> pasteVideoClipboardLayer());
        Button applyBtn      = iconBtn("✔ Apply",      () -> applyVideoLayerControls());
        Button forwardBtn    = iconBtn("Forward",       () -> moveSelectedVideoLayer(1));
        Button backwardBtn   = iconBtn("Backward",      () -> moveSelectedVideoLayer(-1));
        Button deleteBtn     = iconBtn("🗑 Delete",    () -> deleteVideoLayer());
        Button clearBtn      = iconBtn("✕ Clear All",  () -> videoLayers.clear());

        // Canvas holder
        Pane holder = new Pane(videoLayerCanvas);
        holder.setMinHeight(360);
        holder.setPrefHeight(540);
        holder.setMinWidth(640);
        holder.setPrefWidth(960);
        holder.getStyleClass().add("preview-box");
        videoLayerCanvas.widthProperty().bind(holder.widthProperty());
        videoLayerCanvas.heightProperty().bind(holder.heightProperty());
        videoLayerCanvas.widthProperty().addListener((obs, ov, nv) -> drawVideoLayerCanvas());
        videoLayerCanvas.heightProperty().addListener((obs, ov, nv) -> drawVideoLayerCanvas());

        layerPreviewZoomSlider.setShowTickLabels(true);
        layerPreviewZoomSlider.setShowTickMarks(true);
        layerPreviewZoomSlider.setMajorTickUnit(0.5);
        layerPreviewZoomSlider.valueProperty().addListener((obs, ov, nv) -> {
            double zoom = nv.doubleValue();
            layerPreviewZoomLabel.setText(String.format("%d%%", Math.round(zoom * 100)));
            holder.setPrefSize(960 * zoom, 540 * zoom);
            drawVideoLayerCanvas();
        });

        ScrollPane canvasScroll = new ScrollPane(holder);
        canvasScroll.setFitToWidth(false);
        canvasScroll.setFitToHeight(false);
        canvasScroll.setPannable(true);
        canvasScroll.setPrefViewportWidth(960);
        canvasScroll.setPrefViewportHeight(540);
        canvasScroll.setMinHeight(560);
        canvasScroll.setPrefHeight(560);
        canvasScroll.setMaxWidth(Region.USE_PREF_SIZE);
        canvasScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        canvasScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        HBox previewZoomRow = new HBox(8, styled("Preview zoom"), layerPreviewZoomSlider, layerPreviewZoomLabel);
        previewZoomRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(layerPreviewZoomSlider, Priority.ALWAYS);

        // Style controls
        Label sizeLabel    = styled("Size");
        Label fillLabel    = styled("Fill");
        Label strokeLabel  = styled("Stroke colour");
        Label strokeW      = styled("Stroke width");
        Label opacityLabel = styled("Opacity");

        // Row 1: text & font
        HBox row1 = new HBox(8,
            styled("Text:"), videoLayerTextField,
            styled("Font:"), videoFontFamilyBox);
        HBox.setHgrow(videoLayerTextField, Priority.ALWAYS);

        // Row 2: style sliders/pickers
        HBox row2 = new HBox(8,
            sizeLabel, videoFontSizeSlider,
            fillLabel, videoLayerFillPicker,
            strokeLabel, videoLayerStrokePicker,
            strokeW, videoLayerStrokeSlider,
            opacityLabel, videoLayerOpacitySlider);
        HBox.setHgrow(videoFontSizeSlider,     Priority.SOMETIMES);
        HBox.setHgrow(videoLayerStrokeSlider,  Priority.SOMETIMES);
        HBox.setHgrow(videoLayerOpacitySlider, Priority.SOMETIMES);
        row2.setAlignment(Pos.CENTER_LEFT);

        // Row 3: action buttons
        HBox row3 = new HBox(6,
            addTextBtn, addRectBtn, addEllipseBtn, addAssetBtn,
            pasteBtn, applyBtn, forwardBtn, backwardBtn, deleteBtn, clearBtn);
        row3.setAlignment(Pos.CENTER_LEFT);
        row3.setAlignment(Pos.CENTER_LEFT);

        // Layer list
        videoLayerList.setPrefHeight(110);
        Label layerListLabel = new Label("Layer");
        layerListLabel.getStyleClass().add("field-title");
        VBox layerListBox = new VBox(4, layerListLabel, videoLayerList);

        // ═══════════════════════════════════════════════════════════════════
        // Layer Properties Panel
        // ═══════════════════════════════════════════════════════════════════
        VBox layerPropsPanel = buildLayerPropertiesPanel();

        // Main box combining canvas + properties
        VBox box = new VBox(8, 
            row1, 
            previewZoomRow,
            canvasScroll,
            layerListBox,
            row2, 
            row3,
            new Separator(),
            layerPropsPanel
        );
        box.getStyleClass().add("section-card");
        drawVideoLayerCanvas();
        return box;
    }

    // Build the layer properties panel
    private VBox buildLayerPropertiesPanel() {
        layerPropXSpinner.setEditable(true);
        layerPropYSpinner.setEditable(true);
        layerPropWSpinner.setEditable(true);
        layerPropHSpinner.setEditable(true);

        layerVisibilityCheck.setSelected(true);

        // Listen for changes in spinners to update the layer
        layerPropXSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropYSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropWSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerPropHSpinner.valueProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());
        layerVisibilityCheck.selectedProperty().addListener((obs, ov, nv) -> updateLayerFromProperties());

        HBox posRow = new HBox(12,
            styled("X:"), layerPropXSpinner,
            styled("Y:"), layerPropYSpinner,
            styled("Width:"), layerPropWSpinner,
            styled("Height:"), layerPropHSpinner,
            layerVisibilityCheck);
        posRow.setAlignment(Pos.CENTER_LEFT);

        VBox propsBox = new VBox(8, 
            new Label("Layer Properties:"),
            posRow);
        propsBox.setPadding(new Insets(10));
        propsBox.setStyle("-fx-border-color: #d0d0d0; -fx-border-radius: 5;");

        return propsBox;
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

    // ═════════════════════════════════════════════════════════════════════════
    // Player pane
    // ═════════════════════════════════════════════════════════════════════════
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

        HBox controls = new HBox(8,
            skipBackButton, playPauseButton, skipFwdButton,
            seekSlider, timeLabel, fullScrButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);
        controls.setPadding(new Insets(10, 0, 0, 0));

        // "No preview" placeholder
        Label placeholder = new Label("No preview yet.\nClick  ▶  Generate Preview  above to render the video.");
        placeholder.setTextAlignment(TextAlignment.CENTER);
        placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        placeholder.setWrapText(true);

        StackPane videoContainer = new StackPane(placeholder, mediaView);
        videoContainer.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 10;");
        videoContainer.setMinHeight(240);
        videoContainer.setPrefHeight(360);

        mediaView.visibleProperty().bind(
            mediaView.mediaPlayerProperty().isNotNull());
        placeholder.visibleProperty().bind(
            mediaView.mediaPlayerProperty().isNull());

        videoContainer.layoutBoundsProperty().addListener((obs, ov, nv) -> {
            mediaView.setFitWidth(nv.getWidth());
            mediaView.setFitHeight(nv.getHeight());
        });

        playerPane = new BorderPane();
        playerPane.setCenter(videoContainer);
        playerPane.setBottom(controls);
        playerPane.setPadding(new Insets(12));
        playerPane.getStyleClass().add("preview-box");
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
                    videoFontSizeSlider.setValue(layer.getFontSize());
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
                Image firstFrame = new Image(new File(sequenceItems.getFirst().getFilePath()).toURI().toString(), false);
                double scale = Math.min(drawW / firstFrame.getWidth(), drawH / firstFrame.getHeight());
                double imageW = firstFrame.getWidth() * scale;
                double imageH = firstFrame.getHeight() * scale;
                gc.drawImage(firstFrame,
                    videoLayerOffsetX + (drawW - imageW) / 2.0,
                    videoLayerOffsetY + (drawH - imageH) / 2.0,
                    imageW,
                    imageH);
            } catch (Exception ex) {
                drawEmptyVideoLayerPreview(gc, drawW, drawH);
            }
        } else {
            drawEmptyVideoLayerPreview(gc, drawW, drawH);
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
                    overlayTextArea.getText(),
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

    // ═════════════════════════════════════════════════════════════════════════
    // Media player
    // ═════════════════════════════════════════════════════════════════════════
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

        mediaPlayer.setOnReady(() -> {
            mediaDuration = mediaPlayer.getMedia().getDuration();
            updateTimeLabel(Duration.ZERO);
            // capture initial frame
            captureMediaFrame();
        });

        // existing listener - add captureMediaFrame() call inside
        mediaPlayer.currentTimeProperty().addListener((obs, ov, nv) -> {
            if (!seekSlider.isValueChanging() && mediaDuration != null && mediaDuration.greaterThan(Duration.ZERO)) {
                seekSlider.setValue(nv.toMillis() / mediaDuration.toMillis() * 1000.0);
            }
            updateTimeLabel(nv);

            // capture a preview frame periodically (this may be frequent)
            captureMediaFrame();
        });

        mediaPlayer.setOnReady(() -> {
            mediaDuration = mediaPlayer.getMedia().getDuration();
            updateTimeLabel(Duration.ZERO);
            // capture initial frame
            captureMediaFrame();
        });

        // existing listener - add captureMediaFrame() call inside
        mediaPlayer.currentTimeProperty().addListener((obs, ov, nv) -> {
            if (!seekSlider.isValueChanging() && mediaDuration != null && mediaDuration.greaterThan(Duration.ZERO)) {
                seekSlider.setValue(nv.toMillis() / mediaDuration.toMillis() * 1000.0);
            }
            updateTimeLabel(nv);

            // capture a preview frame periodically (this may be frequent)
            captureMediaFrame();
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            playPauseButton.setText("▶  Play");
            mediaPlayer.seek(Duration.ZERO);
            mediaPlayer.pause();
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

    private ListCell<PhotoItem> simpleCell() {
        return new ListCell<>() {
            @Override protected void updateItem(PhotoItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
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
}

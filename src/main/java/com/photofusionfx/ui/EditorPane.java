package com.photofusionfx.ui;

import com.photofusionfx.AppContext;
import com.photofusionfx.model.EditParameters;
import com.photofusionfx.model.LayerType;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.model.ProjectLayer;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
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
import java.nio.file.Path;
import java.util.List;

public class EditorPane extends BorderPane {
    private final AppContext context;
    private final Canvas canvas = new Canvas(900, 680);
    private final Label selectedLabel = new Label("Selected image: none");
    private final Slider brightnessSlider = new Slider(-100, 100, 0);
    private final Slider contrastSlider = new Slider(0.2, 3.0, 1.0);
    private final Slider saturationSlider = new Slider(0.0, 2.5, 1.0);
    private final CheckBox grayscaleCheck = new CheckBox("Convert to grayscale");
    private final CheckBox autoEnhanceCheck = new CheckBox("Auto enhance before manual adjustments");
    private final ColorPicker tintColorPicker = new ColorPicker(Color.TRANSPARENT);
    private final Slider tintStrengthSlider = new Slider(0, 1, 0);
    private final Slider borderSlider = new Slider(0, 40, 0);
    private final ColorPicker borderColorPicker = new ColorPicker(Color.WHITE);
    private final Slider scaleSlider = new Slider(0.25, 2.0, 1.0);
    private final Slider rotationSlider = new Slider(-180, 180, 0);
    private final Slider translateXSlider = new Slider(-300, 300, 0);
    private final Slider translateYSlider = new Slider(-300, 300, 0);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(120));

    private final ObservableList<ProjectLayer> layers = FXCollections.observableArrayList();
    private final ListView<ProjectLayer> layerList = new ListView<>(layers);
    private final TextField textField = new TextField("New text");
    private final ComboBox<String> fontFamilyBox = new ComboBox<>();
    private final Slider fontSizeSlider = new Slider(10, 180, 48);
    private final ColorPicker layerFillPicker = new ColorPicker(Color.WHITE);
    private final ColorPicker layerStrokePicker = new ColorPicker(Color.BLACK);
    private final Slider layerStrokeSlider = new Slider(0, 20, 0);
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);
    private final Spinner<Integer> layerXSpinner = new Spinner<>();
    private final Spinner<Integer> layerYSpinner = new Spinner<>();
    private final Spinner<Integer> layerWidthSpinner = new Spinner<>();
    private final Spinner<Integer> layerHeightSpinner = new Spinner<>();
    private final CheckBox layerVisibleCheck = new CheckBox("Visible");
    private final CheckBox lockLayerAspectCheck = new CheckBox("Keep layer aspect ratio");
    private final Spinner<Integer> resizeWidthSpinner = new Spinner<>();
    private final Spinner<Integer> resizeHeightSpinner = new Spinner<>();
    private final CheckBox lockAspectCheck = new CheckBox("Keep aspect ratio");

    private BufferedImage originalImage;
    private BufferedImage currentBase;
    private double displayScale = 1.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;
    private ProjectLayer draggedLayer;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean updatingResizeSpinners = false;
    private boolean updatingLayerSizeSpinners = false;

    public EditorPane(AppContext context) {
        this.context = context;
        setPadding(new Insets(12));

        ScrollPane controlsScroll = new ScrollPane(buildControls());
        controlsScroll.setFitToWidth(true);
        controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.setMinWidth(390);
        controlsScroll.setStyle("-fx-background-color: transparent;");
        setLeft(controlsScroll);
        setCenter(buildCanvasArea());

        debounce.setOnFinished(e -> renderBasePreview());
        registerListeners();
        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> loadSelectedPhoto(newValue));
        loadSelectedPhoto(context.getSelectedPhoto());
    }

    private VBox buildControls() {
        configureSlider(brightnessSlider, true);
        configureSlider(contrastSlider, false);
        configureSlider(saturationSlider, false);
        configureSlider(tintStrengthSlider, false);
        configureSlider(borderSlider, true);
        configureSlider(scaleSlider, false);
        configureSlider(rotationSlider, true);
        configureSlider(translateXSlider, true);
        configureSlider(translateYSlider, true);
        configureSlider(fontSizeSlider, true);
        configureSlider(layerStrokeSlider, true);
        configureSlider(layerOpacitySlider, false);

        fontFamilyBox.getItems().setAll(Font.getFamilies());
        fontFamilyBox.getSelectionModel().select(Font.getDefault().getFamily());
        textField.setPromptText("Text layer content");

        resizeWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12000, 1024));
        resizeHeightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12000, 768));
        resizeWidthSpinner.setEditable(true);
        resizeHeightSpinner.setEditable(true);
        lockAspectCheck.setSelected(true);
        layerWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12000, 100));
        layerHeightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12000, 100));
        layerXSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-12000, 12000, 0));
        layerYSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-12000, 12000, 0));
        layerXSpinner.setEditable(true);
        layerYSpinner.setEditable(true);
        layerWidthSpinner.setEditable(true);
        layerHeightSpinner.setEditable(true);
        lockLayerAspectCheck.setSelected(true);
        layerVisibleCheck.setSelected(true);

        layerList.setPrefHeight(150);
        layerList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectLayer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        Button resetButton = new Button("Reset Base Adjustments");
        resetButton.setOnAction(e -> resetControls());
        Button clearLayersButton = new Button("Clear Layers");
        clearLayersButton.setOnAction(e -> {
            layers.clear();
            drawCanvas();
        });

        Button addTextButton = new Button("Add Text Layer");
        addTextButton.setOnAction(e -> addTextLayer());
        Button addAssetButton = new Button("Add Image / Asset Layer");
        addAssetButton.setOnAction(e -> addImageLayerFromFile());
        Button pasteLayerButton = new Button("Paste Clipboard as Layer");
        pasteLayerButton.setOnAction(e -> pasteClipboardLayer());
        Button addRectButton = new Button("Add Rectangle");
        addRectButton.setOnAction(e -> addShapeLayer(LayerType.RECTANGLE));
        Button addEllipseButton = new Button("Add Ellipse");
        addEllipseButton.setOnAction(e -> addShapeLayer(LayerType.ELLIPSE));
        Button applyLayerButton = new Button("Apply Layer Changes");
        applyLayerButton.setOnAction(e -> applyLayerControls());
        applyLayerButton.getStyleClass().add("primary-button");
        Button deleteLayerButton = new Button("Delete Layer");
        deleteLayerButton.setOnAction(e -> deleteSelectedLayer());
        deleteLayerButton.getStyleClass().add("danger-button");
        Button frontButton = new Button("Bring Front");
        frontButton.setOnAction(e -> moveSelectedLayer(1));
        Button backButton = new Button("Send Back");
        backButton.setOnAction(e -> moveSelectedLayer(-1));

        Button exportButton = new Button("Export Composite Image");
        exportButton.setOnAction(e -> exportCompositeImage(false));
        exportButton.getStyleClass().add("success-button");
        Button saveToLibraryButton = new Button("Save Composite into Library");
        saveToLibraryButton.setOnAction(e -> saveCompositeIntoLibrary());
        saveToLibraryButton.getStyleClass().add("success-button");
        Button resizeButton = new Button("Export Resized Copy");
        resizeButton.setOnAction(e -> exportResizedCopy());

        VBox controls = new VBox(10,
                selectedLabel,
                new Separator(),
                new Label("Non-destructive base image adjustments"),
                sliderRow("Brightness", brightnessSlider),
                sliderRow("Contrast", contrastSlider),
                sliderRow("Saturation", saturationSlider),
                autoEnhanceCheck,
                grayscaleCheck,
                labelledControl("Scenario tint / color combination", tintColorPicker),
                sliderRow("Tint Strength", tintStrengthSlider),
                sliderRow("Border Width", borderSlider),
                labelledControl("Border Color", borderColorPicker),
                sliderRow("Scale", scaleSlider),
                sliderRow("Rotation", rotationSlider),
                sliderRow("Translate X", translateXSlider),
                sliderRow("Translate Y", translateYSlider),
                new HBox(10, resetButton, clearLayersButton),
                new Separator(),
                new Label("Layer stack (drag directly on preview canvas)"),
                layerList,
                new HBox(10, frontButton, backButton, deleteLayerButton),
                labelledControl("Text", textField),
                labelledControl("Font", fontFamilyBox),
                sliderRow("Font Size", fontSizeSlider),
                labelledControl("Fill / Text Color", layerFillPicker),
                labelledControl("Stroke / Outline Color", layerStrokePicker),
                sliderRow("Stroke Width", layerStrokeSlider),
                sliderRow("Layer Opacity", layerOpacitySlider),
                new Label("Selected layer properties"),
                new HBox(10, new Label("X"), layerXSpinner, new Label("Y"), layerYSpinner),
                new HBox(10, new Label("Width"), layerWidthSpinner, new Label("Height"), layerHeightSpinner),
                new HBox(10, layerVisibleCheck, lockLayerAspectCheck),
                new HBox(10, addTextButton, addAssetButton),
                new HBox(10, pasteLayerButton, addRectButton, addEllipseButton),
                applyLayerButton,
                new Separator(),
                new Label("Export / scaling"),
                new HBox(10, new Label("Width"), resizeWidthSpinner, new Label("Height"), resizeHeightSpinner),
                lockAspectCheck,
                new HBox(10, exportButton, resizeButton),
                saveToLibraryButton
        );
        controls.setPadding(new Insets(4, 16, 4, 4));
        controls.setPrefWidth(370);
        return controls;
    }

    private ScrollPane buildCanvasArea() {
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

        canvas.setOnMousePressed(event -> {
            ProjectLayer hit = findLayerAt(event.getX(), event.getY());
            draggedLayer = hit;
            if (hit != null) {
                layerList.getSelectionModel().select(hit);
                double[] point = canvasToImage(event.getX(), event.getY());
                dragOffsetX = point[0] - hit.getX();
                dragOffsetY = point[1] - hit.getY();
            }
            drawCanvas();
        });
        canvas.setOnMouseDragged(event -> {
            if (draggedLayer == null || currentBase == null) {
                return;
            }
            double[] point = canvasToImage(event.getX(), event.getY());
            draggedLayer.setX(point[0] - dragOffsetX);
            draggedLayer.setY(point[1] - dragOffsetY);
            loadLayerControls(draggedLayer);
            layerList.refresh();
            drawCanvas();
        });
        canvas.setOnMouseReleased(event -> draggedLayer = null);
        return scrollPane;
    }

    private void registerListeners() {
        brightnessSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        contrastSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        saturationSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        tintStrengthSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        borderSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        scaleSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        rotationSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        translateXSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        translateYSlider.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        grayscaleCheck.selectedProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        autoEnhanceCheck.selectedProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        borderColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        tintColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());

        layerList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, layer) -> loadLayerControls(layer));
        layers.addListener((javafx.collections.ListChangeListener<? super ProjectLayer>) change -> drawCanvas());
        layerWidthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> resizeSelectedLayerFromWidth(newValue));
        layerHeightSpinner.valueProperty().addListener((obs, oldValue, newValue) -> resizeSelectedLayerFromHeight(newValue));
        resizeWidthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingResizeSpinners && lockAspectCheck.isSelected() && currentBase != null && newValue != null) {
                updatingResizeSpinners = true;
                int height = Math.max(1, (int) Math.round(newValue * currentBase.getHeight() / (double) currentBase.getWidth()));
                resizeHeightSpinner.getValueFactory().setValue(height);
                updatingResizeSpinners = false;
            }
        });
        resizeHeightSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingResizeSpinners && lockAspectCheck.isSelected() && currentBase != null && newValue != null) {
                updatingResizeSpinners = true;
                int width = Math.max(1, (int) Math.round(newValue * currentBase.getWidth() / (double) currentBase.getHeight()));
                resizeWidthSpinner.getValueFactory().setValue(width);
                updatingResizeSpinners = false;
            }
        });
    }

    private void loadSelectedPhoto(PhotoItem photo) {
        layers.clear();
        if (photo == null) {
            selectedLabel.setText("Selected image: none");
            originalImage = null;
            currentBase = null;
            drawCanvas();
            return;
        }
        try {
            selectedLabel.setText("Selected image: " + photo.getName());
            originalImage = ImageUtils.read(new File(photo.getFilePath()));
            renderBasePreview();
        } catch (Exception ex) {
            Dialogs.error("Load Error", "Could not open the selected image.", ex);
        }
    }

    private void renderBasePreview() {
        if (originalImage == null) {
            return;
        }
        try {
            currentBase = context.getImageProcessingService().applyParameters(originalImage, buildParameters());
            updateResizeSpinnersToCurrentBase();
            drawCanvas();
        } catch (Exception ex) {
            Dialogs.error("Render Error", "Could not render the processed image preview.", ex);
        }
    }

    private EditParameters buildParameters() {
        EditParameters parameters = new EditParameters();
        parameters.setBrightness(brightnessSlider.getValue());
        parameters.setContrast(contrastSlider.getValue());
        parameters.setSaturation(saturationSlider.getValue());
        parameters.setGrayscale(grayscaleCheck.isSelected());
        parameters.setAutoEnhance(autoEnhanceCheck.isSelected());
        parameters.setTintColor(tintColorPicker.getValue());
        parameters.setTintStrength(tintStrengthSlider.getValue());
        parameters.setBorderWidth((int) Math.round(borderSlider.getValue()));
        parameters.setBorderColor(borderColorPicker.getValue());
        parameters.setScale(scaleSlider.getValue());
        parameters.setRotationDegrees(rotationSlider.getValue());
        parameters.setTranslateX(translateXSlider.getValue());
        parameters.setTranslateY(translateYSlider.getValue());
        return parameters;
    }

    private void resetControls() {
        brightnessSlider.setValue(0);
        contrastSlider.setValue(1.0);
        saturationSlider.setValue(1.0);
        grayscaleCheck.setSelected(false);
        autoEnhanceCheck.setSelected(false);
        tintColorPicker.setValue(Color.TRANSPARENT);
        tintStrengthSlider.setValue(0.0);
        borderSlider.setValue(0);
        borderColorPicker.setValue(Color.WHITE);
        scaleSlider.setValue(1.0);
        rotationSlider.setValue(0.0);
        translateXSlider.setValue(0.0);
        translateYSlider.setValue(0.0);
        renderBasePreview();
    }

    private void addTextLayer() {
        if (!ensureBaseImage()) {
            return;
        }
        ProjectLayer layer = ProjectLayer.text(
                textField.getText(),
                fontFamilyBox.getValue(),
                fontSizeSlider.getValue(),
                layerFillPicker.getValue(),
                currentBase.getWidth() * 0.12,
                currentBase.getHeight() * 0.12
        );
        layer.setStrokeColor(layerStrokePicker.getValue());
        layer.setStrokeWidth(layerStrokeSlider.getValue());
        layer.setOpacity(layerOpacitySlider.getValue());
        layers.add(layer);
        layerList.getSelectionModel().select(layer);
        drawCanvas();
    }

    private void addImageLayerFromFile() {
        if (!ensureBaseImage()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Image / Asset Layer");
        chooser.setInitialDirectory(AppPaths.ASSET_DIR.toFile().exists() ? AppPaths.ASSET_DIR.toFile() : AppPaths.EXPORT_DIR.toFile());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Assets", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            addImageLayer(file);
        }
    }

    private void pasteClipboardLayer() {
        if (!ensureBaseImage()) {
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        try {
            if (clipboard.hasFiles()) {
                File firstImage = clipboard.getFiles().stream()
                        .filter(file -> FileUtils.isImageFile(file.toPath()))
                        .findFirst()
                        .orElse(null);
                if (firstImage != null) {
                    File asset = context.getAssetLibraryService().importAsset(firstImage);
                    addImageLayer(asset);
                    return;
                }
            }
            if (clipboard.hasImage()) {
                BufferedImage image = ImageUtils.fromFxImage(clipboard.getImage());
                File asset = context.getAssetLibraryService().stageClipboardImage(image, "pasted-layer");
                File managedAsset = context.getAssetLibraryService().importAsset(asset);
                addImageLayer(managedAsset);
                return;
            }
            Dialogs.warn("Clipboard Empty", "Clipboard does not contain an image file or image data.");
        } catch (Exception ex) {
            Dialogs.error("Paste Error", "Could not paste clipboard content as a layer.", ex);
        }
    }

    private void addImageLayer(File file) {
        try {
            BufferedImage image = ImageUtils.read(file);
            double targetWidth = Math.min(currentBase.getWidth() * 0.35, image.getWidth());
            double targetHeight = targetWidth * image.getHeight() / (double) image.getWidth();
            ProjectLayer layer = ProjectLayer.image(file.getAbsolutePath(), currentBase.getWidth() * 0.10, currentBase.getHeight() * 0.10, targetWidth, targetHeight);
            layer.setOpacity(layerOpacitySlider.getValue());
            layer.setName("Asset: " + file.getName());
            layers.add(layer);
            layerList.getSelectionModel().select(layer);
            drawCanvas();
        } catch (Exception ex) {
            Dialogs.error("Layer Error", "Could not add the selected image layer.", ex);
        }
    }

    private void addShapeLayer(LayerType type) {
        if (!ensureBaseImage()) {
            return;
        }
        ProjectLayer layer = ProjectLayer.shape(
                type,
                currentBase.getWidth() * 0.15,
                currentBase.getHeight() * 0.15,
                currentBase.getWidth() * 0.25,
                currentBase.getHeight() * 0.18,
                layerFillPicker.getValue(),
                layerStrokePicker.getValue(),
                layerStrokeSlider.getValue()
        );
        layer.setOpacity(layerOpacitySlider.getValue());
        layers.add(layer);
        layerList.getSelectionModel().select(layer);
        drawCanvas();
    }

    private void applyLayerControls() {
        ProjectLayer layer = layerList.getSelectionModel().getSelectedItem();
        if (layer == null) {
            Dialogs.warn("No Layer Selected", "Select a layer first.");
            return;
        }
        if (layer.getType() == LayerType.TEXT) {
            layer.setText(textField.getText());
            layer.setFontFamily(fontFamilyBox.getValue());
            layer.setFontSize(fontSizeSlider.getValue());
        }
        layer.setFillColor(layerFillPicker.getValue());
        layer.setStrokeColor(layerStrokePicker.getValue());
        layer.setStrokeWidth(layerStrokeSlider.getValue());
        layer.setOpacity(layerOpacitySlider.getValue());
        layer.setX(layerXSpinner.getValue());
        layer.setY(layerYSpinner.getValue());
        layer.setWidth(layerWidthSpinner.getValue());
        layer.setHeight(layerHeightSpinner.getValue());
        layer.setVisible(layerVisibleCheck.isSelected());
        layerList.refresh();
        drawCanvas();
    }

    private void loadLayerControls(ProjectLayer layer) {
        if (layer == null) {
            return;
        }
        updatingLayerSizeSpinners = true;
        textField.setText(layer.getText() == null ? "" : layer.getText());
        fontFamilyBox.getSelectionModel().select(layer.getFontFamily());
        fontSizeSlider.setValue(layer.getFontSize());
        layerFillPicker.setValue(layer.getFillColor());
        layerStrokePicker.setValue(layer.getStrokeColor());
        layerStrokeSlider.setValue(layer.getStrokeWidth());
        layerOpacitySlider.setValue(layer.getOpacity());
        layerXSpinner.getValueFactory().setValue((int) Math.round(layer.getX()));
        layerYSpinner.getValueFactory().setValue((int) Math.round(layer.getY()));
        layerWidthSpinner.getValueFactory().setValue(Math.max(1, (int) Math.round(layer.getWidth())));
        layerHeightSpinner.getValueFactory().setValue(Math.max(1, (int) Math.round(layer.getHeight())));
        layerVisibleCheck.setSelected(layer.isVisible());
        updatingLayerSizeSpinners = false;
        drawCanvas();
    }

    private void resizeSelectedLayerFromWidth(Integer newWidth) {
        ProjectLayer layer = layerList.getSelectionModel().getSelectedItem();
        if (updatingLayerSizeSpinners || layer == null || newWidth == null) {
            return;
        }
        double oldWidth = Math.max(1.0, layer.getWidth());
        double oldHeight = Math.max(1.0, layer.getHeight());
        if (lockLayerAspectCheck.isSelected()) {
            updatingLayerSizeSpinners = true;
            int newHeight = Math.max(1, (int) Math.round(newWidth * oldHeight / oldWidth));
            layerHeightSpinner.getValueFactory().setValue(newHeight);
            updatingLayerSizeSpinners = false;
        }
    }

    private void resizeSelectedLayerFromHeight(Integer newHeight) {
        ProjectLayer layer = layerList.getSelectionModel().getSelectedItem();
        if (updatingLayerSizeSpinners || layer == null || newHeight == null) {
            return;
        }
        double oldWidth = Math.max(1.0, layer.getWidth());
        double oldHeight = Math.max(1.0, layer.getHeight());
        if (lockLayerAspectCheck.isSelected()) {
            updatingLayerSizeSpinners = true;
            int newWidth = Math.max(1, (int) Math.round(newHeight * oldWidth / oldHeight));
            layerWidthSpinner.getValueFactory().setValue(newWidth);
            updatingLayerSizeSpinners = false;
        }
    }

    private void deleteSelectedLayer() {
        ProjectLayer layer = layerList.getSelectionModel().getSelectedItem();
        if (layer != null) {
            layers.remove(layer);
            drawCanvas();
        }
    }

    private void moveSelectedLayer(int direction) {
        ProjectLayer layer = layerList.getSelectionModel().getSelectedItem();
        if (layer == null) {
            return;
        }
        int index = layers.indexOf(layer);
        int target = direction > 0 ? Math.min(layers.size() - 1, index + 1) : Math.max(0, index - 1);
        if (target != index) {
            layers.remove(index);
            layers.add(target, layer);
            layerList.getSelectionModel().select(layer);
            drawCanvas();
        }
    }

    private void exportCompositeImage(boolean silent) {
        if (!ensureBaseImage()) {
            return;
        }
        try {
            BufferedImage composite = context.getLayerRenderService().renderComposite(currentBase, layers);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Composite Image");
            chooser.setInitialDirectory(AppPaths.EXPORT_DIR.toFile());
            String base = context.getSelectedPhoto() == null ? "layered-image" : FileUtils.slugify(FileUtils.baseName(context.getSelectedPhoto().getName())) + "-layered";
            chooser.setInitialFileName(base + ".png");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                    new FileChooser.ExtensionFilter("JPEG Image", "*.jpg", "*.jpeg")
            );
            File file = chooser.showSaveDialog(getScene().getWindow());
            if (file == null) {
                return;
            }
            ImageUtils.write(composite, file);
            context.setLatestExportFile(file);
            if (!silent) {
                Dialogs.info("Export Complete", "Layered composite exported successfully. The original file was not changed.");
            }
        } catch (Exception ex) {
            Dialogs.error("Export Error", "Could not export the layered image.", ex);
        }
    }

    private void saveCompositeIntoLibrary() {
        if (!ensureBaseImage()) {
            return;
        }
        try {
            BufferedImage composite = context.getLayerRenderService().renderComposite(currentBase, layers);
            String baseName = context.getSelectedPhoto() == null ? "layered-image" : FileUtils.baseName(context.getSelectedPhoto().getName()) + "-layered";
            var imported = context.getLibraryService().saveGeneratedImageToLibrary(composite, baseName);
            context.addImportedPhotos(List.of(imported));
            context.setLatestExportFile(Path.of(imported.getFilePath()).toFile());
            Dialogs.info("Saved to Library", "The composite was imported into the managed library. The source image remains unchanged.");
        } catch (Exception ex) {
            Dialogs.error("Save Error", "Could not save the layered composite into the library.", ex);
        }
    }

    private void exportResizedCopy() {
        if (!ensureBaseImage()) {
            return;
        }
        try {
            int width = Math.max(1, resizeWidthSpinner.getValue());
            int height = Math.max(1, resizeHeightSpinner.getValue());
            BufferedImage composite = context.getLayerRenderService().renderCompositeScaled(currentBase, layers, width, height);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Resized Copy");
            chooser.setInitialDirectory(AppPaths.EXPORT_DIR.toFile());
            String base = context.getSelectedPhoto() == null ? "resized-image" : FileUtils.slugify(FileUtils.baseName(context.getSelectedPhoto().getName())) + "-resized";
            chooser.setInitialFileName(base + ".png");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                    new FileChooser.ExtensionFilter("JPEG Image", "*.jpg", "*.jpeg")
            );
            File file = chooser.showSaveDialog(getScene().getWindow());
            if (file == null) {
                return;
            }
            ImageUtils.write(composite, file);
            context.setLatestExportFile(file);
            Dialogs.info("Resize Export Complete", "A resized copy was exported. The original file was not modified.");
        } catch (Exception ex) {
            Dialogs.error("Resize Error", "Could not export the resized copy.", ex);
        }
    }

    private void drawCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        gc.setFill(Color.rgb(241, 245, 249));
        gc.fillRect(0, 0, cw, ch);
        if (currentBase == null) {
            gc.setFill(Color.rgb(100, 116, 139));
            gc.setFont(Font.font(18));
            gc.fillText("Select an image in Repository to start layer-based editing.", 28, 42);
            return;
        }
        displayScale = Math.min(cw / currentBase.getWidth(), ch / currentBase.getHeight());
        if (!Double.isFinite(displayScale) || displayScale <= 0) {
            displayScale = 1.0;
        }
        double drawW = currentBase.getWidth() * displayScale;
        double drawH = currentBase.getHeight() * displayScale;
        imageOffsetX = (cw - drawW) / 2.0;
        imageOffsetY = (ch - drawH) / 2.0;
        gc.setFill(Color.WHITE);
        gc.fillRect(imageOffsetX, imageOffsetY, drawW, drawH);
        gc.drawImage(ImageUtils.toFxImage(currentBase), imageOffsetX, imageOffsetY, drawW, drawH);
        for (ProjectLayer layer : layers) {
            drawLayer(gc, layer);
        }
        ProjectLayer selected = layerList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            gc.setStroke(Color.rgb(37, 99, 235));
            gc.setLineWidth(2);
            gc.setLineDashes(8, 5);
            gc.strokeRect(imageOffsetX + selected.getX() * displayScale,
                    imageOffsetY + selected.getY() * displayScale,
                    selected.getWidth() * displayScale,
                    selected.getHeight() * displayScale);
            gc.setLineDashes();
        }
    }

    private void drawLayer(GraphicsContext gc, ProjectLayer layer) {
        if (layer == null || !layer.isVisible()) {
            return;
        }
        double x = imageOffsetX + layer.getX() * displayScale;
        double y = imageOffsetY + layer.getY() * displayScale;
        double w = layer.getWidth() * displayScale;
        double h = layer.getHeight() * displayScale;
        gc.save();
        gc.setGlobalAlpha(layer.getOpacity());
        if (layer.getType() == LayerType.TEXT) {
            gc.setFont(Font.font(layer.getFontFamily(), layer.getFontSize() * displayScale));
            if (layer.getStrokeWidth() > 0) {
                gc.setStroke(layer.getStrokeColor());
                gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * displayScale));
                gc.strokeText(layer.getText(), x, y + layer.getFontSize() * displayScale);
            }
            gc.setFill(layer.getFillColor());
            gc.fillText(layer.getText(), x, y + layer.getFontSize() * displayScale);
        } else if (layer.getType() == LayerType.IMAGE) {
            if (layer.getSourcePath() != null) {
                try {
                    Image image = new Image(new File(layer.getSourcePath()).toURI().toString(), false);
                    gc.drawImage(image, x, y, w, h);
                } catch (Exception ignored) {
                    gc.setFill(Color.rgb(220, 38, 38, 0.35));
                    gc.fillRect(x, y, w, h);
                }
            }
        } else if (layer.getType() == LayerType.RECTANGLE) {
            gc.setFill(layer.getFillColor());
            gc.fillRect(x, y, w, h);
            if (layer.getStrokeWidth() > 0) {
                gc.setStroke(layer.getStrokeColor());
                gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * displayScale));
                gc.strokeRect(x, y, w, h);
            }
        } else if (layer.getType() == LayerType.ELLIPSE) {
            gc.setFill(layer.getFillColor());
            gc.fillOval(x, y, w, h);
            if (layer.getStrokeWidth() > 0) {
                gc.setStroke(layer.getStrokeColor());
                gc.setLineWidth(Math.max(1, layer.getStrokeWidth() * displayScale));
                gc.strokeOval(x, y, w, h);
            }
        }
        gc.restore();
    }

    private ProjectLayer findLayerAt(double canvasX, double canvasY) {
        if (currentBase == null) {
            return null;
        }
        double[] point = canvasToImage(canvasX, canvasY);
        double x = point[0];
        double y = point[1];
        for (int i = layers.size() - 1; i >= 0; i--) {
            ProjectLayer layer = layers.get(i);
            if (!layer.isVisible()) {
                continue;
            }
            if (x >= layer.getX() && x <= layer.getX() + layer.getWidth()
                    && y >= layer.getY() && y <= layer.getY() + layer.getHeight()) {
                return layer;
            }
        }
        return null;
    }

    private double[] canvasToImage(double canvasX, double canvasY) {
        return new double[]{(canvasX - imageOffsetX) / displayScale, (canvasY - imageOffsetY) / displayScale};
    }

    private boolean ensureBaseImage() {
        if (currentBase == null) {
            Dialogs.warn("No Image", "Select and load an image first.");
            return false;
        }
        return true;
    }

    private void updateResizeSpinnersToCurrentBase() {
        if (currentBase == null) {
            return;
        }
        updatingResizeSpinners = true;
        resizeWidthSpinner.getValueFactory().setValue(currentBase.getWidth());
        resizeHeightSpinner.getValueFactory().setValue(currentBase.getHeight());
        updatingResizeSpinners = false;
    }

    private VBox sliderRow(String title, Slider slider) {
        Label valueLabel = new Label();
        String format = title.equals("Contrast") || title.equals("Scale") || title.equals("Saturation") || title.equals("Tint Strength") || title.equals("Layer Opacity") ? "%.2f" : "%.0f";
        valueLabel.textProperty().bind(slider.valueProperty().asString(format));
        HBox header = new HBox(8, new Label(title), spacer(), valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, header, slider);
    }

    private VBox labelledControl(String title, javafx.scene.Node node) {
        return new VBox(6, new Label(title), node);
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void configureSlider(Slider slider, boolean integerLabels) {
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setBlockIncrement(integerLabels ? 1 : 0.1);
        slider.setMajorTickUnit((slider.getMax() - slider.getMin()) / 4.0);
    }
}

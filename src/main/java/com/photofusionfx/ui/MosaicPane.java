package com.photofusionfx.ui;

import com.photofusionfx.AppContext;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.service.AppPaths;
import com.photofusionfx.util.Dialogs;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MosaicPane extends BorderPane {
    private final AppContext context;
    private final ComboBox<PhotoItem> targetComboBox = new ComboBox<>();
    private final CheckBox favouritesOnlyCheckBox = new CheckBox("Use favourite images as tiles only");
    private final Spinner<Integer> columnsSpinner = new Spinner<>();
    private final Spinner<Integer> tileSizeSpinner = new Spinner<>();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final ImageView previewView = new ImageView();
    private final Label notesLabel = new Label();

    private BufferedImage currentMosaic;

    public MosaicPane(AppContext context) {
        this.context = context;
        setPadding(new Insets(12));
        setTop(buildControls());
        setCenter(buildPreview());

        targetComboBox.setItems(context.getPhotoLibrary());
        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> targetComboBox.getSelectionModel().select(newValue));
        context.getPhotoLibrary().addListener((javafx.collections.ListChangeListener<? super PhotoItem>) change -> {
            targetComboBox.setItems(context.getPhotoLibrary());
            if (targetComboBox.getSelectionModel().getSelectedItem() == null && !context.getPhotoLibrary().isEmpty()) {
                targetComboBox.getSelectionModel().select(context.getPhotoLibrary().getFirst());
            }
        });
        if (context.getSelectedPhoto() != null) {
            targetComboBox.getSelectionModel().select(context.getSelectedPhoto());
        }
    }

    private ScrollPane buildControls() {
        columnsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(20, 160, 80));
        tileSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(12, 64, 24));
        columnsSpinner.setEditable(true);
        tileSizeSpinner.setEditable(true);

        Button generateButton = new Button("Generate Mosaic");
        generateButton.setOnAction(e -> generateMosaic());
        generateButton.getStyleClass().add("primary-button");

        Button exportButton = new Button("Export Mosaic");
        exportButton.setOnAction(e -> exportMosaic());
        exportButton.getStyleClass().add("success-button");

        Button saveToLibraryButton = new Button("Save Mosaic into Library");
        saveToLibraryButton.setOnAction(e -> saveMosaicIntoLibrary());
        saveToLibraryButton.getStyleClass().add("success-button");

        progressBar.setPrefWidth(280);
        progressBar.setVisible(false);

        notesLabel.setWrapText(true);
        notesLabel.setText("Image Mosaic builds a large composite image from smaller photo tiles in your collection. Each target cell is matched by colour and brightness, repeated neighbours are discouraged, and tiles are gently colour-balanced so the final image remains recognizable while still being made from photographs.");
        notesLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        HBox row1 = new HBox(12,
                new Label("Target Image"), targetComboBox,
                new Label("Columns"), columnsSpinner,
                new Label("Tile Size"), tileSizeSpinner,
                favouritesOnlyCheckBox,
                spacer(),
                progressBar
        );
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(targetComboBox, Priority.ALWAYS);

        HBox row2 = new HBox(12, generateButton, exportButton, saveToLibraryButton);
        row2.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(10, row1, row2, notesLabel);
        controls.setPadding(new Insets(0, 0, 12, 0));
        
        // Ensure the long row of spinners and checkboxes doesn't squish
        controls.setMinWidth(900); 

        // Wrap controls in ScrollPane
        ScrollPane scrollPane = new ScrollPane(controls);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;"); 
        
        return scrollPane;
    }

    private ScrollPane buildPreview() {
        previewView.setPreserveRatio(true);
        previewView.setFitWidth(1100);
        previewView.setFitHeight(760);
        
        BorderPane pane = new BorderPane(previewView);
        pane.getStyleClass().add("preview-box");
        pane.setPadding(new Insets(12));

        // Wrap the large image in a ScrollPane so users can pan around it
        ScrollPane scrollPane = new ScrollPane(pane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        return scrollPane;
    }

    private void generateMosaic() {
        PhotoItem target = targetComboBox.getSelectionModel().getSelectedItem();
        if (target == null) {
            Dialogs.warn("No Target Image", "Choose a target image for the mosaic first.");
            return;
        }
        List<PhotoItem> sourceItems = context.getPhotoLibrary().stream()
                .filter(item -> !favouritesOnlyCheckBox.isSelected() || item.isFavorite())
                .filter(item -> !Objects.equals(item.getFilePath(), target.getFilePath()) || context.getPhotoLibrary().size() == 1)
                .collect(Collectors.toList());
        if (sourceItems.isEmpty()) {
            Dialogs.warn("No Tiles Available", "No source images are available for mosaic generation with the chosen filter.");
            return;
        }

        Task<BufferedImage> task = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                BufferedImage targetImage = ImageUtils.read(new File(target.getFilePath()));
                List<BufferedImage> sources = sourceItems.stream()
                        .map(item -> {
                            try {
                                return ImageUtils.read(new File(item.getFilePath()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList();
                return context.getMosaicService().generateMosaic(
                        targetImage,
                        sources,
                        columnsSpinner.getValue(),
                        tileSizeSpinner.getValue(),
                        progress -> updateProgress((long) (progress * 1000), 1000)
                );
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        task.setOnSucceeded(e -> {
            currentMosaic = task.getValue();
            previewView.setImage(ImageUtils.toFxImage(currentMosaic));
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
        if (file == null) {
            return;
        }
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

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}

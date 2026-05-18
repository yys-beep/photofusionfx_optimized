package com.photofusionfx.ui;

import com.photofusionfx.AppContext;
import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.util.Dialogs;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class RepositoryPane extends BorderPane {
    private final AppContext context;
    private final TilePane tilePane = new TilePane();
    private final ImageView previewImageView = new ImageView();
    private final Label nameValue = new Label("—");
    private final Label pathValue = new Label("—");
    private final Label importedAtValue = new Label("—");
    private final TextArea annotationArea = new TextArea();
    private final CheckBox favouriteCheckBox = new CheckBox("Favourite photo");
    private final TextField searchField = new TextField();
    private final ComboBox<String> filterBox = new ComboBox<>();
    private boolean loadingDetails;

    public RepositoryPane(AppContext context) {
        this.context = context;
        setPadding(new Insets(12));
        setTop(buildToolbar());
        setCenter(buildContent());

        context.getPhotoLibrary().addListener((javafx.collections.ListChangeListener<? super PhotoItem>) change -> rebuildTiles());
        context.selectedPhotoProperty().addListener((obs, oldValue, newValue) -> {
            loadDetails(newValue);
            rebuildTiles();
        });

        rebuildTiles();
        loadDetails(context.getSelectedPhoto());
    }

    private HBox buildToolbar() {
        Button importImagesButton = new Button("Import Images");
        importImagesButton.setOnAction(e -> importImages());
        importImagesButton.getStyleClass().add("primary-button");

        Button importFolderButton = new Button("Import Folder");
        importFolderButton.setOnAction(e -> importFolder());
        importFolderButton.getStyleClass().add("primary-button");

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshFromDatabase());

        searchField.setPromptText("Search by name or annotation");
        searchField.textProperty().addListener((obs, oldValue, newValue) -> rebuildTiles());

        filterBox.getItems().setAll("All", "Annotated", "Favourites");
        filterBox.getSelectionModel().selectFirst();
        filterBox.valueProperty().addListener((obs, oldValue, newValue) -> rebuildTiles());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, importImagesButton, importFolderButton, refreshButton, spacer,
                new Label("Filter:"), filterBox, searchField);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 12, 0));
        return bar;
    }

    private SplitPane buildContent() {
        // Right details panel default width at startup (user can resize by dragging divider)
        final double DETAILS_DEFAULT_WIDTH = 500;

        tilePane.setHgap(12);
        tilePane.setVgap(12);
        tilePane.setPrefColumns(3);
        tilePane.setPadding(new Insets(6));

        ScrollPane libraryScroll = new ScrollPane(tilePane);
        libraryScroll.setFitToWidth(true);
        libraryScroll.setFitToHeight(true);
        libraryScroll.getStyleClass().add("library-scroll");

        VBox detailsPanel = new VBox(12);
        detailsPanel.setPadding(new Insets(8, 4, 8, 16));

        previewImageView.setPreserveRatio(true);
        previewImageView.setFitWidth(760);
        previewImageView.setFitHeight(480);

        StackPane previewBox = new StackPane(previewImageView);
        previewBox.getStyleClass().add("preview-box");
        previewBox.setMinHeight(500);

        annotationArea.setPromptText("Add a personalized note for the selected image...");
        annotationArea.setPrefRowCount(5);

        favouriteCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (loadingDetails || context.getSelectedPhoto() == null) {
                return;
            }
            try {
                context.getSelectedPhoto().setFavorite(newValue);
                context.getLibraryService().updateFavorite(context.getSelectedPhoto());
                rebuildTiles();
            } catch (SQLException ex) {
                Dialogs.error("Save Error", "Could not update favourite state.", ex);
            }
        });

        Button saveAnnotationButton = new Button("Save Annotation");
        saveAnnotationButton.setOnAction(e -> saveAnnotation());
        saveAnnotationButton.getStyleClass().add("success-button");

        Button openLocationButton = new Button("Open File Location");
        openLocationButton.setOnAction(e -> openFileLocation());

        Button deleteButton = new Button("Delete from Library");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(e -> deleteSelected());

        VBox metadataBox = new VBox(8,
                labelledLine("Name", nameValue),
                labelledLine("Path", pathValue),
                labelledLine("Imported", importedAtValue),
                favouriteCheckBox,
                new Label("Annotation"),
                annotationArea,
                new HBox(10, saveAnnotationButton, openLocationButton, deleteButton)
        );
        detailsPanel.getChildren().addAll(previewBox, metadataBox);

        ScrollPane detailsScroll = new ScrollPane(detailsPanel);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailsScroll.setStyle("-fx-background-color: transparent;");

        // Default width at startup, but resizable
        detailsScroll.setPrefWidth(DETAILS_DEFAULT_WIDTH);
        detailsScroll.setMinWidth(320);
        detailsScroll.setMaxWidth(Double.MAX_VALUE);

        SplitPane splitPane = new SplitPane(libraryScroll, detailsScroll);

        // IMPORTANT:
        // Use Platform.runLater so SplitPane has a real width. Then set divider once to make
        // the right panel start at ~DETAILS_DEFAULT_WIDTH pixels, while still allowing user resizing.
        Platform.runLater(() -> {
            double totalW = splitPane.getWidth();
            if (totalW <= 0) {
                return;
            }
            double pos = (totalW - DETAILS_DEFAULT_WIDTH) / totalW;
            pos = Math.max(0.05, Math.min(0.95, pos));
            splitPane.setDividerPositions(pos);
        });

        return splitPane;
    }

    private VBox labelledLine(String key, Label value) {
        Label title = new Label(key);
        title.getStyleClass().add("field-title");
        value.setWrapText(true);
        return new VBox(4, title, value);
    }

    private void importImages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Images");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp"
        ));
        List<File> selected = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (selected == null || selected.isEmpty()) {
            return;
        }
        try {
            List<PhotoItem> imported = context.getLibraryService().importFiles(selected);
            context.addImportedPhotos(imported);
            rebuildTiles();
            Dialogs.info("Import Complete", imported.size() + " images imported.");
        } catch (Exception ex) {
            Dialogs.error("Import Error", "Could not import selected images.", ex);
        }
    }

    private void importFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Import Folder");
        File folder = chooser.showDialog(getScene().getWindow());
        if (folder == null) {
            return;
        }
        try {
            List<PhotoItem> imported = context.getLibraryService().importDirectory(folder);
            context.addImportedPhotos(imported);
            rebuildTiles();
            Dialogs.info("Import Complete", imported.size() + " images imported from folder.");
        } catch (Exception ex) {
            Dialogs.error("Import Error", "Could not import images from the selected folder.", ex);
        }
    }

    private void refreshFromDatabase() {
        try {
            String selectedPath = context.getSelectedPhoto() == null ? null : context.getSelectedPhoto().getFilePath();
            context.loadInitialLibrary();
            if (selectedPath != null) {
                Optional<PhotoItem> match = context.findByPath(selectedPath);
                match.ifPresent(photoItem -> context.setSelectedPhoto(photoItem));
            }
            rebuildTiles();
        } catch (Exception ex) {
            Dialogs.error("Refresh Error", "Could not refresh the library.", ex);
        }
    }

    private void rebuildTiles() {
        tilePane.getChildren().clear();
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = filterBox.getValue();

        for (PhotoItem photo : context.getPhotoLibrary()) {
            if (!matchesSearchAndFilter(photo, search, filter)) {
                continue;
            }
            tilePane.getChildren().add(createCard(photo));
        }
    }

    private boolean matchesSearchAndFilter(PhotoItem photo, String search, String filter) {
        boolean matchesSearch = search.isBlank()
                || photo.getName().toLowerCase(Locale.ROOT).contains(search)
                || photo.getAnnotation().toLowerCase(Locale.ROOT).contains(search);

        if (!matchesSearch) {
            return false;
        }

        return switch (filter) {
            case "Annotated" -> photo.hasAnnotation();
            case "Favourites" -> photo.isFavorite();
            default -> true;
        };
    }

    private StackPane createCard(PhotoItem photo) {
        Image image = new Image(Path.of(photo.getFilePath()).toUri().toString(), 170, 120, true, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(170);
        imageView.setFitHeight(120);

        StackPane imageBox = new StackPane(imageView);
        imageBox.getStyleClass().add("thumbnail-image-box");
        imageBox.setPrefSize(190, 130);

        Label heart = new Label("♥");
        heart.getStyleClass().add("heart-badge");
        heart.setVisible(photo.hasAnnotation());
        StackPane.setAlignment(heart, Pos.TOP_RIGHT);

        Label star = new Label("★");
        star.getStyleClass().add("star-badge");
        star.setVisible(photo.isFavorite());
        StackPane.setAlignment(star, Pos.TOP_LEFT);

        Label caption = new Label(photo.getName());
        caption.getStyleClass().add("thumbnail-caption");
        caption.setWrapText(true);

        caption.setCursor(javafx.scene.Cursor.HAND);
        caption.setTooltip(new javafx.scene.control.Tooltip("Double-click to rename"));

        VBox cardContent = new VBox(8, new StackPane(imageBox, heart, star), caption);
        cardContent.setPadding(new Insets(10));
        cardContent.getStyleClass().add("thumbnail-card");
        if (context.getSelectedPhoto() != null && context.getSelectedPhoto().getId() == photo.getId()) {
            cardContent.getStyleClass().add("selected-thumbnail");
        }

        StackPane wrapper = new StackPane(cardContent);
        wrapper.setOnMouseClicked(e -> context.setSelectedPhoto(photo));
        return wrapper;
    }

    private void loadDetails(PhotoItem photo) {
        loadingDetails = true;
        try {
            if (photo == null) {
                previewImageView.setImage(null);
                nameValue.setText("—");
                pathValue.setText("—");
                importedAtValue.setText("—");
                annotationArea.clear();
                favouriteCheckBox.setSelected(false);
                return;
            }
            previewImageView.setImage(new Image(Path.of(photo.getFilePath()).toUri().toString(), 0, 720, true, true, true));
            nameValue.setText(photo.getName());
            pathValue.setText(photo.getFilePath());
            importedAtValue.setText(photo.getImportedAt());
            annotationArea.setText(photo.getAnnotation());
            favouriteCheckBox.setSelected(photo.isFavorite());
        } finally {
            loadingDetails = false;
        }
    }

    private void saveAnnotation() {
        PhotoItem selected = context.getSelectedPhoto();
        if (selected == null) {
            Dialogs.warn("No Selection", "Select an image first.");
            return;
        }
        try {
            selected.setAnnotation(annotationArea.getText());
            context.getLibraryService().updateAnnotation(selected);
            rebuildTiles();
            Dialogs.info("Saved", "Annotation saved for the selected image.");
        } catch (SQLException ex) {
            Dialogs.error("Save Error", "Could not save the annotation.", ex);
        }
    }

    private void openFileLocation() {
        PhotoItem selected = context.getSelectedPhoto();
        if (selected == null) {
            return;
        }
        try {
            File parent = new File(selected.getFilePath()).getParentFile();
            if (parent != null && parent.exists()) {
                Desktop.getDesktop().open(parent);
            }
        } catch (Exception ex) {
            Dialogs.error("Open Location Error", "Could not open the file location.", ex);
        }
    }

    private void deleteSelected() {
        PhotoItem selected = context.getSelectedPhoto();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the selected image from the managed library?",
                ButtonType.YES,
                ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }
        try {
            context.getLibraryService().deletePhoto(selected);
            context.removePhoto(selected);
            rebuildTiles();
        } catch (Exception ex) {
            Dialogs.error("Delete Error", "Could not delete the selected image.", ex);
        }
    }
}
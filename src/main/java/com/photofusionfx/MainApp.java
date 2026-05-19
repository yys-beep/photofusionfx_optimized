package com.photofusionfx;

import com.photofusionfx.service.AssetLibraryService;
import com.photofusionfx.service.DatabaseManager;
import com.photofusionfx.service.EmailService;
import com.photofusionfx.service.ImageProcessingService;
import com.photofusionfx.service.LayerRenderService;
import com.photofusionfx.service.LibraryService;
import com.photofusionfx.service.MosaicService;
import com.photofusionfx.service.ObjectExtractionService;
import com.photofusionfx.service.VideoService;
import com.photofusionfx.ui.AssetLibraryPane;
import com.photofusionfx.ui.EditorPane;
import com.photofusionfx.ui.ExtractorPane;
import com.photofusionfx.ui.MosaicPane;
import com.photofusionfx.ui.RepositoryPane;
import com.photofusionfx.ui.SharePane;
import com.photofusionfx.ui.VideoPane;
import com.photofusionfx.util.Dialogs;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {
@Override
    public void start(Stage stage) {
        try {
            // 1. Initialize all background services
            DatabaseManager databaseManager = new DatabaseManager();
            LibraryService libraryService = new LibraryService(databaseManager);
            ImageProcessingService imageProcessingService = new ImageProcessingService();
            ObjectExtractionService extractionService = new ObjectExtractionService();
            MosaicService mosaicService = new MosaicService();
            VideoService videoService = new VideoService();
            EmailService emailService = new EmailService();
            AssetLibraryService assetLibraryService = new AssetLibraryService();
            LayerRenderService layerRenderService = new LayerRenderService();

            AppContext context = new AppContext(
                    libraryService,
                    imageProcessingService,
                    extractionService,
                    mosaicService,
                    videoService,
                    emailService,
                    assetLibraryService,
                    layerRenderService
            );
            context.loadInitialLibrary();

            // 2. Create all your Views
            com.photofusionfx.ui.RepositoryPane repositoryPane = new com.photofusionfx.ui.RepositoryPane(context);
            com.photofusionfx.ui.EditorPane editorPane = new com.photofusionfx.ui.EditorPane(context);
            com.photofusionfx.ui.ExtractorPane extractorPane = new com.photofusionfx.ui.ExtractorPane(context);
            com.photofusionfx.ui.AssetLibraryPane assetLibraryPane = new com.photofusionfx.ui.AssetLibraryPane(context);
            com.photofusionfx.ui.MosaicPane mosaicPane = new com.photofusionfx.ui.MosaicPane(context);
            com.photofusionfx.ui.VideoPane videoPane = new com.photofusionfx.ui.VideoPane(context);
            com.photofusionfx.ui.SharePane sharePane = new com.photofusionfx.ui.SharePane(context);

                // 3. Setup Original TabPane
            TabPane tabPane = new TabPane(
                    new Tab("Repository", repositoryPane),      // Tab 0
                    new Tab("Image Editor", editorPane),        // Tab 1
                    new Tab("Object Extractor", extractorPane), // Tab 2
                    new Tab("Mosaic Studio", mosaicPane),       // Tab 3 
                    new Tab("Asset Library", assetLibraryPane), // Tab 4 
                    new Tab("Video Studio", videoPane),         // Tab 5
                    new Tab("Share", sharePane)                 // Tab 6
            );
            tabPane.getTabs().forEach(tab -> tab.setClosable(false));

            // 4. Contextual Deep-Linking support
            context.activeTabProperty().addListener((obs, oldVal, newVal) -> {
                tabPane.getSelectionModel().select(newVal.intValue());
            });

            tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
                context.activeTabProperty().set(newVal.intValue());
            });

            // 5. Final Layout Assembly
            BorderPane root = new BorderPane();
            root.setCenter(tabPane);
            root.setBottom(createStatusBar(context));

            Scene scene = new Scene(root, 1250, 800);
            if (getClass().getResource("/styles/app.css") != null) {
                scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            }

            stage.setTitle("PhotoFusion FX Studio");
            stage.getIcons().setAll(loadAppIcons());
            stage.setScene(scene);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            
            stage.show();
            stage.setMaximized(true);
        } catch (Exception e) {
            Dialogs.error("Startup Error", "The application could not start.", e);
        }
    }

    private List<Image> loadAppIcons() {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<Image> icons = new ArrayList<>();
        var iconUrl = getClass().getResource("/icons/app-icon.png");
        if (iconUrl != null) {
            String iconPath = iconUrl.toExternalForm();
            for (int size : sizes) {
                icons.add(new Image(iconPath, size, size, true, true));
            }
            return icons;
        }
        for (int size : sizes) {
            icons.add(createDefaultPhotoIcon(size));
        }
        return icons;
    }

    private Image createDefaultPhotoIcon(int size) {
        WritableImage icon = new WritableImage(size, size);
        PixelWriter writer = icon.getPixelWriter();

        Color transparent = Color.TRANSPARENT;
        Color frame = Color.rgb(37, 99, 235);
        Color frameDark = Color.rgb(30, 64, 175);
        Color paper = Color.rgb(248, 250, 252);
        Color sky = Color.rgb(125, 211, 252);
        Color mountain = Color.rgb(34, 197, 94);
        Color mountainDark = Color.rgb(22, 163, 74);
        Color sun = Color.rgb(250, 204, 21);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                writer.setColor(x, y, transparent);
            }
        }

        int left = Math.max(1, (int) Math.round(size * 0.10));
        int right = Math.max(left + 1, (int) Math.round(size * 0.90));
        int top = Math.max(1, (int) Math.round(size * 0.12));
        int bottom = Math.max(top + 1, (int) Math.round(size * 0.90));
        int borderWidth = Math.max(2, size / 12);

        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                boolean border = x < left + borderWidth || x >= right - borderWidth
                        || y < top + borderWidth || y >= bottom - borderWidth;
                writer.setColor(x, y, border ? frame : paper);
            }
        }

        int imageLeft = left + borderWidth + Math.max(1, size / 18);
        int imageRight = right - borderWidth - Math.max(1, size / 18);
        int imageTop = top + borderWidth + Math.max(1, size / 18);
        int imageBottom = bottom - borderWidth - Math.max(1, size / 18);
        int horizon = imageTop + (int) Math.round((imageBottom - imageTop) * 0.62);

        for (int y = imageTop; y < horizon; y++) {
            for (int x = imageLeft; x < imageRight; x++) {
                writer.setColor(x, y, sky);
            }
        }

        int sunX = imageLeft + (int) Math.round((imageRight - imageLeft) * 0.76);
        int sunY = imageTop + (int) Math.round((horizon - imageTop) * 0.45);
        int sunRadius = Math.max(2, size / 16);
        for (int y = sunY - sunRadius; y <= sunY + sunRadius; y++) {
            for (int x = sunX - sunRadius; x <= sunX + sunRadius; x++) {
                if (x >= 0 && y >= 0 && x < size && y < size && Math.hypot(x - sunX, y - sunY) <= sunRadius) {
                    writer.setColor(x, y, sun);
                }
            }
        }

        int leftPeak = imageLeft + (int) Math.round((imageRight - imageLeft) * 0.36);
        int rightPeak = imageLeft + (int) Math.round((imageRight - imageLeft) * 0.72);
        for (int y = horizon - 2; y < imageBottom; y++) {
            for (int x = imageLeft; x < imageRight; x++) {
                int leftMountain = imageBottom - Math.abs(x - leftPeak);
                int rightMountain = imageBottom - Math.abs(x - rightPeak);
                if (y >= leftMountain) {
                    writer.setColor(x, y, mountainDark);
                } else if (y >= rightMountain) {
                    writer.setColor(x, y, mountain);
                }
            }
        }

        for (int x = left; x < right; x++) {
            writer.setColor(x, bottom - 1, frameDark);
        }
        for (int y = top; y < bottom; y++) {
            writer.setColor(right - 1, y, frameDark);
        }

        return icon;
    }

    private HBox createStatusBar(AppContext context) {
        Label selectedPhoto = new Label();
        selectedPhoto.textProperty().bind(Bindings.createStringBinding(
                () -> context.getSelectedPhoto() == null
                        ? "Selected: none"
                        : "Selected: " + context.getSelectedPhoto().getName(),
                context.selectedPhotoProperty()
        ));

        Label exportLabel = new Label();
        exportLabel.textProperty().bind(Bindings.createStringBinding(
                () -> context.getLatestExportFile() == null
                        ? "Latest export: none"
                        : "Latest export: " + context.getLatestExportFile().getAbsolutePath(),
                context.latestExportFileProperty()
        ));
        exportLabel.setMaxWidth(Double.MAX_VALUE);
        HBox box = new HBox(24, selectedPhoto, exportLabel);
        HBox.setHgrow(exportLabel, javafx.scene.layout.Priority.ALWAYS);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.getStyleClass().add("status-bar");
        return box;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
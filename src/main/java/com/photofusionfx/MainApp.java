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

            RepositoryPane repositoryPane = new RepositoryPane(context);
            EditorPane editorPane = new EditorPane(context);
            ExtractorPane extractorPane = new ExtractorPane(context);
            MosaicPane mosaicPane = new MosaicPane(context);
            VideoPane videoPane = new VideoPane(context);
            SharePane sharePane = new SharePane(context);
            AssetLibraryPane assetLibraryPane = new AssetLibraryPane(context);

            TabPane tabPane = new TabPane(
                    new Tab("Repository", repositoryPane),
                    new Tab("Editor", editorPane),
                    new Tab("Object Extractor", extractorPane),
                    new Tab("Asset Library", assetLibraryPane),
                    new Tab("Mosaic Studio", mosaicPane),
                    new Tab("Video Studio", videoPane),
                    new Tab("Share", sharePane)
            );
            tabPane.getTabs().forEach(tab -> tab.setClosable(false));

            BorderPane root = new BorderPane();
            root.setCenter(tabPane);
            root.setBottom(createStatusBar(context));

            Scene scene = new Scene(root, 1024, 768);
            String css = getClass().getResource("/styles/app.css").toExternalForm();
            scene.getStylesheets().add(css);

            stage.setTitle("PhotoFusion FX");
            stage.getIcons().setAll(loadAppIcons());
            stage.setScene(scene);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            
            // FIX: Show the window FIRST so the OS calculates its safe central position
            stage.show();
            
            // THEN maximize it. Now when you shrink it, it will return to the safe position!
            stage.setMaximized(true);
        } catch (Exception e) {
            Dialogs.error("Startup Error", "The application could not start.", e);
        }
    }// end of start()

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

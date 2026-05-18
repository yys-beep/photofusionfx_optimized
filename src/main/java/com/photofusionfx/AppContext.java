package com.photofusionfx;

import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.service.AssetLibraryService;
import com.photofusionfx.service.EmailService;
import com.photofusionfx.service.ImageProcessingService;   
import com.photofusionfx.service.LayerRenderService;    
import com.photofusionfx.service.LibraryService;
import com.photofusionfx.service.MosaicService;
import com.photofusionfx.service.ObjectExtractionService;
import com.photofusionfx.service.VideoService;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppContext {
    private final LibraryService libraryService;
    private final ImageProcessingService imageProcessingService;
    private final ObjectExtractionService objectExtractionService;
    private final MosaicService mosaicService;
    private final VideoService videoService;
    private final EmailService emailService;
    private final AssetLibraryService assetLibraryService;
    private final LayerRenderService layerRenderService;

    private final ObservableList<PhotoItem> photoLibrary = FXCollections.observableArrayList(
            photo -> new Observable[]{photo.nameProperty(), photo.annotationProperty(), photo.favoriteProperty()}
    );
    private final ObjectProperty<PhotoItem> selectedPhoto = new SimpleObjectProperty<>();
    private final ObjectProperty<File> latestExportFile = new SimpleObjectProperty<>();

    public AppContext(LibraryService libraryService,
                      ImageProcessingService imageProcessingService,
                      ObjectExtractionService objectExtractionService,
                      MosaicService mosaicService,
                      VideoService videoService,
                      EmailService emailService,
                      AssetLibraryService assetLibraryService,
                      LayerRenderService layerRenderService) {
        this.libraryService = libraryService;
        this.imageProcessingService = imageProcessingService;
        this.objectExtractionService = objectExtractionService;
        this.mosaicService = mosaicService;
        this.videoService = videoService;
        this.emailService = emailService;
        this.assetLibraryService = assetLibraryService;
        this.layerRenderService = layerRenderService;
    }

    public void loadInitialLibrary() throws SQLException {
        List<PhotoItem> loaded = libraryService.loadAllPhotos();
        photoLibrary.setAll(loaded);
        if (!photoLibrary.isEmpty() && getSelectedPhoto() == null) {
            setSelectedPhoto(photoLibrary.getFirst());
        }
    }

    public void addImportedPhotos(List<PhotoItem> imported) {
        if (imported == null || imported.isEmpty()) {
            return;
        }
        PhotoItem firstNew = null;
        for (PhotoItem item : imported) {
            boolean exists = photoLibrary.stream().anyMatch(existing -> existing.getFilePath().equals(item.getFilePath()));
            if (!exists) {
                photoLibrary.add(0, item);
                if (firstNew == null) {
                    firstNew = item;
                }
            }
        }
        if (firstNew != null) {
            setSelectedPhoto(firstNew);
        }
    }

    public void removePhoto(PhotoItem photoItem) {
        photoLibrary.remove(photoItem);
        if (photoItem == getSelectedPhoto()) {
            setSelectedPhoto(photoLibrary.isEmpty() ? null : photoLibrary.getFirst());
        }
    }

    public Optional<PhotoItem> findByPath(String path) {
        return photoLibrary.stream().filter(p -> p.getFilePath().equals(path)).findFirst();
    }

    public LibraryService getLibraryService() {
        return libraryService;
    }

    public ImageProcessingService getImageProcessingService() {
        return imageProcessingService;
    }

    public ObjectExtractionService getObjectExtractionService() {
        return objectExtractionService;
    }

    public MosaicService getMosaicService() {
        return mosaicService;
    }

    public VideoService getVideoService() {
        return videoService;
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public AssetLibraryService getAssetLibraryService() {
        return assetLibraryService;
    }

    public LayerRenderService getLayerRenderService() {
        return layerRenderService;
    }

    public ObservableList<PhotoItem> getPhotoLibrary() {
        return photoLibrary;
    }

    public PhotoItem getSelectedPhoto() {
        return selectedPhoto.get();
    }

    public void setSelectedPhoto(PhotoItem selectedPhoto) {
        this.selectedPhoto.set(selectedPhoto);
    }

    public ObjectProperty<PhotoItem> selectedPhotoProperty() {
        return selectedPhoto;
    }

    public File getLatestExportFile() {
        return latestExportFile.get();
    }

    public void setLatestExportFile(File latestExportFile) {
        this.latestExportFile.set(latestExportFile);
    }

    public ObjectProperty<File> latestExportFileProperty() {
        return latestExportFile;
    }
}

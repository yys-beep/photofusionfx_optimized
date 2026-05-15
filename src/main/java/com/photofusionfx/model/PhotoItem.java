package com.photofusionfx.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PhotoItem {
    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty filePath = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty annotation = new SimpleStringProperty("");
    private final BooleanProperty favorite = new SimpleBooleanProperty(false);
    private final StringProperty checksum = new SimpleStringProperty();
    private final StringProperty importedAt = new SimpleStringProperty();

    public PhotoItem() {
    }

    public PhotoItem(long id, String filePath, String name, String annotation, boolean favorite, String checksum, String importedAt) {
        setId(id);
        setFilePath(filePath);
        setName(name);
        setAnnotation(annotation == null ? "" : annotation);
        setFavorite(favorite);
        setChecksum(checksum);
        setImportedAt(importedAt);
    }

    public long getId() {
        return id.get();
    }

    public void setId(long value) {
        id.set(value);
    }

    public LongProperty idProperty() {
        return id;
    }

    public String getFilePath() {
        return filePath.get();
    }

    public void setFilePath(String value) {
        filePath.set(value);
    }

    public StringProperty filePathProperty() {
        return filePath;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getAnnotation() {
        return annotation.get();
    }

    public void setAnnotation(String value) {
        annotation.set(value == null ? "" : value);
    }

    public StringProperty annotationProperty() {
        return annotation;
    }

    public boolean isFavorite() {
        return favorite.get();
    }

    public void setFavorite(boolean value) {
        favorite.set(value);
    }

    public BooleanProperty favoriteProperty() {
        return favorite;
    }

    public String getChecksum() {
        return checksum.get();
    }

    public void setChecksum(String value) {
        checksum.set(value);
    }

    public StringProperty checksumProperty() {
        return checksum;
    }

    public String getImportedAt() {
        return importedAt.get();
    }

    public void setImportedAt(String value) {
        importedAt.set(value);
    }

    public StringProperty importedAtProperty() {
        return importedAt;
    }

    public boolean hasAnnotation() {
        return getAnnotation() != null && !getAnnotation().isBlank();
    }

    @Override
    public String toString() {
        return getName();
    }
}

package com.photofusionfx.model;

import java.io.File;
import java.time.Instant;

public class AssetItem {
    private final File file;
    private final String name;
    private final long sizeBytes;
    private final Instant modifiedAt;

    public AssetItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.sizeBytes = file.length();
        this.modifiedAt = Instant.ofEpochMilli(file.lastModified());
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    @Override
    public String toString() {
        return name;
    }
}

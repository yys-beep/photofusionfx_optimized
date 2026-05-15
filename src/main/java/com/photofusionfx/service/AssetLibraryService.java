package com.photofusionfx.service;

import com.photofusionfx.model.AssetItem;
import com.photofusionfx.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class AssetLibraryService {
    public List<AssetItem> listAssets() throws IOException {
        AppPaths.ensureDirectories();
        try (Stream<Path> stream = Files.walk(AppPaths.ASSET_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(FileUtils::isSupportedMediaFile)
                    .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
                    .map(path -> new AssetItem(path.toFile()))
                    .toList();
        }
    }

    public File importAsset(File source) throws IOException {
        return copyAsset(source, source == null ? "asset" : source.getName(), false);
    }

    public File saveExtractedObject(File source, String suggestedName) throws IOException {
        return copyAsset(source, suggestedName, true);
    }

    public File copyAsset(File source, String suggestedName, boolean extracted) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Asset source file does not exist.");
        }
        AppPaths.ensureDirectories();
        Path targetDir = extracted ? AppPaths.EXTRACTED_OBJECT_DIR : AppPaths.ASSET_DIR;
        Files.createDirectories(targetDir);
        String extension = FileUtils.extensionOrDefault(source.getName(), ".png");
        String base = FileUtils.slugify(FileUtils.baseName(suggestedName == null ? source.getName() : suggestedName));
        String timestamp = LocalDateTime.now().toString().replace(':', '-');
        Path target = targetDir.resolve(timestamp + "_" + base + "_" + UUID.randomUUID() + extension);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toFile();
    }

    public File stageClipboardImage(java.awt.image.BufferedImage image, String suggestedName) throws IOException {
        AppPaths.ensureDirectories();
        String base = FileUtils.slugify(suggestedName == null || suggestedName.isBlank() ? "clipboard-object" : suggestedName);
        Path target = AppPaths.CLIPBOARD_STAGING_DIR.resolve(base + "-" + UUID.randomUUID() + ".png");
        com.photofusionfx.util.ImageUtils.write(image, target.toFile());
        return target.toFile();
    }
}

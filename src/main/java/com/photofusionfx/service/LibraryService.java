package com.photofusionfx.service;

import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.util.FileUtils;
import com.photofusionfx.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LibraryService {
    private final DatabaseManager databaseManager;

    public LibraryService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<PhotoItem> loadAllPhotos() throws SQLException {
        String sql = "SELECT id, file_path, name, annotation, favorite, checksum, imported_at FROM photos ORDER BY imported_at DESC, id DESC";
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<PhotoItem> photos = new ArrayList<>();
            while (rs.next()) {
                photos.add(mapPhoto(rs));
            }
            return photos;
        }
    }

    public List<PhotoItem> importFiles(List<File> files) throws IOException, SQLException {
        List<PhotoItem> imported = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            Path source = file.toPath();
            if (!FileUtils.isImageFile(source)) {
                continue;
            }
            imported.add(importSingleImage(source));
        }
        return imported;
    }

    public List<PhotoItem> importDirectory(File directory) throws IOException, SQLException {
        List<Path> paths = FileUtils.collectImageFiles(directory.toPath());
        List<PhotoItem> imported = new ArrayList<>();
        for (Path path : paths) {
            imported.add(importSingleImage(path));
        }
        return imported;
    }

    public PhotoItem saveGeneratedImageToLibrary(BufferedImage image, String suggestedName) throws IOException, SQLException {
        String baseName = FileUtils.slugify(suggestedName == null || suggestedName.isBlank() ? "generated-image" : suggestedName);
        String filename = LocalDateTime.now().toString().replace(':', '-') + "_" + baseName + ".png";
        Path output = AppPaths.LIBRARY_DIR.resolve(filename);
        ImageUtils.write(image, output.toFile());
        return importSingleImage(output, baseName + ".png", true);
    }

    public void updateAnnotation(PhotoItem photoItem) throws SQLException {
        String sql = "UPDATE photos SET annotation = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, photoItem.getAnnotation());
            statement.setLong(2, photoItem.getId());
            statement.executeUpdate();
        }
    }

    public void updateFavorite(PhotoItem photoItem) throws SQLException {
        String sql = "UPDATE photos SET favorite = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, photoItem.isFavorite() ? 1 : 0);
            statement.setLong(2, photoItem.getId());
            statement.executeUpdate();
        }
    }

    public void deletePhoto(PhotoItem photoItem) throws SQLException, IOException {
        String sql = "DELETE FROM photos WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, photoItem.getId());
            statement.executeUpdate();
        }
        Path path = Path.of(photoItem.getFilePath());
        if (Files.exists(path) && path.startsWith(AppPaths.LIBRARY_DIR)) {
            Files.deleteIfExists(path);
        }
    }

    private PhotoItem importSingleImage(Path source) throws IOException, SQLException {
        return importSingleImage(source, source.getFileName().toString(), false);
    }

    private PhotoItem importSingleImage(Path source, String displayName, boolean alreadyInManagedLibrary) throws IOException, SQLException {
        String checksum = FileUtils.sha256(source);
        Optional<PhotoItem> existing = findByChecksum(checksum);
        if (existing.isPresent()) {
            if (!alreadyInManagedLibrary) {
                return existing.get();
            }
        }

        Path managedPath = source;
        if (!alreadyInManagedLibrary) {
            String extension = FileUtils.extensionOrDefault(source.getFileName().toString(), ".png");
            String filename = LocalDateTime.now().toString().replace(':', '-') + "_" + UUID.randomUUID() + extension;
            managedPath = AppPaths.LIBRARY_DIR.resolve(filename);
            Files.copy(source, managedPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return insertPhoto(managedPath, displayName, checksum);
    }

    private PhotoItem insertPhoto(Path managedPath, String displayName, String checksum) throws SQLException {
        String sql = "INSERT INTO photos(file_path, name, annotation, favorite, checksum, imported_at) VALUES(?, ?, '', 0, ?, ?)";
        String importedAt = LocalDateTime.now().toString();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, managedPath.toAbsolutePath().toString());
            statement.setString(2, displayName);
            statement.setString(3, checksum);
            statement.setString(4, importedAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1L;
                return new PhotoItem(id,
                        managedPath.toAbsolutePath().toString(),
                        displayName,
                        "",
                        false,
                        checksum,
                        importedAt);
            }
        }
    }

    private Optional<PhotoItem> findByChecksum(String checksum) throws SQLException {
        String sql = "SELECT id, file_path, name, annotation, favorite, checksum, imported_at FROM photos WHERE checksum = ? ORDER BY id DESC LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checksum);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPhoto(rs));
                }
                return Optional.empty();
            }
        }
    }

    private PhotoItem mapPhoto(ResultSet rs) throws SQLException {
        return new PhotoItem(
                rs.getLong("id"),
                rs.getString("file_path"),
                rs.getString("name"),
                rs.getString("annotation"),
                rs.getInt("favorite") == 1,
                rs.getString("checksum"),
                rs.getString("imported_at")
        );
    }
}

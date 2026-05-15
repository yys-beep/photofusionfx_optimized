package com.photofusionfx.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final String jdbcUrl;

    public DatabaseManager() throws IOException, SQLException {
        AppPaths.ensureDirectories();
        this.jdbcUrl = "jdbc:sqlite:" + AppPaths.DB_PATH.toAbsolutePath();
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_path TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL,
                        annotation TEXT DEFAULT '',
                        favorite INTEGER DEFAULT 0,
                        checksum TEXT,
                        imported_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_photos_checksum ON photos(checksum)");
        }
    }
}

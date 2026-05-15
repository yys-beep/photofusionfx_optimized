package com.photofusionfx.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    public static final Path APP_HOME = Path.of(System.getProperty("user.home"), ".photofusionfx");
    public static final Path LIBRARY_DIR = APP_HOME.resolve("library");
    public static final Path EXPORT_DIR = APP_HOME.resolve("exports");
    public static final Path PROJECT_DIR = APP_HOME.resolve("projects");
    public static final Path ASSET_DIR = APP_HOME.resolve("asset-library");
    public static final Path EXTRACTED_OBJECT_DIR = ASSET_DIR.resolve("extracted-objects");
    public static final Path CLIPBOARD_STAGING_DIR = APP_HOME.resolve("clipboard-staging");
    public static final Path DB_PATH = APP_HOME.resolve("photofusionfx.db");
    public static final Path MAIL_CONFIG_PATH = APP_HOME.resolve("mail.properties");

    private AppPaths() {
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(APP_HOME);
        Files.createDirectories(LIBRARY_DIR);
        Files.createDirectories(EXPORT_DIR);
        Files.createDirectories(PROJECT_DIR);
        Files.createDirectories(ASSET_DIR);
        Files.createDirectories(EXTRACTED_OBJECT_DIR);
        Files.createDirectories(CLIPBOARD_STAGING_DIR);
    }
}

package com.photofusionfx.model;

import javafx.scene.paint.Color;

import java.util.UUID;

public class ProjectLayer {
    private final String id;
    private String name;
    private LayerType type;
    private boolean visible = true;
    private double x;
    private double y;
    private double width;
    private double height;
    private double opacity = 1.0;
    private String text = "Text";
    private String fontFamily = "SansSerif";
    private double fontSize = 48.0;
    private Color fillColor = Color.WHITE;
    private Color strokeColor = Color.BLACK;
    private double strokeWidth = 0.0;
    private String sourcePath;

    public ProjectLayer(LayerType type) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.name = defaultName(type);
    }

    public static ProjectLayer text(String value, String fontFamily, double fontSize, Color color, double x, double y) {
        ProjectLayer layer = new ProjectLayer(LayerType.TEXT);
        layer.setText(value == null || value.isBlank() ? "Text" : value);
        layer.setFontFamily(fontFamily == null || fontFamily.isBlank() ? "SansSerif" : fontFamily);
        layer.setFontSize(fontSize);
        layer.setFillColor(color == null ? Color.WHITE : color);
        layer.setX(x);
        layer.setY(y);
        layer.setWidth(Math.max(120, layer.getText().length() * fontSize * 0.55));
        layer.setHeight(fontSize * 1.35);
        layer.setName("Text: " + trimName(layer.getText()));
        return layer;
    }

    public static ProjectLayer image(String sourcePath, double x, double y, double width, double height) {
        ProjectLayer layer = new ProjectLayer(LayerType.IMAGE);
        layer.setSourcePath(sourcePath);
        layer.setX(x);
        layer.setY(y);
        layer.setWidth(width);
        layer.setHeight(height);
        layer.setName("Image Layer");
        return layer;
    }

    public static ProjectLayer shape(LayerType type, double x, double y, double width, double height, Color fill, Color stroke, double strokeWidth) {
        if (type != LayerType.RECTANGLE && type != LayerType.ELLIPSE) {
            throw new IllegalArgumentException("Shape layer type must be RECTANGLE or ELLIPSE.");
        }
        ProjectLayer layer = new ProjectLayer(type);
        layer.setX(x);
        layer.setY(y);
        layer.setWidth(width);
        layer.setHeight(height);
        layer.setFillColor(fill == null ? Color.rgb(255, 255, 255, 0.35) : fill);
        layer.setStrokeColor(stroke == null ? Color.WHITE : stroke);
        layer.setStrokeWidth(strokeWidth);
        return layer;
    }

    public ProjectLayer copy() {
        ProjectLayer layer = new ProjectLayer(type);
        layer.name = name;
        layer.visible = visible;
        layer.x = x;
        layer.y = y;
        layer.width = width;
        layer.height = height;
        layer.opacity = opacity;
        layer.text = text;
        layer.fontFamily = fontFamily;
        layer.fontSize = fontSize;
        layer.fillColor = fillColor;
        layer.strokeColor = strokeColor;
        layer.strokeWidth = strokeWidth;
        layer.sourcePath = sourcePath;
        return layer;
    }

    private static String defaultName(LayerType type) {
        return switch (type) {
            case TEXT -> "Text Layer";
            case IMAGE -> "Image Layer";
            case RECTANGLE -> "Rectangle Layer";
            case ELLIPSE -> "Ellipse Layer";
        };
    }

    private static String trimName(String text) {
        String stripped = text == null ? "Text" : text.strip();
        return stripped.length() <= 24 ? stripped : stripped.substring(0, 24) + "…";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? defaultName(type) : name;
    }

    public LayerType getType() {
        return type;
    }

    public void setType(LayerType type) {
        this.type = type == null ? LayerType.TEXT : type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(1, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(1, height);
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        if (type == LayerType.TEXT) {
            this.name = "Text: " + trimName(this.text);
        }
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily == null || fontFamily.isBlank() ? "SansSerif" : fontFamily;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = Math.max(1.0, fontSize);
        if (type == LayerType.TEXT) {
            setHeight(this.fontSize * 1.35);
            setWidth(Math.max(getWidth(), Math.max(120, getText().length() * this.fontSize * 0.55)));
        }
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor == null ? Color.WHITE : fillColor;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor == null ? Color.BLACK : strokeColor;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(double strokeWidth) {
        this.strokeWidth = Math.max(0.0, strokeWidth);
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public String toString() {
        return (visible ? "☑ " : "☐ ") + name;
    }
}

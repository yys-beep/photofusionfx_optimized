package com.photofusionfx.model;

import javafx.scene.paint.Color;

public class EditParameters {
    private double brightness = 0.0;
    private double contrast = 1.0;
    private double saturation = 1.0;
    private boolean grayscale = false;
    private boolean autoEnhance = false;
    private Color tintColor = Color.TRANSPARENT;
    private double tintStrength = 0.0;
    private int borderWidth = 0;
    private Color borderColor = Color.WHITE;
    private double scale = 1.0;
    private double rotationDegrees = 0.0;
    private double translateX = 0.0;
    private double translateY = 0.0;

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = brightness;
    }

    public double getContrast() {
        return contrast;
    }

    public void setContrast(double contrast) {
        this.contrast = contrast;
    }

    public double getSaturation() {
        return saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = Math.max(0.0, saturation);
    }

    public boolean isGrayscale() {
        return grayscale;
    }

    public void setGrayscale(boolean grayscale) {
        this.grayscale = grayscale;
    }

    public boolean isAutoEnhance() {
        return autoEnhance;
    }

    public void setAutoEnhance(boolean autoEnhance) {
        this.autoEnhance = autoEnhance;
    }

    public Color getTintColor() {
        return tintColor;
    }

    public void setTintColor(Color tintColor) {
        this.tintColor = tintColor == null ? Color.TRANSPARENT : tintColor;
    }

    public double getTintStrength() {
        return tintStrength;
    }

    public void setTintStrength(double tintStrength) {
        this.tintStrength = Math.max(0.0, Math.min(1.0, tintStrength));
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(int borderWidth) {
        this.borderWidth = Math.max(0, borderWidth);
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor == null ? Color.WHITE : borderColor;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = Math.max(0.01, scale);
    }

    public double getRotationDegrees() {
        return rotationDegrees;
    }

    public void setRotationDegrees(double rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
    }

    public double getTranslateX() {
        return translateX;
    }

    public void setTranslateX(double translateX) {
        this.translateX = translateX;
    }

    public double getTranslateY() {
        return translateY;
    }

    public void setTranslateY(double translateY) {
        this.translateY = translateY;
    }

    public void reset() {
        brightness = 0.0;
        contrast = 1.0;
        saturation = 1.0;
        grayscale = false;
        autoEnhance = false;
        tintColor = Color.TRANSPARENT;
        tintStrength = 0.0;
        borderWidth = 0;
        borderColor = Color.WHITE;
        scale = 1.0;
        rotationDegrees = 0.0;
        translateX = 0.0;
        translateY = 0.0;
    }
}

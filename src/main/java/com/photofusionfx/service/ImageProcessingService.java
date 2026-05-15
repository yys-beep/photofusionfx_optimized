package com.photofusionfx.service;

import com.photofusionfx.model.EditParameters;
import com.photofusionfx.util.ImageUtils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ImageProcessingService {
    public BufferedImage applyParameters(BufferedImage source, EditParameters parameters) {
        BufferedImage working = ImageUtils.deepCopyToArgb(source);
        if (parameters.isAutoEnhance()) {
            working = autoEnhance(working);
        }
        working = adjustBrightnessAndContrast(working, parameters.getBrightness(), parameters.getContrast());
        if (Math.abs(parameters.getSaturation() - 1.0) > 0.001) {
            working = adjustSaturation(working, parameters.getSaturation());
        }
        if (parameters.getTintStrength() > 0.001 && parameters.getTintColor().getOpacity() > 0.001) {
            working = applyTint(working, ImageUtils.toAwtColor(parameters.getTintColor()), parameters.getTintStrength());
        }
        if (parameters.isGrayscale()) {
            working = toGrayscale(working);
        }
        if (parameters.getBorderWidth() > 0) {
            working = addBorder(working, parameters.getBorderWidth(), ImageUtils.toAwtColor(parameters.getBorderColor()));
        }
        working = transform(working,
                parameters.getScale(),
                parameters.getRotationDegrees(),
                parameters.getTranslateX(),
                parameters.getTranslateY());
        return working;
    }

    public BufferedImage adjustBrightnessAndContrast(BufferedImage source, double brightness, double contrast) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;

                red = ImageUtils.clamp((int) Math.round((red - 128) * contrast + 128 + brightness));
                green = ImageUtils.clamp((int) Math.round((green - 128) * contrast + 128 + brightness));
                blue = ImageUtils.clamp((int) Math.round((blue - 128) * contrast + 128 + brightness));
                output.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return output;
    }

    public BufferedImage adjustSaturation(BufferedImage source, double saturation) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                double gray = 0.299 * red + 0.587 * green + 0.114 * blue;
                red = ImageUtils.clamp((int) Math.round(gray + (red - gray) * saturation));
                green = ImageUtils.clamp((int) Math.round(gray + (green - gray) * saturation));
                blue = ImageUtils.clamp((int) Math.round(gray + (blue - gray) * saturation));
                output.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return output;
    }

    public BufferedImage applyTint(BufferedImage source, java.awt.Color tint, double strength) {
        double s = Math.max(0, Math.min(1, strength));
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                red = ImageUtils.clamp((int) Math.round(red * (1 - s) + tint.getRed() * s));
                green = ImageUtils.clamp((int) Math.round(green * (1 - s) + tint.getGreen() * s));
                blue = ImageUtils.clamp((int) Math.round(blue * (1 - s) + tint.getBlue() * s));
                output.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return output;
    }

    public BufferedImage autoEnhance(BufferedImage source) {
        int min = 255;
        int max = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int lum = (int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue);
                min = Math.min(min, lum);
                max = Math.max(max, lum);
            }
        }
        if (max <= min + 10) {
            return ImageUtils.deepCopyToArgb(source);
        }
        double scale = 255.0 / (max - min);
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                red = ImageUtils.clamp((int) Math.round((red - min) * scale));
                green = ImageUtils.clamp((int) Math.round((green - min) * scale));
                blue = ImageUtils.clamp((int) Math.round((blue - min) * scale));
                output.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return adjustSaturation(output, 1.08);
    }

    public BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int gray = ImageUtils.clamp((int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue));
                output.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return output;
    }

    public BufferedImage addBorder(BufferedImage source, int width, java.awt.Color color) {
        int newWidth = source.getWidth() + width * 2;
        int newHeight = source.getHeight() + width * 2;
        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        ImageUtils.applyQualityHints(g);
        g.setColor(color);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(source, width, width, null);
        g.dispose();
        return output;
    }

    public BufferedImage transform(BufferedImage source, double scale, double rotationDegrees, double translateX, double translateY) {
        scale = Math.max(0.01, scale);
        double scaledWidth = source.getWidth() * scale;
        double scaledHeight = source.getHeight() * scale;
        double diagonal = Math.ceil(Math.hypot(scaledWidth, scaledHeight));
        int canvasWidth = Math.max(1, (int) Math.ceil(diagonal + Math.abs(translateX) * 2 + 80));
        int canvasHeight = Math.max(1, (int) Math.ceil(diagonal + Math.abs(translateY) * 2 + 80));

        BufferedImage output = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        ImageUtils.applyQualityHints(g);
        g.translate(canvasWidth / 2.0 + translateX, canvasHeight / 2.0 + translateY);
        g.rotate(Math.toRadians(rotationDegrees));
        g.scale(scale, scale);
        g.drawImage(source, -source.getWidth() / 2, -source.getHeight() / 2, null);
        g.dispose();
        return output;
    }
}

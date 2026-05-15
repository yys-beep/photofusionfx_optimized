package com.photofusionfx.service;

import com.photofusionfx.util.ImageUtils;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Queue;

public class ObjectExtractionService {
    private static final int[][] NEIGHBOURS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},            {1, 0},
            {-1, 1},  {0, 1},  {1, 1}
    };

    public BufferedImage extractByColorSimilarity(BufferedImage source, int seedX, int seedY, double threshold) {
        boolean[] mask = maskByColorSimilarity(source, seedX, seedY, threshold);
        return extractByMask(source, mask, source.getWidth(), source.getHeight(), true);
    }

    public boolean[] maskByColorSimilarity(BufferedImage source, int seedX, int seedY, double threshold) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (seedX < 0 || seedY < 0 || seedX >= width || seedY >= height) {
            throw new IllegalArgumentException("Seed point is outside the image.");
        }

        int total = width * height;
        boolean[] visited = new boolean[total];
        boolean[] mask = new boolean[total];
        float[] seedHsv = hsv(source.getRGB(seedX, seedY));
        Queue<Point> queue = new ArrayDeque<>();
        queue.add(new Point(seedX, seedY));
        visited[index(seedX, seedY, width)] = true;

        while (!queue.isEmpty()) {
            Point point = queue.remove();
            int rgb = source.getRGB(point.x, point.y);
            if (!isSimilar(seedHsv, hsv(rgb), threshold)) {
                continue;
            }
            mask[index(point.x, point.y, width)] = true;
            for (int[] delta : NEIGHBOURS) {
                int nx = point.x + delta[0];
                int ny = point.y + delta[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                int idx = index(nx, ny, width);
                if (!visited[idx]) {
                    visited[idx] = true;
                    queue.add(new Point(nx, ny));
                }
            }
        }
        return mask;
    }

    public boolean[] lassoMask(int width, int height, double[] xPoints, double[] yPoints, int count) {
        boolean[] mask = new boolean[Math.max(0, width * height)];
        if (count < 3 || width <= 0 || height <= 0) {
            return mask;
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xPoints[0], yPoints[0]);
        for (int i = 1; i < count; i++) {
            path.lineTo(xPoints[i], yPoints[i]);
        }
        path.closePath();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (path.contains(x + 0.5, y + 0.5)) {
                    mask[index(x, y, width)] = true;
                }
            }
        }
        return mask;
    }

    public BufferedImage extractByMask(BufferedImage source, boolean[] mask, int maskWidth, int maskHeight, boolean trim) {
        if (source == null) {
            throw new IllegalArgumentException("Source image is required.");
        }
        if (mask == null || maskWidth <= 0 || maskHeight <= 0 || mask.length < maskWidth * maskHeight) {
            throw new IllegalArgumentException("Selection mask is empty or invalid.");
        }
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            int my = Math.min(maskHeight - 1, Math.max(0, (int) Math.floor(y * maskHeight / (double) source.getHeight())));
            for (int x = 0; x < source.getWidth(); x++) {
                int mx = Math.min(maskWidth - 1, Math.max(0, (int) Math.floor(x * maskWidth / (double) source.getWidth())));
                if (mask[index(mx, my, maskWidth)]) {
                    int rgb = source.getRGB(x, y) & 0x00FFFFFF;
                    int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;
                    output.setRGB(x, y, (alpha << 24) | rgb);
                } else {
                    output.setRGB(x, y, 0x00000000);
                }
            }
        }
        return trim ? trimTransparentBorder(output) : output;
    }

    public BufferedImage addOutlineToAlpha(BufferedImage source, javafx.scene.paint.Color outlineColor, int radius) {
        radius = Math.max(1, radius);
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int outline = ImageUtils.toAwtColor(outlineColor == null ? javafx.scene.paint.Color.WHITE : outlineColor).getRGB();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha > 0) {
                    output.setRGB(x, y, argb);
                    continue;
                }
                boolean nearObject = false;
                for (int dy = -radius; dy <= radius && !nearObject; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (dx * dx + dy * dy > radius * radius) {
                            continue;
                        }
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= source.getWidth() || ny >= source.getHeight()) {
                            continue;
                        }
                        if (((source.getRGB(nx, ny) >>> 24) & 0xFF) > 0) {
                            nearObject = true;
                            break;
                        }
                    }
                }
                if (nearObject) {
                    output.setRGB(x, y, outline);
                }
            }
        }
        return output;
    }

    public BufferedImage enhanceTransparentObject(BufferedImage source) {
        ImageProcessingService processor = new ImageProcessingService();
        BufferedImage enhanced = processor.autoEnhance(source);
        enhanced = processor.adjustBrightnessAndContrast(enhanced, 6, 1.08);
        return processor.adjustSaturation(enhanced, 1.10);
    }

    public BufferedImage tintTransparentObject(BufferedImage source, javafx.scene.paint.Color color, double strength) {
        ImageProcessingService processor = new ImageProcessingService();
        return processor.applyTint(source, ImageUtils.toAwtColor(color == null ? javafx.scene.paint.Color.TRANSPARENT : color), strength);
    }

    public BufferedImage trimTransparentBorder(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return ImageUtils.deepCopyToArgb(source);
        }
        BufferedImage cropped = source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        return ImageUtils.deepCopyToArgb(cropped);
    }

    private float[] hsv(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return Color.RGBtoHSB(red, green, blue, null);
    }

    private boolean isSimilar(float[] base, float[] candidate, double threshold) {
        double hueDiff = Math.abs(base[0] - candidate[0]);
        hueDiff = Math.min(hueDiff, 1.0 - hueDiff) * 2.0;
        double satDiff = Math.abs(base[1] - candidate[1]);
        double briDiff = Math.abs(base[2] - candidate[2]);
        double distance = hueDiff * 0.60 + satDiff * 0.25 + briDiff * 0.15;
        return distance <= threshold;
    }

    private int index(int x, int y, int width) {
        return y * width + x;
    }
}

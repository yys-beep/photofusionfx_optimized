// Module 3: Image mosaic generation and anti-repeat scoring
package com.photofusionfx.service;

import com.photofusionfx.util.ImageUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MosaicService {
    public BufferedImage generateMosaic(BufferedImage target,
                                        List<BufferedImage> sourceImages,
                                        int columns,
                                        int tileSize,
                                        Consumer<Double> progressConsumer) {
        if (sourceImages == null || sourceImages.isEmpty()) {
            throw new IllegalArgumentException("At least one source image is required to build a mosaic.");
        }
        columns = Math.max(5, columns);
        tileSize = Math.max(8, tileSize);

        int rows = Math.max(1, (int) Math.round((double) target.getHeight() / target.getWidth() * columns));
        BufferedImage reducedTarget = ImageUtils.resize(target, columns, rows);

        List<TileInfo> tiles = new ArrayList<>();
        for (BufferedImage source : sourceImages) {
            BufferedImage tile = ImageUtils.cropCenterSquareAndResize(source, tileSize);
            int[] avg = averageRgb(tile);
            tiles.add(new TileInfo(tile, avg[0], avg[1], avg[2]));
        }

        BufferedImage output = new BufferedImage(columns * tileSize, rows * tileSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        ImageUtils.applyQualityHints(g);
        int total = rows * columns;
        int done = 0;
        TileInfo[][] placedTiles = new TileInfo[rows][columns];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int rgb = reducedTarget.getRGB(x, y);
                int targetRed = (rgb >>> 16) & 0xFF;
                int targetGreen = (rgb >>> 8) & 0xFF;
                int targetBlue = rgb & 0xFF;
                TileInfo best = chooseBestTile(tiles, placedTiles, x, y, targetRed, targetGreen, targetBlue);
                best.usageCount++;
                placedTiles[y][x] = best;
                BufferedImage balancedTile = colorBalanceTile(best.tile, best, targetRed, targetGreen, targetBlue);
                g.drawImage(balancedTile, x * tileSize, y * tileSize, null);
                done++;
                if (progressConsumer != null) {
                    progressConsumer.accept(done / (double) total);
                }
            }
        }
        g.dispose();
        return output;
    }

    private TileInfo chooseBestTile(List<TileInfo> tiles,
                                    TileInfo[][] placedTiles,
                                    int x,
                                    int y,
                                    int red,
                                    int green,
                                    int blue) {
        TileInfo best = tiles.getFirst();
        double bestScore = Double.MAX_VALUE;
        for (TileInfo tile : tiles) {
            double colorDistance = Math.sqrt(
                    squared(tile.red - red) +
                    squared(tile.green - green) +
                    squared(tile.blue - blue)
            );
            double luminanceDistance = Math.abs(luminance(tile.red, tile.green, tile.blue) - luminance(red, green, blue));
            double distance = colorDistance + luminanceDistance * 0.65;
            distance += tile.usageCount * 18.0;
            if (isSameAsNeighbour(tile, placedTiles, x, y)) {
                distance += 90.0;
            }
            if (distance < bestScore) {
                bestScore = distance;
                best = tile;
            }
        }
        return best;
    }

    private BufferedImage colorBalanceTile(BufferedImage tile, TileInfo info, int targetRed, int targetGreen, int targetBlue) {
        BufferedImage output = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_INT_RGB);
        double mix = 0.34;
        double redShift = (targetRed - info.red) * 0.42;
        double greenShift = (targetGreen - info.green) * 0.42;
        double blueShift = (targetBlue - info.blue) * 0.42;
        Color targetColor = new Color(targetRed, targetGreen, targetBlue);

        for (int y = 0; y < tile.getHeight(); y++) {
            for (int x = 0; x < tile.getWidth(); x++) {
                int rgb = tile.getRGB(x, y);
                int red = (rgb >>> 16) & 0xFF;
                int green = (rgb >>> 8) & 0xFF;
                int blue = rgb & 0xFF;

                red = clamp((int) Math.round(red + redShift));
                green = clamp((int) Math.round(green + greenShift));
                blue = clamp((int) Math.round(blue + blueShift));

                red = clamp((int) Math.round(red * (1.0 - mix) + targetColor.getRed() * mix));
                green = clamp((int) Math.round(green * (1.0 - mix) + targetColor.getGreen() * mix));
                blue = clamp((int) Math.round(blue * (1.0 - mix) + targetColor.getBlue() * mix));
                output.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return output;
    }

    private boolean isSameAsNeighbour(TileInfo tile, TileInfo[][] placedTiles, int x, int y) {
        return (x > 0 && placedTiles[y][x - 1] == tile)
                || (y > 0 && placedTiles[y - 1][x] == tile);
    }

    private double squared(double value) {
        return value * value;
    }

    private double luminance(int red, int green, int blue) {
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int[] averageRgb(BufferedImage image) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long total = (long) image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                red += (rgb >>> 16) & 0xFF;
                green += (rgb >>> 8) & 0xFF;
                blue += rgb & 0xFF;
            }
        }
        return new int[]{
                (int) (red / total),
                (int) (green / total),
                (int) (blue / total)
        };
    }

    private static final class TileInfo {
        private final BufferedImage tile;
        private final int red;
        private final int green;
        private final int blue;
        private int usageCount;

        private TileInfo(BufferedImage tile, int red, int green, int blue) {
            this.tile = tile;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}

package com.photofusionfx.service;

import com.photofusionfx.util.ImageUtils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class MosaicService {

    public enum PlacementMode {
        TARGET_MATCH_AVG_RGB,
        TARGET_MATCH_AVG_RGB_ANTI_REPEAT,
        RANDOM_FROM_COLLECTION
    }

    /**
     * Backwards-compatible method (keeps old callers working).
     */
    public BufferedImage generateMosaic(BufferedImage target,
                                        List<BufferedImage> sourceImages,
                                        int columns,
                                        int tileSize,
                                        Consumer<Double> progressConsumer) {
        return generateMosaic(
                target,
                sourceImages,
                columns,
                tileSize,
                true,
                999999,
                1,
                90.0,
                PlacementMode.TARGET_MATCH_AVG_RGB_ANTI_REPEAT,
                progressConsumer
        );
    }

    /**
     * New configurable mosaic generator.
     */
    public BufferedImage generateMosaic(BufferedImage target,
                                        List<BufferedImage> sourceImages,
                                        int columns,
                                        int tileSize,
                                        boolean colorBalanceEnabled,
                                        int maxUsagePerTile,
                                        int neighborRadius,
                                        double neighborPenalty,
                                        PlacementMode placementMode,
                                        Consumer<Double> progressConsumer) {

        if (target == null) {
            throw new IllegalArgumentException("Target image is required.");
        }
        if (sourceImages == null || sourceImages.isEmpty()) {
            throw new IllegalArgumentException("At least one source image is required to build a mosaic.");
        }

        columns = Math.max(5, columns);
        tileSize = Math.max(8, tileSize);
        maxUsagePerTile = Math.max(1, maxUsagePerTile);
        neighborRadius = Math.max(1, neighborRadius);
        neighborPenalty = Math.max(0.0, neighborPenalty);
        if (placementMode == null) {
            placementMode = PlacementMode.TARGET_MATCH_AVG_RGB_ANTI_REPEAT;
        }

        int rows = Math.max(1, (int) Math.round((double) target.getHeight() / target.getWidth() * columns));
        BufferedImage reducedTarget = ImageUtils.resize(target, columns, rows);

        List<TileInfo> tiles = new ArrayList<>();
        for (BufferedImage source : sourceImages) {
            if (source == null) continue;
            BufferedImage tile = ImageUtils.cropCenterSquareAndResize(source, tileSize);
            int[] avg = averageRgb(tile);
            tiles.add(new TileInfo(tile, avg[0], avg[1], avg[2]));
        }
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("No usable source images could be processed into tiles.");
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

                TileInfo best = chooseTile(
                        tiles,
                        placedTiles,
                        x,
                        y,
                        targetRed,
                        targetGreen,
                        targetBlue,
                        maxUsagePerTile,
                        neighborRadius,
                        neighborPenalty,
                        placementMode
                );

                best.usageCount++;
                placedTiles[y][x] = best;

                BufferedImage toDraw = best.tile;
                if (colorBalanceEnabled) {
                    toDraw = colorBalanceTile(best.tile, best, targetRed, targetGreen, targetBlue);
                }
                g.drawImage(toDraw, x * tileSize, y * tileSize, null);

                done++;
                if (progressConsumer != null) {
                    progressConsumer.accept(done / (double) total);
                }
            }
        }

        g.dispose();
        return output;
    }

    private TileInfo chooseTile(List<TileInfo> tiles,
                                TileInfo[][] placedTiles,
                                int x,
                                int y,
                                int red,
                                int green,
                                int blue,
                                int maxUsagePerTile,
                                int neighborRadius,
                                double neighborPenalty,
                                PlacementMode placementMode) {

        return switch (placementMode) {
            case RANDOM_FROM_COLLECTION -> chooseRandomTile(tiles, placedTiles, x, y, maxUsagePerTile, neighborRadius, neighborPenalty);
            case TARGET_MATCH_AVG_RGB -> chooseBestTileByColor(tiles, placedTiles, x, y, red, green, blue,
                    Integer.MAX_VALUE, 1, 0.0); // basically no constraints
            case TARGET_MATCH_AVG_RGB_ANTI_REPEAT -> chooseBestTileByColor(tiles, placedTiles, x, y, red, green, blue,
                    maxUsagePerTile, neighborRadius, neighborPenalty);
        };
    }

    private TileInfo chooseRandomTile(List<TileInfo> tiles,
                                     TileInfo[][] placedTiles,
                                     int x,
                                     int y,
                                     int maxUsagePerTile,
                                     int neighborRadius,
                                     double neighborPenalty) {

        // prefer tiles under cap; if none, allow overflow
        List<TileInfo> pool = new ArrayList<>();
        for (TileInfo t : tiles) {
            if (t.usageCount < maxUsagePerTile) pool.add(t);
        }
        if (pool.isEmpty()) pool = tiles;

        // Try a few random picks and take the one with fewer near matches (still "strategic" about repetition)
        TileInfo best = pool.getFirst();
        int bestNear = Integer.MAX_VALUE;

        int tries = Math.min(40, pool.size());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < tries; i++) {
            TileInfo candidate = pool.get(rnd.nextInt(pool.size()));
            int near = countNearMatches(candidate, placedTiles, x, y, neighborRadius);
            if (near < bestNear) {
                bestNear = near;
                best = candidate;
                if (bestNear == 0) break;
            }
        }

        return best;
    }

    private TileInfo chooseBestTileByColor(List<TileInfo> tiles,
                                          TileInfo[][] placedTiles,
                                          int x,
                                          int y,
                                          int red,
                                          int green,
                                          int blue,
                                          int maxUsagePerTile,
                                          int neighborRadius,
                                          double neighborPenalty) {

        TileInfo best = tiles.getFirst();
        double bestScore = Double.MAX_VALUE;

        boolean anyBelowCap = false;
        for (TileInfo t : tiles) {
            if (t.usageCount < maxUsagePerTile) {
                anyBelowCap = true;
                break;
            }
        }

        for (TileInfo tile : tiles) {
            if (anyBelowCap && tile.usageCount >= maxUsagePerTile) continue;

            double colorDistance = Math.sqrt(
                    squared(tile.red - red) +
                            squared(tile.green - green) +
                            squared(tile.blue - blue)
            );

            double luminanceDistance = Math.abs(luminance(tile.red, tile.green, tile.blue) - luminance(red, green, blue));
            double score = colorDistance + luminanceDistance * 0.65;

            // usage penalty (soft)
            score += tile.usageCount * 18.0;

            // neighbor penalty (radius window)
            int nearCount = countNearMatches(tile, placedTiles, x, y, neighborRadius);
            if (nearCount > 0) {
                score += neighborPenalty * nearCount;
            }

            if (score < bestScore) {
                bestScore = score;
                best = tile;
            }
        }

        return best;
    }

    private int countNearMatches(TileInfo tile, TileInfo[][] placedTiles, int x, int y, int radius) {
        int count = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dy == 0) continue;
                int yy = y + dy;
                int xx = x + dx;
                if (yy < 0 || yy >= placedTiles.length) continue;
                if (xx < 0 || xx >= placedTiles[0].length) continue;
                if (placedTiles[yy][xx] == tile) count++;
            }
        }
        return count;
    }

    private BufferedImage colorBalanceTile(BufferedImage tile, TileInfo info, int targetRed, int targetGreen, int targetBlue) {
        BufferedImage output = new BufferedImage(tile.getWidth(), tile.getHeight(), BufferedImage.TYPE_INT_RGB);
        double mix = 0.34;
        double redShift = (targetRed - info.red) * 0.42;
        double greenShift = (targetGreen - info.green) * 0.42;
        double blueShift = (targetBlue - info.blue) * 0.42;

        for (int y = 0; y < tile.getHeight(); y++) {
            for (int x = 0; x < tile.getWidth(); x++) {
                int rgb = tile.getRGB(x, y);
                int red = (rgb >>> 16) & 0xFF;
                int green = (rgb >>> 8) & 0xFF;
                int blue = rgb & 0xFF;

                red = clamp((int) Math.round(red + redShift));
                green = clamp((int) Math.round(green + greenShift));
                blue = clamp((int) Math.round(blue + blueShift));

                red = clamp((int) Math.round(red * (1.0 - mix) + targetRed * mix));
                green = clamp((int) Math.round(green * (1.0 - mix) + targetGreen * mix));
                blue = clamp((int) Math.round(blue * (1.0 - mix) + targetBlue * mix));

                output.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return output;
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
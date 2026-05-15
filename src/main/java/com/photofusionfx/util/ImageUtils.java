package com.photofusionfx.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static BufferedImage deepCopyToArgb(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        applyQualityHints(g);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        applyQualityHints(g);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    public static BufferedImage resizeToFit(BufferedImage source, int maxSide) {
        if (source.getWidth() <= maxSide && source.getHeight() <= maxSide) {
            return deepCopyToArgb(source);
        }
        double scale = Math.min((double) maxSide / source.getWidth(), (double) maxSide / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return resize(source, width, height);
    }

    public static BufferedImage cropCenterSquareAndResize(BufferedImage source, int size) {
        int w = source.getWidth();
        int h = source.getHeight();
        int edge = Math.min(w, h);
        int x = (w - edge) / 2;
        int y = (h - edge) / 2;
        BufferedImage cropped = source.getSubimage(x, y, edge, edge);
        return resize(deepCopyToArgb(cropped), size, size);
    }

    public static javafx.scene.image.Image toFxImage(BufferedImage image) {
        return SwingFXUtils.toFXImage(image, null);
    }

    public static BufferedImage fromFxImage(Image image) {
        return SwingFXUtils.fromFXImage(image, null);
    }

    public static BufferedImage read(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unsupported image file: " + file.getAbsolutePath());
        }
        return deepCopyToArgb(image);
    }

    public static void write(BufferedImage image, File file) throws IOException {
        String extension = FileUtils.extensionOrDefault(file.getName(), ".png").replace(".", "").toLowerCase(Locale.ROOT);
        if (extension.equals("jpg") || extension.equals("jpeg")) {
            writeJpeg(flattenOnWhite(image), file);
            return;
        }
        if (!ImageIO.write(image, extension, file)) {
            throw new IOException("No writer available for format: " + extension);
        }
    }

    private static void writeJpeg(BufferedImage image, File file) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer not found.");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.95f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public static BufferedImage flattenOnWhite(BufferedImage image) {
        BufferedImage flattened = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flattened.createGraphics();
        applyQualityHints(g);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return flattened;
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    public static java.awt.Color toAwtColor(Color color) {
        return new java.awt.Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
    }

    public static double[] viewToImageCoordinates(ImageView imageView, MouseEvent event) {
        Image image = imageView.getImage();
        if (image == null) {
            return null;
        }
        double viewWidth = imageView.getBoundsInLocal().getWidth();
        double viewHeight = imageView.getBoundsInLocal().getHeight();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        double scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        double displayedWidth = imageWidth * scale;
        double displayedHeight = imageHeight * scale;
        double xOffset = (viewWidth - displayedWidth) / 2;
        double yOffset = (viewHeight - displayedHeight) / 2;

        double x = event.getX() - xOffset;
        double y = event.getY() - yOffset;
        if (x < 0 || y < 0 || x > displayedWidth || y > displayedHeight) {
            return null;
        }
        return new double[]{x / scale, y / scale};
    }

    public static BufferedImage ensureEvenDimensions(BufferedImage image) {
        int width = image.getWidth() % 2 == 0 ? image.getWidth() : image.getWidth() + 1;
        int height = image.getHeight() % 2 == 0 ? image.getHeight() : image.getHeight() + 1;
        if (width == image.getWidth() && height == image.getHeight()) {
            return image;
        }
        BufferedImage adjusted = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = adjusted.createGraphics();
        applyQualityHints(g);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return adjusted;
    }

    public static BufferedImage scaleWithAffine(BufferedImage source, double scale) {
        int newWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int newHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        AffineTransform tx = AffineTransform.getScaleInstance(scale, scale);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BICUBIC);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        op.filter(source, scaled);
        return scaled;
    }
}

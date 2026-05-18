package com.photofusionfx.service;

import com.photofusionfx.model.LayerType;
import com.photofusionfx.model.ProjectLayer;
import com.photofusionfx.util.ImageUtils;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class LayerRenderService {
    public BufferedImage renderComposite(BufferedImage base, List<ProjectLayer> layers) throws IOException {
        BufferedImage output = ImageUtils.deepCopyToArgb(base);
        Graphics2D g = output.createGraphics();
        ImageUtils.applyQualityHints(g);
        drawLayers(g, layers, 1.0, 1.0);
        g.dispose();
        return output;
    }

    public BufferedImage renderCompositeScaled(BufferedImage base, List<ProjectLayer> layers, int width, int height) throws IOException {
        width = Math.max(1, width);
        height = Math.max(1, height);
        double scaleX = width / (double) base.getWidth();
        double scaleY = height / (double) base.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        ImageUtils.applyQualityHints(g);
        g.drawImage(base, 0, 0, width, height, null);
        drawLayers(g, layers, scaleX, scaleY);
        g.dispose();
        return output;
    }

    public void drawLayers(Graphics2D g, List<ProjectLayer> layers, double scaleX, double scaleY) throws IOException {
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (ProjectLayer layer : layers) {
            drawLayer(g, layer, scaleX, scaleY);
        }
        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    public void drawLayer(Graphics2D g, ProjectLayer layer, double scaleX, double scaleY) throws IOException {
        if (layer == null || !layer.isVisible()) {
            return;
        }
        double x = layer.getX() * scaleX;
        double y = layer.getY() * scaleY;
        double w = Math.max(1, layer.getWidth() * scaleX);
        double h = Math.max(1, layer.getHeight() * scaleY);
        double fontScale = Math.min(scaleX, scaleY);

        var oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) layer.getOpacity()));
        if (layer.getType() == LayerType.TEXT) {
            g.setColor(toAwt(layer.getFillColor()));
            g.setFont(new Font(layer.getFontFamily(), Font.PLAIN, Math.max(1, (int) Math.round(layer.getFontSize() * fontScale))));
            if (layer.getStrokeWidth() > 0.0) {
                g.setColor(toAwt(layer.getStrokeColor()));
                float outline = (float) Math.max(1, layer.getStrokeWidth() * fontScale);
                g.setStroke(new BasicStroke(outline));
                // Simple shadow/outline approximation for readable exported text.
                int baseline = (int) Math.round(y + layer.getFontSize() * fontScale);
                String text = layer.getText() == null ? "" : layer.getText();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0) {
                            g.drawString(text, (float) (x + dx * outline), (float) (baseline + dy * outline));
                        }
                    }
                }
                g.setColor(toAwt(layer.getFillColor()));
                g.drawString(text, (float) x, (float) baseline);
            } else {
                g.drawString(layer.getText() == null ? "" : layer.getText(), (float) x, (float) (y + layer.getFontSize() * fontScale));
            }
        } else if (layer.getType() == LayerType.IMAGE) {
            if (layer.getSourcePath() != null && !layer.getSourcePath().isBlank()) {
                File file = new File(layer.getSourcePath());
                if (file.isFile()) {
                    try {
                        BufferedImage image = ImageUtils.read(file);
                        g.drawImage(image, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
                    } catch (Exception e) {
                        System.err.println("Skipping image layer: " + e.getMessage());
                    }
                }
            }
        } else if (layer.getType() == LayerType.RECTANGLE) {
            Rectangle2D rect = new Rectangle2D.Double(x, y, w, h);
            fillAndStroke(g, rect, layer, fontScale);
        } else if (layer.getType() == LayerType.ELLIPSE) {
            Ellipse2D ellipse = new Ellipse2D.Double(x, y, w, h);
            fillAndStroke(g, ellipse, layer, fontScale);
        }
        g.setComposite(oldComposite);
    }

    private void fillAndStroke(Graphics2D g, java.awt.Shape shape, ProjectLayer layer, double scale) {
        g.setColor(toAwt(layer.getFillColor()));
        g.fill(shape);
        if (layer.getStrokeWidth() > 0.0) {
            g.setStroke(new BasicStroke((float) Math.max(1, layer.getStrokeWidth() * scale)));
            g.setColor(toAwt(layer.getStrokeColor()));
            g.draw(shape);
        }
    }

    private Color toAwt(javafx.scene.paint.Color color) {
        return new Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
    }
}

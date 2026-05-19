// Module 3: JCodec video rendering pipeline.
package com.photofusionfx.service;

import com.photofusionfx.model.PhotoItem;
import com.photofusionfx.model.ProjectLayer;
import com.photofusionfx.util.ImageUtils;
import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VideoService {
    public void renderVideo(List<PhotoItem> sequence,
                            String overlayText,
                            int secondsPerImage,
                            int fps,
                            int outputWidth,
                            File outputFile,
                            Consumer<Double> progressConsumer) throws IOException {
        renderVideo(sequence, overlayText, secondsPerImage, fps, outputWidth, outputFile, List.of(), 1280, 720, progressConsumer);
    }

    public void renderVideo(List<PhotoItem> sequence,
                            String overlayText,
                            int secondsPerImage,
                            int fps,
                            int outputWidth,
                            File outputFile,
                            List<ProjectLayer> graphicalLayers,
                            int layerReferenceWidth,
                            int layerReferenceHeight,
                            Consumer<Double> progressConsumer) throws IOException {
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("Select at least one image for video generation.");
        }
        fps = Math.max(12, fps);
        secondsPerImage = Math.max(1, secondsPerImage);
        int width = makeEven(Math.max(640, outputWidth));
        int height = makeEven((int) Math.round(width * 9.0 / 16.0));
        int displayFrames = secondsPerImage * fps;
        int transitionFrames = sequence.size() > 1 ? Math.max(1, Math.min(fps / 2, displayFrames / 3)) : 0;
        int holdFrames = Math.max(1, displayFrames - transitionFrames);
        int totalFrames = sequence.size() * displayFrames - transitionFrames * (sequence.size() - 1);

        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, fps);
        int frameIndex = 0;
        try {
            for (int i = 0; i < sequence.size(); i++) {
                BufferedImage current = renderStyledFrame(ImageUtils.read(new File(sequence.get(i).getFilePath())), width, height, overlayText, i, sequence.size(), graphicalLayers, layerReferenceWidth, layerReferenceHeight);
                current = ImageUtils.ensureEvenDimensions(current);
                if (i < sequence.size() - 1) {
                    BufferedImage next = renderStyledFrame(ImageUtils.read(new File(sequence.get(i + 1).getFilePath())), width, height, overlayText, i + 1, sequence.size(), graphicalLayers, layerReferenceWidth, layerReferenceHeight);
                    next = ImageUtils.ensureEvenDimensions(next);
                    for (int f = 0; f < holdFrames; f++) {
                        encoder.encodeImage(current);
                        frameIndex++;
                        notifyProgress(progressConsumer, frameIndex, totalFrames);
                    }
                    for (int t = 0; t < transitionFrames; t++) {
                        float alpha = (t + 1f) / transitionFrames;
                        BufferedImage blended = blend(current, next, alpha);
                        encoder.encodeImage(blended);
                        frameIndex++;
                        notifyProgress(progressConsumer, frameIndex, totalFrames);
                    }
                } else {
                    for (int f = 0; f < displayFrames; f++) {
                        encoder.encodeImage(current);
                        frameIndex++;
                        notifyProgress(progressConsumer, frameIndex, totalFrames);
                    }
                }
            }
        } finally {
            encoder.finish();
        }
    }

    private void notifyProgress(Consumer<Double> progressConsumer, int current, int total) {
        if (progressConsumer != null) {
            progressConsumer.accept(Math.min(1.0, current / (double) total));
        }
    }

    private BufferedImage renderStyledFrame(BufferedImage source,
                                            int width,
                                            int height,
                                            String overlayText,
                                            int index,
                                            int total,
                                            List<ProjectLayer> graphicalLayers,
                                            int layerReferenceWidth,
                                            int layerReferenceHeight) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        drawCoverBackground(g, source, width, height);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
        g.setColor(new Color(8, 12, 20));
        g.fillRect(0, 0, width, height);
        g.setComposite(AlphaComposite.SrcOver);
        drawContainedImage(g, source, width, height);
        drawDecorativeChrome(g, width, height, overlayText, index, total);
        drawGraphicalLayers(g, graphicalLayers, width, height, layerReferenceWidth, layerReferenceHeight);

        g.dispose();
        return canvas;
    }

    private void drawCoverBackground(Graphics2D g, BufferedImage source, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int drawWidth = (int) Math.round(source.getWidth() * scale);
        int drawHeight = (int) Math.round(source.getHeight() * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        g.drawImage(source, x, y, drawWidth, drawHeight, null);
    }

    private void drawContainedImage(Graphics2D g, BufferedImage source, int width, int height) {
        double scale = Math.min((width * 0.92) / source.getWidth(), (height * 0.82) / source.getHeight());
        int drawWidth = (int) Math.round(source.getWidth() * scale);
        int drawHeight = (int) Math.round(source.getHeight() * scale);
        int x = (width - drawWidth) / 2;
        int y = (int) (height * 0.06) + (int) ((height * 0.76 - drawHeight) / 2.0);

        g.setColor(new Color(0, 0, 0, 70));
        g.fillRoundRect(x - 10, y - 10, drawWidth + 20, drawHeight + 20, 20, 20);
        g.drawImage(source, x, y, drawWidth, drawHeight, null);
        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x - 1, y - 1, drawWidth + 2, drawHeight + 2, 14, 14);
    }

    private void drawDecorativeChrome(Graphics2D g, int width, int height, String overlayText, int index, int total) {
        int panelX = Math.max(24, width / 28);
        int panelW = width - panelX * 2;
        int panelH = Math.max(86, height / 7);
        int panelY = height - panelH - Math.max(24, height / 24);
        g.setColor(new Color(5, 10, 18, 165));
        g.fill(new RoundRectangle2D.Double(panelX, panelY, panelW, panelH, 20, 20));
        g.setColor(new Color(255, 255, 255, 52));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Double(panelX, panelY, panelW, panelH, 20, 20));

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(22, width / 48)));
        String title = overlayText == null || overlayText.isBlank() ? "Favourite Photo Montage" : overlayText.strip();
        List<String> lines = wrapText(g, title, panelW - 48);
        int textY = panelY + Math.max(34, panelH / 3);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.drawString(lines.get(i), panelX + 24, textY + i * Math.max(24, width / 56));
        }

        int barX = panelX + 24;
        int barY = panelY + panelH - 20;
        int barW = panelW - 48;
        g.setColor(new Color(255, 255, 255, 45));
        g.fillRoundRect(barX, barY, barW, 6, 6, 6);
        g.setPaint(new GradientPaint(barX, 0, new Color(255, 255, 255, 220), barX + barW, 0, new Color(130, 180, 255, 210)));
        int filledW = Math.max(12, (int) Math.round(barW * ((index + 1) / (double) total)));
        g.fillRoundRect(barX, barY, filledW, 6, 6, 6);
    }


    private void drawGraphicalLayers(Graphics2D g,
                                     List<ProjectLayer> graphicalLayers,
                                     int width,
                                     int height,
                                     int referenceWidth,
                                     int referenceHeight) {
        if (graphicalLayers == null || graphicalLayers.isEmpty()) {
            return;
        }
        referenceWidth = Math.max(1, referenceWidth);
        referenceHeight = Math.max(1, referenceHeight);
        try {
            new LayerRenderService().drawLayers(g, graphicalLayers, width / (double) referenceWidth, height / (double) referenceHeight);
        } catch (IOException ignored) {
            // Missing optional image assets should not abort a whole video render.
        }
    }

    private List<String> wrapText(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            String[] words = paragraph.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (fm.stringWidth(candidate) > maxWidth) {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        lines.add(candidate);
                        current = new StringBuilder();
                    }
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private BufferedImage blend(BufferedImage current, BufferedImage next, float alpha) {
        BufferedImage blended = new BufferedImage(current.getWidth(), current.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = blended.createGraphics();
        g.drawImage(current, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(next, 0, 0, null);
        g.dispose();
        return blended;
    }

    private int makeEven(int value) {
        return value % 2 == 0 ? value : value + 1;
    }
}

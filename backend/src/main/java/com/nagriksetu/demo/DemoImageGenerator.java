package com.nagriksetu.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws clearly labelled placeholder images for demonstration complaints. They are illustrations,
 * not photographs, and say so on the image itself.
 */
final class DemoImageGenerator {

    private DemoImageGenerator() {
    }

    static byte[] png(String heading, String subheading, Color base, long seed) {
        int w = 960;
        int h = 640;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, base.brighter(), w, h, base.darker()));
            g.fillRect(0, 0, w, h);
            Random random = new Random(seed);
            g.setStroke(new BasicStroke(3f));
            for (int i = 0; i < 18; i++) {
                g.setColor(new Color(255, 255, 255, 25 + random.nextInt(40)));
                int size = 40 + random.nextInt(160);
                g.fillOval(random.nextInt(w), random.nextInt(h), size, (int) (size * 0.6));
            }
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(60, h / 2 - 130, w - 120, 260, 28, 28);
            g.setColor(Color.WHITE);
            try {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                g.drawString("DEMO IMAGE • NOT A REAL PHOTOGRAPH", 100, h / 2 - 70);
                int size = 54;
                do {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
                    size -= 2;
                } while (size > 20 && g.getFontMetrics().stringWidth(heading) > w - 200);
                g.drawString(heading, 100, h / 2 + 5);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
                g.drawString(subheading, 100, h / 2 + 60);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                g.drawString("Nagrik Setu sample data • Hyderabad (demonstration)", 100, h / 2 + 105);
            } catch (Exception ignored) {
                // No fonts available in this environment: the labelled box still marks it as a placeholder.
            }
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

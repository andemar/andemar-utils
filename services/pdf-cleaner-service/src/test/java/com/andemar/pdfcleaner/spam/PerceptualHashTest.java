package com.andemar.pdfcleaner.spam;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerceptualHashTest {

    @Test
    void testIdenticalImagesProduceZeroHammingDistance() {
        BufferedImage img1 = createSampleImage(50, 50, Color.BLUE, Color.YELLOW);
        BufferedImage img2 = createSampleImage(50, 50, Color.BLUE, Color.YELLOW);

        long hash1 = PerceptualHash.computeDHash(img1);
        long hash2 = PerceptualHash.computeDHash(img2);

        assertEquals(0, PerceptualHash.hammingDistance(hash1, hash2));
        assertTrue(PerceptualHash.isSimilar(hash1, hash2, 0));
    }

    @Test
    void testRescaledImagesHaveSmallHammingDistance() {
        BufferedImage original = createSampleImage(100, 100, Color.RED, Color.WHITE);
        BufferedImage scaledDown = createSampleImage(30, 30, Color.RED, Color.WHITE);

        long hash1 = PerceptualHash.computeDHash(original);
        long hash2 = PerceptualHash.computeDHash(scaledDown);

        int distance = PerceptualHash.hammingDistance(hash1, hash2);
        assertTrue(distance <= 5, "Scaled image distance should be <= 5, was: " + distance);
    }

    @Test
    void testCompletelyDifferentImagesHaveHighDistance() {
        BufferedImage verticalStripes = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = verticalStripes.createGraphics();
        g1.setColor(Color.WHITE);
        g1.fillRect(0, 0, 32, 64);
        g1.setColor(Color.BLACK);
        g1.fillRect(32, 0, 32, 64);
        g1.dispose();

        BufferedImage horizontalStripes = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = horizontalStripes.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 64, 32);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 32, 64, 32);
        g2.dispose();

        long hash1 = PerceptualHash.computeDHash(verticalStripes);
        long hash2 = PerceptualHash.computeDHash(horizontalStripes);

        int distance = PerceptualHash.hammingDistance(hash1, hash2);
        assertTrue(distance > 15, "Inverted pattern distance should be > 15, was: " + distance);
    }

    private BufferedImage createSampleImage(int width, int height, Color c1, Color c2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c1);
        g.fillRect(0, 0, width, height);
        g.setColor(c2);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        return img;
    }
}

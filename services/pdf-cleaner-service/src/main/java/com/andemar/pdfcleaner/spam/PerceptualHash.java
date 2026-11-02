package com.andemar.pdfcleaner.spam;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Utility for computing perceptual image hashes (dHash and aHash) using pure Java AWT,
 * enabling robust detection of identical or slightly modified repeated logos and watermarks
 * without native dependencies.
 */
public final class PerceptualHash {

    private PerceptualHash() {}

    /**
     * Computes 64-bit Difference Hash (dHash) from raw image bytes.
     */
    public static long computeDHash(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return 0L;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return 0L;
            return computeDHash(image);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Computes 64-bit Difference Hash (dHash) from a BufferedImage.
     * Resizes to 9x8 grayscale and sets bits based on whether each pixel is brighter than the next.
     */
    public static long computeDHash(BufferedImage img) {
        if (img == null) return 0L;

        BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        try {
            g.drawImage(img.getScaledInstance(9, 8, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            g.dispose();
        }

        long hash = 0L;
        int bitPos = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = scaled.getRaster().getSample(x, y, 0);
                int right = scaled.getRaster().getSample(x + 1, y, 0);
                if (left > right) {
                    hash |= (1L << bitPos);
                }
                bitPos++;
            }
        }
        return hash;
    }

    /**
     * Computes 64-bit Average Hash (aHash) from a BufferedImage.
     * Resizes to 8x8 grayscale and sets bits based on whether each pixel is above the mean value.
     */
    public static long computeAHash(BufferedImage img) {
        if (img == null) return 0L;

        BufferedImage scaled = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        try {
            g.drawImage(img.getScaledInstance(8, 8, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            g.dispose();
        }

        long sum = 0;
        int[] pixels = new int[64];
        int idx = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int val = scaled.getRaster().getSample(x, y, 0);
                pixels[idx++] = val;
                sum += val;
            }
        }

        double mean = sum / 64.0;
        long hash = 0L;
        for (int i = 0; i < 64; i++) {
            if (pixels[i] >= mean) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    /**
     * Calculates the Hamming distance (number of bit differences) between two 64-bit hashes.
     * Returns a value between 0 (identical) and 64 (completely inverted).
     */
    public static int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
    }

    /**
     * Returns true if two hashes are considered perceptually similar within a tolerance threshold.
     */
    public static boolean isSimilar(long hash1, long hash2, int maxHammingDistance) {
        return hammingDistance(hash1, hash2) <= maxHammingDistance;
    }
}

package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an extracted raster image from the PDF with bounding box, image bytes, dimensions, and perceptual hashes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageBlock {
    private String id;
    private BoundingBox boundingBox;
    private int pageNumber;
    private int imageIndex;
    private byte[] rawBytes;
    private String mimeType;
    private int pixelWidth;
    private int pixelHeight;
    private long dHash; // Difference perceptual hash
    private long aHash; // Average perceptual hash
    private boolean isSpam;
    private String spamReason;

    public void markSpam(String reason) {
        this.isSpam = true;
        this.spamReason = reason;
    }

    public double getAspectRatio() {
        if (pixelHeight == 0) return 1.0;
        return (double) pixelWidth / (double) pixelHeight;
    }
}

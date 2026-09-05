package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an extracted line or fragment of text with its coordinates, font details, and spam status.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TextBlock {
    private String text;
    private BoundingBox boundingBox;
    private int pageNumber;
    private float fontSize;
    private String fontName;
    private boolean bold;
    private boolean isSpam;
    private String spamReason;

    public void markSpam(String reason) {
        this.isSpam = true;
        this.spamReason = reason;
    }

    public String getCleanText() {
        return text != null ? text.trim() : "";
    }
}

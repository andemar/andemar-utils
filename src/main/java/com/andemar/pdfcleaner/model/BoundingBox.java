package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized 2D bounding box where (0,0) is top-left of the page.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoundingBox {
    private float x;
    private float y;
    private float width;
    private float height;

    public float getRight() {
        return x + width;
    }

    public float getBottom() {
        return y + height;
    }

    public float getCenterX() {
        return x + (width / 2.0f);
    }

    public float getCenterY() {
        return y + (height / 2.0f);
    }

    public float getArea() {
        return Math.max(0.0f, width) * Math.max(0.0f, height);
    }

    public boolean isInTopMargin(float pageHeight, float marginPercent) {
        float threshold = pageHeight * marginPercent;
        float tolerance = Math.max(15.0f, pageHeight * 0.04f);
        // Element must be positioned near the top AND not extend far past the margin threshold
        return y <= threshold && getBottom() <= (threshold + tolerance);
    }

    public boolean isInBottomMargin(float pageHeight, float marginPercent) {
        float threshold = pageHeight * (1.0f - marginPercent);
        float tolerance = Math.max(15.0f, pageHeight * 0.04f);
        // Element must be positioned near the bottom AND not start far above the margin threshold
        return getBottom() >= threshold && y >= (threshold - tolerance);
    }

    public boolean isInHorizontalMargin(float pageWidth, float marginPercent) {
        float leftThreshold = pageWidth * marginPercent;
        float rightThreshold = pageWidth * (1.0f - marginPercent);
        float tolerance = Math.max(15.0f, pageWidth * 0.04f);
        boolean inLeft = (x <= leftThreshold) && (getRight() <= leftThreshold + tolerance);
        boolean inRight = (getRight() >= rightThreshold) && (x >= rightThreshold - tolerance);
        return inLeft || inRight;
    }

    public boolean isInCorner(float pageWidth, float pageHeight, float marginPercent) {
        boolean vertical = isInTopMargin(pageHeight, marginPercent) || isInBottomMargin(pageHeight, marginPercent);
        boolean horizontal = isInHorizontalMargin(pageWidth, marginPercent);
        return vertical && horizontal;
    }

    public boolean intersects(BoundingBox other) {
        if (other == null) return false;
        return x < other.getRight() && getRight() > other.x &&
               y < other.getBottom() && getBottom() > other.y;
    }

    public float distanceTo(BoundingBox other) {
        if (other == null) return Float.MAX_VALUE;
        float dx = Math.max(0.0f, Math.max(x - other.getRight(), other.x - getRight()));
        float dy = Math.max(0.0f, Math.max(y - other.getBottom(), other.y - getBottom()));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}

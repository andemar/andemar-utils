package com.andemar.pdfcleaner.config;

public enum Aggressiveness {
    CONSERVATIVE(0.06f, 0.06f, 0.75f, 0.70f),
    BALANCED(0.10f, 0.10f, 0.55f, 0.50f),
    AGGRESSIVE(0.15f, 0.15f, 0.40f, 0.35f);

    private final float headerMargin;
    private final float footerMargin;
    private final float textFrequency;
    private final float imageFrequency;

    Aggressiveness(float headerMargin, float footerMargin, float textFrequency, float imageFrequency) {
        this.headerMargin = headerMargin;
        this.footerMargin = footerMargin;
        this.textFrequency = textFrequency;
        this.imageFrequency = imageFrequency;
    }

    public float getHeaderMargin() {
        return headerMargin;
    }

    public float getFooterMargin() {
        return footerMargin;
    }

    public float getTextFrequency() {
        return textFrequency;
    }

    public float getImageFrequency() {
        return imageFrequency;
    }
}

package com.andemar.pdfcleaner.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration options for the PDF cleaning and conversion pipeline.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CleanerConfig {
    @Builder.Default
    private Aggressiveness aggressiveness = Aggressiveness.BALANCED;

    @Builder.Default
    private Float headerMarginPercent = null;

    @Builder.Default
    private Float footerMarginPercent = null;

    @Builder.Default
    private Float textFrequencyThreshold = null;

    @Builder.Default
    private Float imageFrequencyThreshold = null;

    @Builder.Default
    private int minImageDimension = 20;

    @Builder.Default
    private float minImageAreaPercent = 0.005f;

    @Builder.Default
    private LayoutMode layoutMode = LayoutMode.REFLOWABLE;

    @Builder.Default
    private TextStructureMode textStructure = TextStructureMode.SEMANTIC;

    @Builder.Default
    private List<String> whitelistPhrases = new ArrayList<>();

    private String mlEndpointUrl;

    @Builder.Default
    private boolean dryRun = false;

    private String reportPath;

    public float getEffectiveHeaderMargin() {
        return headerMarginPercent != null ? headerMarginPercent : aggressiveness.getHeaderMargin();
    }

    public float getEffectiveFooterMargin() {
        return footerMarginPercent != null ? footerMarginPercent : aggressiveness.getFooterMargin();
    }

    public float getEffectiveTextFrequency() {
        return textFrequencyThreshold != null ? textFrequencyThreshold : aggressiveness.getTextFrequency();
    }

    public float getEffectiveImageFrequency() {
        return imageFrequencyThreshold != null ? imageFrequencyThreshold : aggressiveness.getImageFrequency();
    }

    public void addWhitelistPhrase(String phrase) {
        if (phrase != null && !phrase.isBlank()) {
            this.whitelistPhrases.add(phrase.trim());
        }
    }
}

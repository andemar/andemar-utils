package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.model.ImageBlock;

/**
 * Pluggable strategy interface for external machine-learning logo/branding classification
 * (e.g., external CLIP or vision-transformer microservice).
 */
public interface MlLogoClassifier {

    /**
     * Determines whether the given image is a logo, watermark, or spam decorative artifact.
     *
     * @param image the image block under inspection
     * @return true if classified as spam logo, false otherwise
     */
    boolean isSpamLogo(ImageBlock image);

    /**
     * Optional description of the classifier for logging.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}

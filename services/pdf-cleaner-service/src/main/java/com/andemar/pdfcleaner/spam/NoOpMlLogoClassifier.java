package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.model.ImageBlock;

/**
 * Default no-op classifier used when no external ML logo service is configured.
 */
public class NoOpMlLogoClassifier implements MlLogoClassifier {

    @Override
    public boolean isSpamLogo(ImageBlock image) {
        return false;
    }
}

package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import com.andemar.pdfcleaner.model.TextBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates text and image spam detection strategies across the parsed document.
 */
public class SpamDetectionEngine {

    private final TextSpamDetector textDetector;
    private final ImageSpamDetector imageDetector;

    public SpamDetectionEngine() {
        this(new TextSpamDetector(), new ImageSpamDetector());
    }

    public SpamDetectionEngine(TextSpamDetector textDetector, ImageSpamDetector imageDetector) {
        this.textDetector = textDetector != null ? textDetector : new TextSpamDetector();
        this.imageDetector = imageDetector != null ? imageDetector : new ImageSpamDetector();
    }

    public static SpamDetectionEngine create(CleanerConfig config) {
        MlLogoClassifier mlClassifier;
        if (config.getMlEndpointUrl() != null && !config.getMlEndpointUrl().isBlank()) {
            mlClassifier = new HttpClipLogoClassifier(config.getMlEndpointUrl());
        } else {
            mlClassifier = new NoOpMlLogoClassifier();
        }
        return new SpamDetectionEngine(new TextSpamDetector(), new ImageSpamDetector(mlClassifier));
    }

    public SpamDetectionReport process(DocumentModel document, CleanerConfig config) {
        // Stage 1: Run Text Spam Detection
        textDetector.detect(document, config);

        // Stage 2: Run Image Spam Detection (can leverage detected spam text for proximity)
        imageDetector.detect(document, config);

        // Stage 3: Collect metrics and compile diagnostic report
        int totalText = 0;
        int spamText = 0;
        int totalImages = 0;
        int spamImages = 0;
        Set<String> spamPhrases = new LinkedHashSet<>();
        Set<String> spamImageSummaries = new LinkedHashSet<>();

        for (PageModel page : document.getPages()) {
            for (TextBlock tb : page.getTextBlocks()) {
                totalText++;
                if (tb.isSpam()) {
                    spamText++;
                    spamPhrases.add(String.format("'%s' [%s, p.%d]", tb.getCleanText(), tb.getSpamReason(), page.getPageNumber()));
                }
            }

            for (ImageBlock img : page.getImageBlocks()) {
                totalImages++;
                if (img.isSpam()) {
                    spamImages++;
                    spamImageSummaries.add(String.format("Image %s (%dx%d) [%s, p.%d]", 
                            img.getId(), img.getPixelWidth(), img.getPixelHeight(), img.getSpamReason(), page.getPageNumber()));
                }
            }
        }

        SpamDetectionReport report = SpamDetectionReport.builder()
                .totalPages(document.getPageCount())
                .totalTextBlocks(totalText)
                .spamTextBlocksCount(spamText)
                .totalImages(totalImages)
                .spamImagesCount(spamImages)
                .flaggedSpamPhrases(new ArrayList<>(spamPhrases))
                .flaggedSpamImages(new ArrayList<>(spamImageSummaries))
                .build();

        report.addLog(String.format("Analyzed %d pages: marked %d/%d text blocks and %d/%d images as spam.",
                document.getPageCount(), spamText, totalText, spamImages, totalImages));

        return report;
    }
}

package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.BoundingBox;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.TextBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects spam images (repeated logos, header/footer icons, watermarks, decorative rules)
 * using perceptual hash clustering, position zones, size thresholds, text spam proximity,
 * and optional ML classifier calls, while safeguarding genuine figures and diagrams.
 */
public class ImageSpamDetector {

    private final MlLogoClassifier mlClassifier;

    public ImageSpamDetector() {
        this(new NoOpMlLogoClassifier());
    }

    public ImageSpamDetector(MlLogoClassifier mlClassifier) {
        this.mlClassifier = mlClassifier != null ? mlClassifier : new NoOpMlLogoClassifier();
    }

    public void detect(DocumentModel document, CleanerConfig config) {
        if (document == null || document.getPages() == null || document.getPages().isEmpty()) {
            return;
        }

        int totalPages = document.getPageCount();
        float headerMargin = config.getEffectiveHeaderMargin();
        float footerMargin = config.getEffectiveFooterMargin();
        float imageFreqThreshold = config.getEffectiveImageFrequency();
        int minDimension = config.getMinImageDimension();
        float minAreaPercent = config.getMinImageAreaPercent();

        // 1. Collect all images
        List<ImageBlock> allImages = new ArrayList<>();
        for (PageModel page : document.getPages()) {
            allImages.addAll(page.getImageBlocks());
        }

        if (allImages.isEmpty()) {
            return;
        }

        // 2. Perceptual Hash clustering (dHash similarity with Hamming distance <= 6)
        List<ImageCluster> clusters = clusterImages(allImages);

        // 3. Mark clusters that appear on many pages as repeating logo/watermark spam
        for (ImageCluster cluster : clusters) {
            Set<Integer> distinctPages = cluster.getDistinctPages();
            double frequency = (double) distinctPages.size() / totalPages;

            boolean isRepeating = totalPages >= 2 && (frequency >= imageFreqThreshold || 
                    (totalPages >= 3 && frequency >= 0.40 && cluster.isGenerallyInMargins(headerMargin, footerMargin)));

            if (isRepeating) {
                for (ImageBlock img : cluster.images) {
                    img.markSpam(String.format("REPEATED_LOGO_HASH (freq=%.0f%% on %d pages)", 
                            frequency * 100, distinctPages.size()));
                }
            }
        }

        // 4. Per-image inspection for individual decorative or zone-based spam
        for (PageModel page : document.getPages()) {
            float pageWidth = page.getWidth();
            float pageHeight = page.getHeight();
            float pageArea = pageWidth * pageHeight;

            for (ImageBlock img : page.getImageBlocks()) {
                if (img.isSpam()) {
                    continue; // Already flagged
                }

                BoundingBox bbox = img.getBoundingBox();
                float imgArea = bbox.getArea();
                float areaRatio = pageArea > 0 ? (imgArea / pageArea) : 0;

                // Safeguard: Large, prominent images (> 10% of page area or >= 200px in both dimensions)
                // that are not repeating logo clusters are genuine book illustrations, diagrams, or covers!
                boolean isLarge = areaRatio >= 0.10f || (img.getPixelWidth() >= 200 && img.getPixelHeight() >= 200);
                if (isLarge) {
                    // Only consider spam if strictly confined entirely within a header or footer strip
                    boolean strictlyInMarginStrip = (bbox.isInTopMargin(pageHeight, headerMargin) && bbox.getHeight() < pageHeight * (headerMargin + 0.05f)) ||
                                                   (bbox.isInBottomMargin(pageHeight, footerMargin) && bbox.getHeight() < pageHeight * (footerMargin + 0.05f));
                    if (!strictlyInMarginStrip) {
                        continue; // Safeguarded legitimate illustration
                    }
                }

                // Heuristic A: Tiny decorative icon/line in margins
                boolean isTiny = img.getPixelWidth() < minDimension || img.getPixelHeight() < minDimension || areaRatio < minAreaPercent;
                boolean inMargin = bbox.isInTopMargin(pageHeight, headerMargin) || bbox.isInBottomMargin(pageHeight, footerMargin);

                if (isTiny && inMargin) {
                    img.markSpam("TINY_MARGIN_DECORATION");
                    continue;
                }

                // Heuristic B: Branding corners (top-left or top-right or bottom-left or bottom-right in margin)
                boolean inCorner = bbox.isInCorner(pageWidth, pageHeight, Math.max(headerMargin, 0.12f));
                if (inCorner && (isTiny || areaRatio < 0.05f)) {
                    img.markSpam("CORNER_BRANDING_ZONE");
                    continue;
                }

                // Heuristic C: Direct proximity to an already detected spam text block (e.g. small logo right next to "Downloaded from...")
                // This heuristic strictly applies to small decorative icons/logos, never to large figures or illustrations!
                if (!isLarge && isNearSpamTextBlock(bbox, page.getTextBlocks(), 30.0f)) {
                    img.markSpam("PROXIMITY_TO_SPAM_TEXT");
                    continue;
                }

                // Heuristic D: Optional ML Logo Classifier
                if (mlClassifier.isSpamLogo(img)) {
                    img.markSpam("ML_CLASSIFIED_LOGO (" + mlClassifier.getName() + ")");
                }
            }
        }
    }

    private boolean isNearSpamTextBlock(BoundingBox imgBbox, List<TextBlock> textBlocks, float maxDistance) {
        if (textBlocks == null || textBlocks.isEmpty()) return false;
        for (TextBlock tb : textBlocks) {
            if (tb.isSpam()) {
                if (imgBbox.distanceTo(tb.getBoundingBox()) <= maxDistance) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ImageCluster> clusterImages(List<ImageBlock> images) {
        List<ImageCluster> clusters = new ArrayList<>();
        for (ImageBlock img : images) {
            boolean added = false;
            for (ImageCluster cluster : clusters) {
                if (cluster.matches(img)) {
                    cluster.add(img);
                    added = true;
                    break;
                }
            }
            if (!added) {
                ImageCluster newCluster = new ImageCluster();
                newCluster.add(img);
                clusters.add(newCluster);
            }
        }
        return clusters;
    }

    private static class ImageCluster {
        private final List<ImageBlock> images = new ArrayList<>();
        private long representativeDHash = 0L;
        private int avgWidth = 0;
        private int avgHeight = 0;

        public void add(ImageBlock img) {
            if (images.isEmpty()) {
                this.representativeDHash = img.getDHash();
                this.avgWidth = img.getPixelWidth();
                this.avgHeight = img.getPixelHeight();
            }
            images.add(img);
        }

        public boolean matches(ImageBlock img) {
            if (images.isEmpty()) return false;

            // Same or very close pixel aspect ratio / dimensions
            boolean sizeClose = Math.abs(img.getPixelWidth() - avgWidth) <= 5 &&
                                Math.abs(img.getPixelHeight() - avgHeight) <= 5;

            // Perceptual dHash check
            int dist = PerceptualHash.hammingDistance(representativeDHash, img.getDHash());

            // If dHash is 0 for both (e.g. solid color), use size match
            if (representativeDHash == 0 && img.getDHash() == 0) {
                return sizeClose;
            }

            return dist <= 6 && sizeClose;
        }

        public Set<Integer> getDistinctPages() {
            Set<Integer> pages = new HashSet<>();
            for (ImageBlock img : images) {
                pages.add(img.getPageNumber());
            }
            return pages;
        }

        public boolean isGenerallyInMargins(float headerMargin, float footerMargin) {
            int inMarginCount = 0;
            for (ImageBlock img : images) {
                // Heuristic check on bbox
                BoundingBox bbox = img.getBoundingBox();
                if (bbox != null && (bbox.getY() < 120 || bbox.getBottom() > 650)) {
                    inMarginCount++;
                }
            }
            return (double) inMarginCount / images.size() >= 0.5;
        }
    }
}

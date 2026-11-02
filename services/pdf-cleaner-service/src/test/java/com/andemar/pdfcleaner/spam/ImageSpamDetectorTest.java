package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.BoundingBox;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.PageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSpamDetectorTest {

    private ImageSpamDetector detector;
    private CleanerConfig config;

    @BeforeEach
    void setUp() {
        detector = new ImageSpamDetector();
        config = new CleanerConfig();
    }

    @Test
    void testRepeatedLogosAreFlaggedAndUniqueDiagramsAreRetained() {
        long logoDHash = 0x123456789ABCDEF0L;
        long diagramDHash = 0xFEDCBA9876543210L;

        List<PageModel> pages = new ArrayList<>();
        float pageWidth = 600.0f;
        float pageHeight = 800.0f;

        // Create 4 pages with repeated 40x40 logo at top right
        for (int p = 1; p <= 4; p++) {
            List<ImageBlock> images = new ArrayList<>();

            // Repeated logo
            images.add(ImageBlock.builder()
                    .id("logo-" + p)
                    .pageNumber(p)
                    .imageIndex(1)
                    .pixelWidth(40)
                    .pixelHeight(40)
                    .dHash(logoDHash)
                    .boundingBox(BoundingBox.builder().x(540).y(20).width(40).height(40).build())
                    .build());

            // Page 2 has a large, unique central diagram
            if (p == 2) {
                images.add(ImageBlock.builder()
                        .id("unique-diagram-p2")
                        .pageNumber(p)
                        .imageIndex(2)
                        .pixelWidth(450)
                        .pixelHeight(320)
                        .dHash(diagramDHash)
                        .boundingBox(BoundingBox.builder().x(75).y(200).width(450).height(320).build())
                        .build());
            }

            pages.add(PageModel.builder()
                    .pageNumber(p)
                    .width(pageWidth)
                    .height(pageHeight)
                    .imageBlocks(images)
                    .build());
        }

        DocumentModel doc = DocumentModel.builder()
                .metadata(DocumentMetadata.builder().title("Book").build())
                .pages(pages)
                .build();

        detector.detect(doc, config);

        // Verify logos were flagged
        for (PageModel page : doc.getPages()) {
            ImageBlock logo = page.getImageBlocks().get(0);
            assertTrue(logo.isSpam(), "Repeated logo should be flagged as spam on page " + page.getPageNumber());
            assertTrue(logo.getSpamReason().contains("REPEATED_LOGO"));
        }

        // Verify unique diagram was retained
        ImageBlock diagram = doc.getPages().get(1).getImageBlocks().get(1);
        assertFalse(diagram.isSpam(), "Unique central book diagram must NOT be flagged as spam");
    }

    @Test
    void testPluggableMlClassifierIntegration() {
        MlLogoClassifier mockMl = img -> img.getId().equals("suspicious-badge");
        ImageSpamDetector mlDetector = new ImageSpamDetector(mockMl);

        ImageBlock img1 = ImageBlock.builder()
                .id("suspicious-badge")
                .pageNumber(1)
                .pixelWidth(80)
                .pixelHeight(80)
                .boundingBox(BoundingBox.builder().x(100).y(200).width(80).height(80).build())
                .build();

        ImageBlock img2 = ImageBlock.builder()
                .id("legit-photo")
                .pageNumber(1)
                .pixelWidth(300)
                .pixelHeight(300)
                .boundingBox(BoundingBox.builder().x(100).y(300).width(300).height(300).build())
                .build();

        DocumentModel doc = DocumentModel.builder()
                .metadata(DocumentMetadata.builder().title("ML Test").build())
                .pages(List.of(PageModel.builder()
                        .pageNumber(1)
                        .width(600)
                        .height(800)
                        .imageBlocks(new ArrayList<>(List.of(img1, img2)))
                        .build()))
                .build();

        mlDetector.detect(doc, config);

        assertTrue(img1.isSpam(), "Suspicious badge should be flagged by ML classifier");
        assertTrue(img1.getSpamReason().contains("ML_CLASSIFIED_LOGO"));
        assertFalse(img2.isSpam(), "Legitimate photo must not be flagged");
    }
}

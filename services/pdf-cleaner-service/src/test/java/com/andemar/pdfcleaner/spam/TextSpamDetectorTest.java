package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.BoundingBox;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSpamDetectorTest {

    private TextSpamDetector detector;
    private CleanerConfig config;

    @BeforeEach
    void setUp() {
        detector = new TextSpamDetector();
        config = new CleanerConfig();
    }

    @Test
    void testRepeatingHeadersAndFootersAreFlagged() {
        DocumentModel doc = buildMockDocument(5, true, true, false);

        detector.detect(doc, config);

        for (PageModel page : doc.getPages()) {
            // Header block at y=20 (top 2.5% of 800)
            TextBlock header = page.getTextBlocks().get(0);
            assertTrue(header.isSpam(), "Header should be flagged as spam on page " + page.getPageNumber());
            assertTrue(header.getSpamReason().contains("HEADER"));

            // Body block at y=300 (central)
            TextBlock body = page.getTextBlocks().get(1);
            assertFalse(body.isSpam(), "Body text should NOT be flagged as spam");

            // Footer block at y=770 (bottom of 800)
            TextBlock footer = page.getTextBlocks().get(2);
            assertTrue(footer.isSpam(), "Footer page number should be flagged as spam");
        }
    }

    @Test
    void testWhitelistedPhrasesArePreservedEvenInHeader() {
        config.addWhitelistPhrase("Important Header");
        DocumentModel doc = buildMockDocument(5, true, false, true);

        detector.detect(doc, config);

        for (PageModel page : doc.getPages()) {
            TextBlock header = page.getTextBlocks().get(0);
            assertFalse(header.isSpam(), "Whitelisted header should NOT be marked as spam");
        }
    }

    @Test
    void testBrandingKeywordsAreFlagged() {
        DocumentModel doc = DocumentModel.builder()
                .metadata(DocumentMetadata.builder().title("Test").build())
                .pages(List.of(
                        createSingleTextBlockPage(1, "Downloaded from www.ebookfree.org - All Rights Reserved", 15.0f),
                        createSingleTextBlockPage(2, "Downloaded from www.ebookfree.org - All Rights Reserved", 15.0f)
                ))
                .build();

        detector.detect(doc, config);

        assertTrue(doc.getPages().get(0).getTextBlocks().get(0).isSpam());
        assertTrue(doc.getPages().get(1).getTextBlocks().get(0).isSpam());
    }

    private DocumentModel buildMockDocument(int pageCount, boolean withHeader, boolean withPageNum, boolean whitelistHeader) {
        List<PageModel> pages = new ArrayList<>();
        float pageHeight = 800.0f;
        float pageWidth = 600.0f;

        for (int p = 1; p <= pageCount; p++) {
            List<TextBlock> blocks = new ArrayList<>();

            if (withHeader) {
                String headerText = whitelistHeader ? "Important Header Section" : "ACME CORP CONFIDENTIAL MANUAL";
                blocks.add(TextBlock.builder()
                        .text(headerText)
                        .pageNumber(p)
                        .boundingBox(BoundingBox.builder().x(50).y(20).width(300).height(12).build())
                        .fontSize(9.0f)
                        .build());
            }

            blocks.add(TextBlock.builder()
                    .text("This is genuine chapter body text discussing algorithms and data architectures.")
                    .pageNumber(p)
                    .boundingBox(BoundingBox.builder().x(50).y(300).width(500).height(40).build())
                    .fontSize(12.0f)
                    .build());

            if (withPageNum) {
                blocks.add(TextBlock.builder()
                        .text(String.valueOf(p))
                        .pageNumber(p)
                        .boundingBox(BoundingBox.builder().x(290).y(770).width(20).height(12).build())
                        .fontSize(9.0f)
                        .build());
            }

            pages.add(PageModel.builder()
                    .pageNumber(p)
                    .width(pageWidth)
                    .height(pageHeight)
                    .textBlocks(blocks)
                    .build());
        }

        return DocumentModel.builder()
                .metadata(DocumentMetadata.builder().title("Test Doc").build())
                .pages(pages)
                .build();
    }

    private PageModel createSingleTextBlockPage(int pageNum, String text, float y) {
        TextBlock tb = TextBlock.builder()
                .text(text)
                .pageNumber(pageNum)
                .boundingBox(BoundingBox.builder().x(50).y(y).width(400).height(15).build())
                .fontSize(9.0f)
                .build();
        return PageModel.builder()
                .pageNumber(pageNum)
                .width(600)
                .height(800)
                .textBlocks(new ArrayList<>(List.of(tb)))
                .build();
    }
}

package com.andemar.pdfcleaner.pipeline;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.Chapter;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Clean2EpubPipelineTest {

    @Test
    void testEndToEndPdfCleaningAndEpubGeneration(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("sample_polluted_book.pdf").toFile();
        File epubFile = tempDir.resolve("sample_cleaned_book.epub").toFile();

        // 1. Create a synthetic PDF containing spam headers, footers, repeated logos,
        // and legitimate book text with a unique illustration.
        createSyntheticPollutedPdf(pdfFile);

        // 2. Execute the Cleaning and Conversion Pipeline
        CleanerConfig config = CleanerConfig.builder()
                .headerMarginPercent(0.10f)
                .footerMarginPercent(0.10f)
                .textFrequencyThreshold(0.60f)
                .imageFrequencyThreshold(0.50f)
                .build();

        CleanerPipelineOrchestrator orchestrator = new CleanerPipelineOrchestrator();
        CleanerPipelineOrchestrator.CleanResult result = orchestrator.processFile(pdfFile, epubFile, config);

        // 3. Verify Spam Detection Report
        SpamDetectionReport report = result.getReport();
        assertNotNull(report);
        assertTrue(report.getSpamTextBlocksCount() >= 3, 
                "Should have flagged at least 3 spam text blocks (repeated headers/footers), found: " + report.getSpamTextBlocksCount());
        assertTrue(report.getSpamImagesCount() >= 3, 
                "Should have flagged 3 repeated logos as spam, found: " + report.getSpamImagesCount());

        // 4. Verify Cleaned Document
        CleanedDocument cleanedDoc = result.getCleanedDocument();
        assertNotNull(cleanedDoc);
        assertEquals("sample_polluted_book", cleanedDoc.getMetadata().getTitle(),
                "Title should be derived from input filename 'sample_polluted_book.pdf'");
        assertFalse(cleanedDoc.getChapters().isEmpty());

        // Verify genuine unique diagram was preserved
        boolean foundDiagram = false;
        for (Chapter ch : cleanedDoc.getChapters()) {
            for (ImageBlock img : ch.getImages()) {
                if (img.getPixelWidth() >= 200) {
                    foundDiagram = true;
                    break;
                }
            }
        }
        assertTrue(foundDiagram, "Legitimate unique book illustration should be preserved in chapters");

        // 5. Verify Output EPUB File
        assertTrue(epubFile.exists(), "Output EPUB file must exist on disk");
        assertTrue(epubFile.length() > 500, "EPUB file should be non-trivial in size");

        List<String> zipEntries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(epubFile.toPath())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertTrue(zipEntries.contains("mimetype"));
        assertTrue(zipEntries.contains("META-INF/container.xml"));
        assertTrue(zipEntries.contains("OEBPS/content.opf"));
        assertTrue(zipEntries.contains("OEBPS/nav.xhtml"));
        assertTrue(zipEntries.contains("OEBPS/toc.ncx"));
        assertTrue(zipEntries.contains("OEBPS/chapter_001.xhtml"));
        assertTrue(zipEntries.contains("OEBPS/images/image_1.png"), "Preserved illustration must be embedded in EPUB");
    }

    private void createSyntheticPollutedPdf(File destination) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font fontHelv = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            // Create 40x40 logo bitmap
            BufferedImage logoImg = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D gLogo = logoImg.createGraphics();
            gLogo.setColor(Color.RED);
            gLogo.fillRect(0, 0, 40, 40);
            gLogo.setColor(Color.WHITE);
            gLogo.drawString("LOGO", 4, 25);
            gLogo.dispose();

            // Create 300x200 unique technical diagram bitmap
            BufferedImage diagramImg = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D gDiag = diagramImg.createGraphics();
            gDiag.setColor(Color.WHITE);
            gDiag.fillRect(0, 0, 300, 200);
            gDiag.setColor(Color.BLUE);
            gDiag.drawRect(20, 20, 260, 160);
            gDiag.drawString("System Architecture Diagram", 50, 100);
            gDiag.dispose();

            PDImageXObject logoXObj = LosslessFactory.createFromImage(doc, logoImg);
            PDImageXObject diagramXObj = LosslessFactory.createFromImage(doc, diagramImg);

            for (int p = 1; p <= 3; p++) {
                PDPage page = new PDPage(PDRectangle.A4); // 595 x 842 points
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    // 1. Draw spam repeated header text in top 5% (y ~ 810)
                    cs.beginText();
                    cs.setFont(fontHelv, 9);
                    cs.newLineAtOffset(50, 810);
                    cs.showText("CONFIDENTIAL TRAINING MANUAL - ACME CORP");
                    cs.endText();

                    // 2. Draw spam repeated logo in top-right margin
                    cs.drawImage(logoXObj, 520, 790, 40, 40);

                    // 3. Draw legitimate chapter content
                    if (p == 1) {
                        cs.beginText();
                        cs.setFont(fontBold, 18);
                        cs.newLineAtOffset(50, 700);
                        cs.showText("Chapter 1: Principles of Software Architecture");
                        cs.endText();

                        cs.beginText();
                        cs.setFont(fontHelv, 11);
                        cs.newLineAtOffset(50, 650);
                        cs.showText("Modern software engineering emphasizes modular monolithic design, clean abstractions,");
                        cs.endText();

                        cs.beginText();
                        cs.setFont(fontHelv, 11);
                        cs.newLineAtOffset(50, 630);
                        cs.showText("and robust heuristic filtering pipelines that operate deterministically.");
                        cs.endText();
                    } else if (p == 2) {
                        cs.beginText();
                        cs.setFont(fontBold, 14);
                        cs.newLineAtOffset(50, 700);
                        cs.showText("Component Overview and Pipeline Flow");
                        cs.endText();

                        cs.beginText();
                        cs.setFont(fontHelv, 11);
                        cs.newLineAtOffset(50, 660);
                        cs.showText("Refer to the architectural blueprint below illustrating the ingestion and detection steps:");
                        cs.endText();

                        // Draw legitimate unique figure in center
                        cs.drawImage(diagramXObj, 150, 400, 300, 200);

                        cs.beginText();
                        cs.setFont(fontHelv, 11);
                        cs.newLineAtOffset(50, 350);
                        cs.showText("Figure 1.1: System Flow Diagram.");
                        cs.endText();
                    } else {
                        cs.beginText();
                        cs.setFont(fontHelv, 11);
                        cs.newLineAtOffset(50, 700);
                        cs.showText("In summary, automating document transformation requires rigorous separation of concerns.");
                        cs.endText();
                    }

                    // 4. Draw spam pagination in bottom margin (y ~ 30)
                    cs.beginText();
                    cs.setFont(fontHelv, 9);
                    cs.newLineAtOffset(290, 30);
                    cs.showText(String.valueOf(p));
                    cs.endText();
                }
            }

            doc.save(destination);
        }
    }
}

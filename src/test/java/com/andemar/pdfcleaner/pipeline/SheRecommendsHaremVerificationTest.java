package com.andemar.pdfcleaner.pipeline;

import com.andemar.pdfcleaner.analyzer.PdfAnalyzer;
import com.andemar.pdfcleaner.cleaner.PdfCleaner;
import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.epub.EpubBuilder;
import com.andemar.pdfcleaner.model.Chapter;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import com.andemar.pdfcleaner.spam.SpamDetectionEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class SheRecommendsHaremVerificationTest {

    @Test
    public void testSheRecommendsHaremFullPipeline() throws Exception {
        File pdfFile = new File("src/main/resources/static/She Recommends the Harem - Volumen 03.pdf");
        assertTrue(pdfFile.exists(), "Sample PDF file should exist at " + pdfFile.getAbsolutePath());

        CleanerConfig config = CleanerConfig.builder().build();
        CleanerPipelineOrchestrator orchestrator = new CleanerPipelineOrchestrator();

        File tempEpub = File.createTempFile("test-harem-", ".epub");
        tempEpub.deleteOnExit();

        CleanerPipelineOrchestrator.CleanResult result = orchestrator.processFile(pdfFile, tempEpub, config);

        assertNotNull(result);
        CleanedDocument doc = result.getCleanedDocument();
        SpamDetectionReport report = result.getReport();

        // 1. Verify Spam Detection:
        // 3 repeating logos across 132 pages = 396 spam images
        assertTrue(report.getSpamImagesCount() >= 390,
                "Should detect repeating logo watermarks across pages, got: " + report.getSpamImagesCount());

        // 2. Verify Legitimate Illustrations are SAFEGUARDED (10 illustrations: pages 1, 2, 3, 5, 16, 32, 49, 57, 86, 121)
        long nonSpamImagesCount = doc.getPages().stream()
                .mapToLong(p -> p.getCleanImageBlocks().size())
                .sum();
        assertEquals(10, nonSpamImagesCount,
                "All 10 authentic illustrations and covers should be safeguarded, but found: " + nonSpamImagesCount);

        // 3. Verify Chapter Structure
        List<Chapter> chapters = doc.getChapters();
        assertTrue(chapters.size() >= 7,
                "Should detect at least Front Matter, 6 main chapters, and author section, got: " + chapters.size());

        List<String> chapterTitles = chapters.stream().map(Chapter::getTitle).toList();
        System.out.println("Detected chapters: " + chapterTitles);

        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 1") || t.toUpperCase().contains("VISITA DOMICILIARIA")),
                "Chapter 1 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 2") || t.toUpperCase().contains("MES DE REFUERZO")),
                "Chapter 2 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 3") || t.toUpperCase().contains("AKIRA ES")),
                "Chapter 3 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 4") || t.toUpperCase().contains("CONTINUACIÓN") || t.toUpperCase().contains("CONTINUACION")),
                "Chapter 4 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 5") || t.toUpperCase().contains("INVITAR")),
                "Chapter 5 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("CAP 6") || t.toUpperCase().contains("NANAMI")),
                "Chapter 6 should be detected");
        assertTrue(chapterTitles.stream().anyMatch(t -> t.toUpperCase().contains("PALABRAS DEL AUTOR")),
                "Palabras del autor should be detected");

        // 4. Verify in-sequence illustrations within chapters
        // Find Chapter 1
        Chapter ch1 = chapters.stream()
                .filter(c -> c.getTitle().toUpperCase().contains("CAP 1"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, ch1.getImages().size(), "Chapter 1 should have 1 illustration (from page 16)");

        // 5. Verify Dialogues and Formats:
        // Dialogues should not be merged into single runaway lines; italics should be preserved
        boolean foundItalics = false;
        boolean foundDialogue = false;
        for (Chapter ch : chapters) {
            for (String p : ch.getParagraphs()) {
                if (p.contains("<i>") && p.contains("</i>")) {
                    foundItalics = true;
                }
                if (p.startsWith("“") || p.startsWith("\"") || p.startsWith("—")) {
                    foundDialogue = true;
                }
            }
        }
        assertTrue(foundItalics, "Italics tags (<i>...</i>) should be preserved in paragraphs");
        assertTrue(foundDialogue, "Dialogue lines starting with quotes or dashes should be preserved as paragraphs");

        // 6. Verify EPUB archive validity
        assertTrue(tempEpub.exists() && tempEpub.length() > 100_000,
                "Generated EPUB should exist and be > 100KB with images");

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(tempEpub.toPath())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        assertTrue(entryNames.contains("mimetype"));
        assertTrue(entryNames.contains("META-INF/container.xml"));
        assertTrue(entryNames.contains("OEBPS/content.opf"));
        assertTrue(entryNames.contains("OEBPS/toc.ncx"));
        assertTrue(entryNames.contains("OEBPS/nav.xhtml"));
        assertTrue(entryNames.contains("OEBPS/styles.css"));
        assertTrue(entryNames.contains("OEBPS/cover.xhtml"), "Should have dedicated cover.xhtml");

        long imageEntries = entryNames.stream().filter(n -> n.startsWith("OEBPS/images/")).count();
        assertEquals(10, imageEntries, "All 10 legitimate images should be packed into EPUB");
    }

    @Test
    public void testSheRecommendsHaremExactStructureMode() throws Exception {
        File pdfFile = new File("src/main/resources/static/She Recommends the Harem - Volumen 03.pdf");
        assertTrue(pdfFile.exists());

        CleanerConfig configSemantic = CleanerConfig.builder()
                .textStructure(com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC)
                .build();
        CleanerConfig configExact = CleanerConfig.builder()
                .textStructure(com.andemar.pdfcleaner.config.TextStructureMode.EXACT)
                .build();

        CleanerPipelineOrchestrator orchestrator = new CleanerPipelineOrchestrator();

        File tempEpubSemantic = File.createTempFile("test-harem-sem-", ".epub");
        File tempEpubExact = File.createTempFile("test-harem-exact-", ".epub");
        tempEpubSemantic.deleteOnExit();
        tempEpubExact.deleteOnExit();

        CleanerPipelineOrchestrator.CleanResult resultSemantic = orchestrator.processFile(pdfFile, tempEpubSemantic, configSemantic);
        CleanerPipelineOrchestrator.CleanResult resultExact = orchestrator.processFile(pdfFile, tempEpubExact, configExact);

        assertNotNull(resultSemantic);
        assertNotNull(resultExact);

        int totalParagraphsSemantic = resultSemantic.getCleanedDocument().getChapters().stream()
                .mapToInt(c -> c.getParagraphs().size()).sum();
        int totalParagraphsExact = resultExact.getCleanedDocument().getChapters().stream()
                .mapToInt(c -> c.getParagraphs().size()).sum();

        System.out.println("Semantic paragraphs: " + totalParagraphsSemantic + ", Exact line paragraphs: " + totalParagraphsExact);

        // Exact mode maintains original PDF text line breaks as individual paragraphs,
        // so paragraph count must be substantially higher than in semantic reflow mode
        assertTrue(totalParagraphsExact > totalParagraphsSemantic,
                "Exact structure should preserve individual line breaks resulting in more line-level blocks than merged semantic paragraphs");
        assertTrue(totalParagraphsExact >= 2000,
                "Exact structure should preserve all distinct lines from the PDF (~2000+ lines)");

        // Verify valid EPUB creation for EXACT mode
        assertTrue(tempEpubExact.exists() && tempEpubExact.length() > 100_000,
                "Exact EPUB should be generated successfully with valid content");
    }

    @Test
    public void testUserSpecificSemanticExample() {
        // User example:
        // Line 1: "Si bien uno es consciente de las condiciones para ser odiado, no conoce muy bien las"
        // Line 2: "cualidades para ser amado."
        com.andemar.pdfcleaner.model.TextBlock tb1 = com.andemar.pdfcleaner.model.TextBlock.builder()
                .text("Si bien uno es consciente de las condiciones para ser odiado, no conoce muy bien las")
                .formattedText("Si bien uno es consciente de las condiciones para ser odiado, no conoce muy bien las")
                .boundingBox(com.andemar.pdfcleaner.model.BoundingBox.builder()
                        .x(85.0f).y(100.0f).width(427.0f).height(12.0f).build())
                .fontSize(11.0f)
                .pageNumber(1)
                .build();

        com.andemar.pdfcleaner.model.TextBlock tb2 = com.andemar.pdfcleaner.model.TextBlock.builder()
                .text("cualidades para ser amado.")
                .formattedText("cualidades para ser amado.")
                .boundingBox(com.andemar.pdfcleaner.model.BoundingBox.builder()
                        .x(85.0f).y(119.0f).width(185.0f).height(12.0f).build())
                .fontSize(11.0f)
                .pageNumber(1)
                .build();

        com.andemar.pdfcleaner.model.PageModel page = com.andemar.pdfcleaner.model.PageModel.builder()
                .pageNumber(1)
                .width(600.0f)
                .height(800.0f)
                .textBlocks(List.of(tb1, tb2))
                .imageBlocks(List.of())
                .build();

        com.andemar.pdfcleaner.model.DocumentModel doc = com.andemar.pdfcleaner.model.DocumentModel.builder()
                .metadata(com.andemar.pdfcleaner.model.DocumentMetadata.builder().title("Test").build())
                .pages(List.of(page))
                .build();

        com.andemar.pdfcleaner.cleaner.PdfCleaner cleaner = new com.andemar.pdfcleaner.cleaner.PdfCleaner();

        // 1. SEMANTIC mode must merge them into 1 grammatical paragraph without an enter after "muy bien las"
        CleanerConfig semanticConfig = CleanerConfig.builder()
                .textStructure(com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC)
                .build();
        CleanedDocument semanticDoc = cleaner.clean(doc, new SpamDetectionReport(), semanticConfig);
        Chapter semChapter = semanticDoc.getChapters().get(0);
        assertEquals(1, semChapter.getParagraphs().size(), "SEMANTIC mode must merge wrapped lines into a single paragraph");
        assertEquals("Si bien uno es consciente de las condiciones para ser odiado, no conoce muy bien las cualidades para ser amado.",
                semChapter.getParagraphs().get(0),
                "Text must have no enter after 'muy bien las', and 'cualidades' must remain lowercase");

        // 2. EXACT mode must preserve the 2 distinct lines verbatim
        CleanerConfig exactConfig = CleanerConfig.builder()
                .textStructure(com.andemar.pdfcleaner.config.TextStructureMode.EXACT)
                .build();
        CleanedDocument exactDoc = cleaner.clean(doc, new SpamDetectionReport(), exactConfig);
        Chapter exactChapter = exactDoc.getChapters().get(0);
        assertEquals(2, exactChapter.getParagraphs().size(), "EXACT mode must preserve verbatim lines as separate paragraphs");
    }

    @Test
    public void testSheRecommendsHaremSemanticParagraphMerging() throws Exception {
        File pdfFile = new File("src/main/resources/static/She Recommends the Harem - Volumen 03.pdf");
        CleanerConfig configSemantic = CleanerConfig.builder()
                .textStructure(com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC)
                .build();
        CleanerPipelineOrchestrator orchestrator = new CleanerPipelineOrchestrator();
        File tempEpub = File.createTempFile("test-harem-merging-", ".epub");
        tempEpub.deleteOnExit();

        CleanerPipelineOrchestrator.CleanResult result = orchestrator.processFile(pdfFile, tempEpub, configSemantic);
        CleanedDocument doc = result.getCleanedDocument();

        // Search for the page 78 dialogue which spans across wrapped lines in the PDF
        boolean foundMergedDialogue = false;
        for (Chapter ch : doc.getChapters()) {
            for (String p : ch.getParagraphs()) {
                if (p.contains("Esto no compensa lo que hice o intenté hacer")) {
                    foundMergedDialogue = true;
                    System.out.println("Verified merged dialogue:\n" + p);
                    assertTrue(p.toLowerCase().contains("o intenté hacer... y además, legalmente..."),
                            "Dialogue should be seamlessly merged across wrapped lines");
                }
            }
        }
        assertTrue(foundMergedDialogue, "Should find seamlessly merged multi-line dialogue paragraph");
    }
}

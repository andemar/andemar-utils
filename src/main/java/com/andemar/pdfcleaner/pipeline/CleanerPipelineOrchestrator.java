package com.andemar.pdfcleaner.pipeline;

import com.andemar.pdfcleaner.analyzer.PdfAnalyzer;
import com.andemar.pdfcleaner.cleaner.PdfCleaner;
import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.epub.EpubBuilder;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import com.andemar.pdfcleaner.spam.SpamDetectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Pipeline Orchestrator coordinating:
 * Ingest -> Analyze -> Detect Spam -> Clean -> Build EPUB.
 */
public class CleanerPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CleanerPipelineOrchestrator.class);

    private final PdfAnalyzer analyzer;
    private final PdfCleaner cleaner;
    private final EpubBuilder epubBuilder;

    public CleanerPipelineOrchestrator() {
        this(new PdfAnalyzer(), new PdfCleaner(), new EpubBuilder());
    }

    public CleanerPipelineOrchestrator(PdfAnalyzer analyzer, PdfCleaner cleaner, EpubBuilder epubBuilder) {
        this.analyzer = analyzer != null ? analyzer : new PdfAnalyzer();
        this.cleaner = cleaner != null ? cleaner : new PdfCleaner();
        this.epubBuilder = epubBuilder != null ? epubBuilder : new EpubBuilder();
    }

    /**
     * Executes the pipeline on a local PDF file, writing output to outputFile.
     */
    public CleanResult processFile(File inputFile, File outputFile, CleanerConfig config) throws IOException {
        validatePdfFile(inputFile);

        long startTime = System.currentTimeMillis();
        long fileSizeKb = inputFile.length() / 1024;
        log.info("[Pipeline] Starting PDF conversion for '{}' ({} KB) | Aggressiveness: {} | Layout: {} | TextStructure: {}",
                inputFile.getName(), fileSizeKb, config.getAggressiveness(), config.getLayoutMode(), config.getTextStructure());

        // 1. Analyze
        log.info("[Pipeline: 1/4] Analyzing document structure and geometry...");
        DocumentModel docModel = analyzer.analyze(inputFile);
        applyConfigMetadata(docModel, config, inputFile != null ? inputFile.getName() : null);
        int totalTextBlocks = docModel.getPages().stream().mapToInt(p -> p.getTextBlocks().size()).sum();
        int totalImages = docModel.getPages().stream().mapToInt(p -> p.getImageBlocks().size()).sum();
        log.info("[Pipeline: 1/4] Extracted {} pages ({} text blocks, {} image blocks). Document Title: '{}'",
                docModel.getPageCount(), totalTextBlocks, totalImages, docModel.getMetadata().getTitle());

        // 2. Detect Spam
        log.info("[Pipeline: 2/4] Running repeating spam pattern detection...");
        SpamDetectionEngine spamEngine = SpamDetectionEngine.create(config);
        SpamDetectionReport report = spamEngine.process(docModel, config);
        log.info("[Pipeline: 2/4] Spam detection complete: flagged {} text blocks, {} repeating logo watermarks.",
                report.getSpamTextBlocksCount(), report.getSpamImagesCount());

        // Optional Dry-run report export
        if (config.getReportPath() != null && !config.getReportPath().isBlank()) {
            File reportFile = new File(config.getReportPath());
            Files.writeString(reportFile.toPath(), report.toFormattedSummary());
            log.info("[Pipeline] Dry-run diagnostic report written to: {}", reportFile.getAbsolutePath());
        }

        // 3. Clean & Structure
        log.info("[Pipeline: 3/4] Cleaning content and applying text structure [{}]...", config.getTextStructure());
        CleanedDocument cleanedDoc = cleaner.clean(docModel, report, config);
        int totalParagraphs = cleanedDoc.getChapters().stream().mapToInt(c -> c.getParagraphs().size()).sum();
        log.info("[Pipeline: 3/4] Text structuring complete: organized {} chapters with {} formatted paragraphs.",
                cleanedDoc.getChapters().size(), totalParagraphs);

        // 4. Build EPUB (unless pure dry-run requested without output)
        byte[] epubBytes = null;
        if (!config.isDryRun()) {
            File targetOut = outputFile != null ? outputFile : deriveDefaultOutputFile(inputFile);
            log.info("[Pipeline: 4/4] Packaging EPUB archive...");
            epubBuilder.build(cleanedDoc, targetOut, config);
            log.info("[Pipeline: 4/4] EPUB successfully generated: {} ({} KB)",
                    targetOut.getAbsolutePath(), targetOut.length() / 1024);
        } else {
            log.info("[Pipeline: 4/4] Dry-run mode: skipped EPUB archive generation.");
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Pipeline] Completed entire pipeline in {} ms.", duration);

        return new CleanResult(cleanedDoc, report, duration, outputFile);
    }

    /**
     * In-memory processing for REST API endpoints.
     */
    public CleanResult processBytes(byte[] pdfBytes, CleanerConfig config) throws IOException {
        return processBytes(pdfBytes, null, config);
    }

    /**
     * In-memory processing for REST API endpoints with original filename support.
     */
    public CleanResult processBytes(byte[] pdfBytes, String originalFilename, CleanerConfig config) throws IOException {
        if (pdfBytes == null || pdfBytes.length < 5) {
            throw new IllegalArgumentException("PDF byte stream is empty or too short.");
        }

        long startTime = System.currentTimeMillis();
        long sizeKb = pdfBytes.length / 1024;
        log.info("[Pipeline] Ingesting in-memory PDF payload ({} KB) | Filename: '{}' | Aggressiveness: {} | Layout: {} | TextStructure: {}",
                sizeKb, originalFilename, config.getAggressiveness(), config.getLayoutMode(), config.getTextStructure());

        // 1. Analyze
        log.info("[Pipeline: 1/4] Analyzing in-memory PDF pages...");
        DocumentModel docModel = analyzer.analyze(pdfBytes, originalFilename);
        applyConfigMetadata(docModel, config, originalFilename);
        int totalTextBlocks = docModel.getPages().stream().mapToInt(p -> p.getTextBlocks().size()).sum();
        int totalImages = docModel.getPages().stream().mapToInt(p -> p.getImageBlocks().size()).sum();
        log.info("[Pipeline: 1/4] Extracted {} pages ({} text blocks, {} image blocks). Document Title: '{}'",
                docModel.getPageCount(), totalTextBlocks, totalImages, docModel.getMetadata().getTitle());

        // 2. Detect Spam
        log.info("[Pipeline: 2/4] Running spam detection...");
        SpamDetectionEngine spamEngine = SpamDetectionEngine.create(config);
        SpamDetectionReport report = spamEngine.process(docModel, config);
        log.info("[Pipeline: 2/4] Spam detection complete: flagged {} text blocks, {} repeating logos.",
                report.getSpamTextBlocksCount(), report.getSpamImagesCount());

        // 3. Clean & Structure
        log.info("[Pipeline: 3/4] Cleaning and applying text structure [{}]...", config.getTextStructure());
        CleanedDocument cleanedDoc = cleaner.clean(docModel, report, config);
        int totalParagraphs = cleanedDoc.getChapters().stream().mapToInt(c -> c.getParagraphs().size()).sum();
        log.info("[Pipeline: 3/4] Text structuring complete: {} chapters, {} paragraphs.",
                cleanedDoc.getChapters().size(), totalParagraphs);

        // 4. Build EPUB
        byte[] epubBytes = null;
        if (!config.isDryRun()) {
            log.info("[Pipeline: 4/4] Packaging in-memory EPUB...");
            epubBytes = epubBuilder.buildToByteArray(cleanedDoc, config);
            log.info("[Pipeline: 4/4] In-memory EPUB generated ({} KB).", epubBytes.length / 1024);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Pipeline] Completed entire pipeline in {} ms.", duration);

        CleanResult result = new CleanResult(cleanedDoc, report, duration, null);
        result.setEpubBytes(epubBytes);
        return result;
    }

    private void applyConfigMetadata(DocumentModel docModel, CleanerConfig config, String filename) {
        if (docModel == null || docModel.getMetadata() == null) {
            return;
        }
        DocumentMetadata meta = docModel.getMetadata();

        // 1. Title precedence: explicit config title -> existing valid metadata title -> derived from filename -> fallback
        if (config != null && config.getTitle() != null && !config.getTitle().isBlank()) {
            meta.setTitle(config.getTitle().trim());
        } else if (meta.getTitle() == null || meta.getTitle().isBlank() || PdfAnalyzer.isPlaceholderTitle(meta.getTitle())) {
            String derived = PdfAnalyzer.deriveTitleFromFilename(filename);
            if (derived != null && !derived.isBlank()) {
                meta.setTitle(derived);
            } else {
                meta.setTitle("Untitled Document");
            }
        }

        // 2. Author precedence: explicit config author -> existing author
        if (config != null && config.getAuthor() != null && !config.getAuthor().isBlank()) {
            meta.setAuthor(config.getAuthor().trim());
        }
    }

    private void validatePdfFile(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("PDF input file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        if (file.length() < 10) {
            throw new IllegalArgumentException("File is empty or corrupted: " + file.getPath());
        }
    }

    public static File deriveDefaultOutputFile(File inputFile) {
        String parent = inputFile.getParent();
        String name = inputFile.getName();
        int dotIndex = name.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? name.substring(0, dotIndex) : name;
        String outName = baseName + ".cleaned.epub";
        return parent != null ? new File(parent, outName) : new File(outName);
    }

    public static class CleanResult {
        private final CleanedDocument cleanedDocument;
        private final SpamDetectionReport report;
        private final long processingTimeMs;
        private final File outputFile;
        private byte[] epubBytes;

        public CleanResult(CleanedDocument cleanedDocument, SpamDetectionReport report, long processingTimeMs, File outputFile) {
            this.cleanedDocument = cleanedDocument;
            this.report = report;
            this.processingTimeMs = processingTimeMs;
            this.outputFile = outputFile;
        }

        public CleanedDocument getCleanedDocument() {
            return cleanedDocument;
        }

        public SpamDetectionReport getReport() {
            return report;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public byte[] getEpubBytes() {
            return epubBytes;
        }

        public void setEpubBytes(byte[] epubBytes) {
            this.epubBytes = epubBytes;
        }
    }
}

package com.andemar.pdfcleaner.pipeline;

import com.andemar.pdfcleaner.analyzer.PdfAnalyzer;
import com.andemar.pdfcleaner.cleaner.PdfCleaner;
import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.epub.EpubBuilder;
import com.andemar.pdfcleaner.model.CleanedDocument;
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

        log.info("Starting PDF cleaning pipeline for: {}", inputFile.getAbsolutePath());
        long startTime = System.currentTimeMillis();

        // 1. Analyze
        DocumentModel docModel = analyzer.analyze(inputFile);
        log.info("Extracted {} pages from PDF.", docModel.getPageCount());

        // 2. Detect Spam
        SpamDetectionEngine spamEngine = SpamDetectionEngine.create(config);
        SpamDetectionReport report = spamEngine.process(docModel, config);
        log.info("Spam detection completed: {} spam text lines, {} spam logos.",
                report.getSpamTextBlocksCount(), report.getSpamImagesCount());

        // Optional Dry-run report export
        if (config.getReportPath() != null && !config.getReportPath().isBlank()) {
            File reportFile = new File(config.getReportPath());
            Files.writeString(reportFile.toPath(), report.toFormattedSummary());
            log.info("Dry-run report saved to: {}", reportFile.getAbsolutePath());
        }

        // 3. Clean
        CleanedDocument cleanedDoc = cleaner.clean(docModel, report, config);

        // 4. Build EPUB (unless pure dry-run requested without output)
        byte[] epubBytes = null;
        if (!config.isDryRun() || outputFile != null) {
            File targetOut = outputFile != null ? outputFile : deriveDefaultOutputFile(inputFile);
            epubBuilder.build(cleanedDoc, targetOut, config);
            log.info("Successfully generated clean EPUB: {} ({} bytes)",
                    targetOut.getAbsolutePath(), targetOut.length());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Pipeline completed in {} ms.", duration);

        return new CleanResult(cleanedDoc, report, duration, outputFile);
    }

    /**
     * In-memory processing for REST API endpoints.
     */
    public CleanResult processBytes(byte[] pdfBytes, CleanerConfig config) throws IOException {
        if (pdfBytes == null || pdfBytes.length < 5) {
            throw new IllegalArgumentException("PDF byte stream is empty or too short.");
        }

        long startTime = System.currentTimeMillis();
        DocumentModel docModel = analyzer.analyze(pdfBytes);
        SpamDetectionEngine spamEngine = SpamDetectionEngine.create(config);
        SpamDetectionReport report = spamEngine.process(docModel, config);
        CleanedDocument cleanedDoc = cleaner.clean(docModel, report, config);

        byte[] epubBytes = null;
        if (!config.isDryRun()) {
            epubBytes = epubBuilder.buildToByteArray(cleanedDoc, config);
        }

        long duration = System.currentTimeMillis() - startTime;
        CleanResult result = new CleanResult(cleanedDoc, report, duration, null);
        result.setEpubBytes(epubBytes);
        return result;
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

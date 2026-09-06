package com.andemar.pdfcleaner.controller;

import com.andemar.pdfcleaner.config.Aggressiveness;
import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.config.LayoutMode;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import com.andemar.pdfcleaner.pipeline.CleanerPipelineOrchestrator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Spring Boot REST Controller providing HTTP endpoints for PDF cleaning and conversion.
 */
@RestController
public class PdfCleanerController {

    private final CleanerPipelineOrchestrator orchestrator;

    public PdfCleanerController() {
        this.orchestrator = new CleanerPipelineOrchestrator();
    }

    public PdfCleanerController(CleanerPipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "pdf-cleaner",
                "version", "0.0.1-SNAPSHOT"
        );
    }

    /**
     * Primary endpoint: POST /process
     * Ingests a multipart PDF file and returns the generated .epub file download.
     */
    @PostMapping(value = "/process", produces = "application/epub+zip")
    public ResponseEntity<byte[]> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aggressiveness", defaultValue = "BALANCED") String aggressiveness,
            @RequestParam(value = "layoutMode", defaultValue = "REFLOWABLE") String layoutMode,
            @RequestParam(value = "textStructure", required = false) String textStructure,
            @RequestParam(value = "structure", required = false) String structure,
            @RequestParam(value = "whitelist", required = false) String whitelist,
            @RequestParam(value = "mlEndpointUrl", required = false) String mlEndpointUrl,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        CleanerConfig config = buildConfig(aggressiveness, layoutMode, textStructure, structure, whitelist, mlEndpointUrl, title, author, false);
        CleanerPipelineOrchestrator.CleanResult result = orchestrator.processBytes(file.getBytes(), file.getOriginalFilename(), config);

        String originalName = file.getOriginalFilename();
        String outName = "cleaned.epub";
        if (originalName != null && originalName.contains(".")) {
            outName = originalName.substring(0, originalName.lastIndexOf('.')) + ".cleaned.epub";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + outName + "\"")
                .contentType(MediaType.parseMediaType("application/epub+zip"))
                .body(result.getEpubBytes());
    }

    /**
     * Dry-run endpoint: POST /api/dry-run
     * Returns JSON spam detection metrics without generating an EPUB binary.
     */
    @PostMapping(value = "/api/dry-run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SpamDetectionReport> dryRun(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aggressiveness", defaultValue = "BALANCED") String aggressiveness,
            @RequestParam(value = "textStructure", required = false) String textStructure,
            @RequestParam(value = "structure", required = false) String structure,
            @RequestParam(value = "whitelist", required = false) String whitelist,
            @RequestParam(value = "mlEndpointUrl", required = false) String mlEndpointUrl,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        CleanerConfig config = buildConfig(aggressiveness, "REFLOWABLE", textStructure, structure, whitelist, mlEndpointUrl, title, author, true);
        CleanerPipelineOrchestrator.CleanResult result = orchestrator.processBytes(file.getBytes(), file.getOriginalFilename(), config);

        return ResponseEntity.ok(result.getReport());
    }

    private CleanerConfig buildConfig(String aggStr, String modeStr, String textStructure, String structure, String whitelist, String mlUrl, String title, String author, boolean dryRun) {
        Aggressiveness agg = Aggressiveness.BALANCED;
        try {
            agg = Aggressiveness.valueOf(aggStr.toUpperCase());
        } catch (Exception ignored) {}

        LayoutMode mode = LayoutMode.REFLOWABLE;
        if (modeStr != null && modeStr.toUpperCase().contains("FIXED")) {
            mode = LayoutMode.FIXED_ISH;
        }

        com.andemar.pdfcleaner.config.TextStructureMode textStruct = com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC;
        String structChoice = textStructure != null ? textStructure : structure;
        if (structChoice != null && (structChoice.equalsIgnoreCase("EXACT") || structChoice.equalsIgnoreCase("RAW"))) {
            textStruct = com.andemar.pdfcleaner.config.TextStructureMode.EXACT;
        }

        CleanerConfig config = CleanerConfig.builder()
                .aggressiveness(agg)
                .layoutMode(mode)
                .textStructure(textStruct)
                .mlEndpointUrl(mlUrl)
                .title(title)
                .author(author)
                .dryRun(dryRun)
                .build();

        if (whitelist != null && !whitelist.isBlank()) {
            for (String phrase : whitelist.split(",")) {
                config.addWhitelistPhrase(phrase.trim());
            }
        }
        return config;
    }
}

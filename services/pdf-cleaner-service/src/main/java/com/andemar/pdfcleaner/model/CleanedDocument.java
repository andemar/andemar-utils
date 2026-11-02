package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleaned document model containing structured chapters, cleaned pages, and diagnostics.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CleanedDocument {
    private DocumentMetadata metadata;
    @Builder.Default
    private List<CleanedPage> pages = new ArrayList<>();
    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();
    private SpamDetectionReport report;
}

package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata extracted from the PDF or provided by user.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentMetadata {
    @Builder.Default
    private String title = "Untitled Document";
    @Builder.Default
    private String author = "Unknown";
    private String subject;
    private String producer;
    @Builder.Default
    private String language = "en";
    private int pageCount;
}

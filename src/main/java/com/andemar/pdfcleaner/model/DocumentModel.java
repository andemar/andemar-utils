package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured in-memory representation of the parsed PDF document.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentModel {
    private DocumentMetadata metadata;
    @Builder.Default
    private List<PageModel> pages = new ArrayList<>();

    public int getPageCount() {
        return pages != null ? pages.size() : 0;
    }
}

package com.andemar.pdfcleaner.config;

/**
 * Determines whether to preserve the exact line structure and formatting of the PDF
 * or apply intelligent semantic language structuring (dialogue handling, paragraph breaks,
 * commas, semicolons, capital letters, and punctuation normalization).
 */
public enum TextStructureMode {
    /**
     * Applies intelligent semantic structuring:
     * - Reconstructs logical paragraphs across line wraps.
     * - Formats dialogue dashes and quotation marks.
     * - Normalizes commas, semicolons, colons, and ellipses.
     * - Enforces capitalization at sentence boundaries and dialogue turns.
     */
    SEMANTIC,

    /**
     * Preserves the verbatim line structure and exact text layout of the original PDF:
     * - Retains original line breaks as distinct blocks.
     * - Preserves verbatim punctuation and casing without restructuring.
     */
    EXACT
}

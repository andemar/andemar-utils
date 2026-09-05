package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleaned version of a page containing only legitimate content text and book figures.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CleanedPage {
    private int pageNumber;
    private float width;
    private float height;
    @Builder.Default
    private List<TextBlock> cleanTextBlocks = new ArrayList<>();
    @Builder.Default
    private List<ImageBlock> cleanImageBlocks = new ArrayList<>();
}

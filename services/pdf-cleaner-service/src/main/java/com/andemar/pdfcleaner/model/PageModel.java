package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model of a single PDF page with dimensions, text blocks, and images.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageModel {
    private int pageNumber;
    private float width;
    private float height;
    @Builder.Default
    private List<TextBlock> textBlocks = new ArrayList<>();
    @Builder.Default
    private List<ImageBlock> imageBlocks = new ArrayList<>();
}

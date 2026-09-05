package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model of a chapter or section in the book, containing ordered paragraphs and illustrations.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Chapter {
    private int chapterIndex;
    private String title;
    @Builder.Default
    private List<String> paragraphs = new ArrayList<>();
    @Builder.Default
    private List<ImageBlock> images = new ArrayList<>();
    private int startPage;
}

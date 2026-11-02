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
    @Builder.Default
    private List<Element> elements = new ArrayList<>();
    private int startPage;

    public void addParagraph(String paragraphHtml) {
        if (paragraphHtml != null && !paragraphHtml.isBlank()) {
            paragraphs.add(paragraphHtml);
            elements.add(new ParagraphElement(paragraphHtml, false));
        }
    }

    public void addImage(ImageBlock imageBlock) {
        if (imageBlock != null) {
            images.add(imageBlock);
            elements.add(new ImageElement(imageBlock));
        }
    }

    public sealed interface Element permits ParagraphElement, ImageElement {}

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class ParagraphElement implements Element {
        private String text;
        private boolean heading;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class ImageElement implements Element {
        private ImageBlock image;
    }
}

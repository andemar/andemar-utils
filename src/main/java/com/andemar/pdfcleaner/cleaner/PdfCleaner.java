package com.andemar.pdfcleaner.cleaner;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.Chapter;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.CleanedPage;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.SpamDetectionReport;
import com.andemar.pdfcleaner.model.TextBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Transforms the raw DocumentModel into a CleanedDocument:
 * - Omits all flagged spam text and spam images.
 * - Reconstructs paragraphs, reading flow, and dialogue formatting.
 * - Detects chapter and section headings in multiple languages.
 * - Associates non-spam book illustrations with chapters in exact reading order.
 */
public class PdfCleaner {

    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile(
            "^((chapter|cap[ií]tulo|cap\\.?|part|parte|section|secci[oó]n)\\s+(\\d+|[ivxlcdm]+)" +
            "|(prologue|pr[oó]logo|epilogue|ep[ií]logo|introduction|introducci[oó]n|preface|prefacio" +
            "|appendix|ap[eé]ndice|palabras del autor|acerca del autor|posfacio|afterword))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOC_LINE_PATTERN = Pattern.compile(
            ".*(\\s+|\\.{2,}|…)\\d{1,4}$"
    );

    private static final String DIALOGUE_OPENERS = "“\"‘«—–-";
    private static final String SENTENCE_CLOSERS = ".?!:”\"’»…";

    private final SemanticTextProcessor semanticProcessor = new SemanticTextProcessor();

    public CleanedDocument clean(DocumentModel rawDocument, SpamDetectionReport report, CleanerConfig config) {
        List<CleanedPage> cleanedPages = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();

        Chapter currentChapter = Chapter.builder()
                .chapterIndex(1)
                .title("Front Matter")
                .startPage(1)
                .build();
        chapters.add(currentChapter);

        StringBuilder paragraphBuffer = new StringBuilder();
        String lastRawLine = null;
        float lastLineY = -1;
        float estimatedLineHeight = 16.0f;

        for (PageModel rawPage : rawDocument.getPages()) {
            List<TextBlock> cleanTextBlocks = new ArrayList<>();
            List<ImageBlock> cleanImageBlocks = new ArrayList<>();

            // 1. Collect non-spam text blocks sorted top-to-bottom, left-to-right
            List<TextBlock> sortedText = new ArrayList<>(rawPage.getTextBlocks());
            sortedText.sort(Comparator.comparingDouble((TextBlock tb) -> tb.getBoundingBox().getY())
                    .thenComparingDouble(tb -> tb.getBoundingBox().getX()));

            for (TextBlock tb : sortedText) {
                if (!tb.isSpam()) {
                    cleanTextBlocks.add(tb);
                }
            }

            // 2. Collect non-spam images sorted top-to-bottom
            List<ImageBlock> sortedImages = new ArrayList<>(rawPage.getImageBlocks());
            sortedImages.sort(Comparator.comparingDouble(img -> img.getBoundingBox().getY()));

            for (ImageBlock img : sortedImages) {
                if (!img.isSpam()) {
                    cleanImageBlocks.add(img);
                }
            }

            cleanedPages.add(CleanedPage.builder()
                    .pageNumber(rawPage.getPageNumber())
                    .width(rawPage.getWidth())
                    .height(rawPage.getHeight())
                    .cleanTextBlocks(cleanTextBlocks)
                    .cleanImageBlocks(cleanImageBlocks)
                    .build());

            // 3. Process items in vertical order on this page (text and illustrations in reading order)
            List<PageItem> pageItems = new ArrayList<>();
            for (TextBlock tb : cleanTextBlocks) {
                pageItems.add(new PageItem(tb));
            }
            for (ImageBlock img : cleanImageBlocks) {
                pageItems.add(new PageItem(img));
            }
            pageItems.sort(Comparator.comparingDouble(PageItem::getY));

            for (PageItem item : pageItems) {
                if (item.isImage()) {
                    // Flush accumulated paragraph before placing image
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                    lastRawLine = null;
                    lastLineY = -1;

                    currentChapter.addImage(item.image);
                    continue;
                }

                TextBlock tb = item.textBlock;
                String cleanLine = tb.getCleanText();
                String formattedLine = tb.getFormattedText();
                if (cleanLine.isBlank()) continue;

                float currentY = tb.getBoundingBox().getY();
                float currentH = tb.getBoundingBox().getHeight();
                if (currentH > 4.0f && currentH < 40.0f) {
                    estimatedLineHeight = currentH * 1.5f;
                }

                boolean isHeading = isChapterHeading(tb);

                if (isHeading) {
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                    lastRawLine = null;
                    lastLineY = -1;

                    String headingTitle = cleanLine;
                    if (config.getTextStructure() == com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC) {
                        headingTitle = semanticProcessor.refineParagraph(cleanLine);
                    }

                    // If current chapter already has elements, create a new chapter
                    if (!currentChapter.getElements().isEmpty() || currentChapter.getChapterIndex() == 1) {
                        if (!currentChapter.getElements().isEmpty()) {
                            currentChapter = Chapter.builder()
                                    .chapterIndex(chapters.size() + 1)
                                    .title(headingTitle)
                                    .startPage(rawPage.getPageNumber())
                                    .build();
                            chapters.add(currentChapter);
                        } else {
                            currentChapter.setTitle(headingTitle);
                        }
                    }
                    continue;
                }

                if (config.getTextStructure() == com.andemar.pdfcleaner.config.TextStructureMode.EXACT) {
                    // In EXACT mode, do not merge across lines; preserve verbatim line formatting
                    currentChapter.addParagraph(formattedLine);
                    lastRawLine = cleanLine;
                    lastLineY = currentY;
                    continue;
                }

                // In SEMANTIC mode: check if we should flush paragraph before appending this line
                float deltaY = (lastLineY >= 0) ? (currentY - lastLineY) : 0;
                if (paragraphBuffer.length() > 0 && shouldFlushParagraph(lastRawLine, cleanLine, deltaY, estimatedLineHeight)) {
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                }

                // Append line to paragraph buffer
                if (paragraphBuffer.length() > 0) {
                    char lastChar = paragraphBuffer.charAt(paragraphBuffer.length() - 1);
                    if (lastChar == '-') {
                        // Word hyphenated across line break
                        paragraphBuffer.setLength(paragraphBuffer.length() - 1);
                        paragraphBuffer.append(formattedLine);
                    } else {
                        paragraphBuffer.append(" ").append(formattedLine);
                    }
                } else {
                    paragraphBuffer.append(formattedLine);
                }

                lastRawLine = cleanLine;
                lastLineY = currentY;
            }

            // In fixed-ish mode or if page had illustrations only, flush paragraphs
            if (config.getLayoutMode() == com.andemar.pdfcleaner.config.LayoutMode.FIXED_ISH) {
                flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                lastRawLine = null;
                lastLineY = -1;
            }
        }

        // Flush any remaining text in buffer
        flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());

        // Clean up initial chapter if empty
        if (chapters.size() > 1 && chapters.get(0).getElements().isEmpty()) {
            chapters.remove(0);
        } else if (!chapters.isEmpty() && chapters.get(0).getTitle().equals("Front Matter") && chapters.size() > 1) {
            // Keep front matter title descriptive
            chapters.get(0).setTitle("Front Matter & Illustrations");
        }

        return CleanedDocument.builder()
                .metadata(rawDocument.getMetadata())
                .pages(cleanedPages)
                .chapters(chapters)
                .report(report)
                .build();
    }

    private boolean isChapterHeading(TextBlock tb) {
        String text = tb.getCleanText();
        if (text == null || text.isBlank() || text.length() > 100) {
            return false;
        }

        // Exclude Table of Contents lines that end with page numbers (e.g. "... 021", "006")
        if (TOC_LINE_PATTERN.matcher(text).matches()) {
            return false;
        }

        if (CHAPTER_HEADING_PATTERN.matcher(text).find()) {
            return true;
        }

        // Prominent large font heading (e.g. >= 14pt) with short text
        if (tb.getFontSize() >= 13.8f && text.length() < 70) {
            if (!text.endsWith(".") || text.length() < 40) {
                return true;
            }
        }

        // Bold line with font >= 12.5pt and short length
        if (tb.isBold() && tb.getFontSize() >= 12.5f && text.length() < 50) {
            return true;
        }

        return false;
    }

    private boolean shouldFlushParagraph(String prevRaw, String nextRaw, float deltaY, float lineHeight) {
        if (prevRaw == null || prevRaw.isBlank()) {
            return false;
        }
        String prev = prevRaw.trim();
        String next = nextRaw != null ? nextRaw.trim() : "";

        // If next line starts with a dialogue opener, always start a new dialogue paragraph
        if (!next.isEmpty() && DIALOGUE_OPENERS.indexOf(next.charAt(0)) >= 0) {
            return true;
        }

        // If there is significant vertical spacing between lines (blank line in PDF)
        if (lineHeight > 0 && deltaY >= lineHeight * 1.35f) {
            return true;
        }

        // If previous line ends with sentence / dialogue closer
        char lastChar = prev.charAt(prev.length() - 1);
        if (SENTENCE_CLOSERS.indexOf(lastChar) >= 0 || prev.endsWith("...")) {
            if (!next.isEmpty()) {
                char firstChar = next.charAt(0);
                // If next line starts with an uppercase letter or quote
                if (Character.isUpperCase(firstChar) || DIALOGUE_OPENERS.indexOf(firstChar) >= 0) {
                    if (prev.length() < 60 || deltaY >= lineHeight * 1.15f) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void flushParagraph(StringBuilder buffer, Chapter chapter, com.andemar.pdfcleaner.config.TextStructureMode structureMode) {
        if (buffer.length() > 0) {
            String p = buffer.toString().trim();
            if (!p.isBlank()) {
                if (structureMode == com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC) {
                    p = semanticProcessor.refineParagraph(p);
                }
                if (!p.isBlank()) {
                    chapter.addParagraph(p);
                }
            }
            buffer.setLength(0);
        }
    }

    private static class PageItem {
        final TextBlock textBlock;
        final ImageBlock image;

        PageItem(TextBlock textBlock) {
            this.textBlock = textBlock;
            this.image = null;
        }

        PageItem(ImageBlock image) {
            this.textBlock = null;
            this.image = image;
        }

        boolean isImage() {
            return image != null;
        }

        float getY() {
            if (image != null) return image.getBoundingBox().getY();
            if (textBlock != null) return textBlock.getBoundingBox().getY();
            return 0;
        }
    }
}

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
 * - Reconstructs paragraphs and reading flow.
 * - Detects chapter and section headings.
 * - Associates non-spam book illustrations and diagrams with chapters.
 */
public class PdfCleaner {

    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile(
            "^(chapter\\s+(\\d+|[ivxlcdm]+)|part\\s+(\\d+|[ivxlcdm]+)|section\\s+\\d+|prologue|epilogue|introduction|preface|appendix)",
            Pattern.CASE_INSENSITIVE
    );

    public CleanedDocument clean(DocumentModel rawDocument, SpamDetectionReport report, CleanerConfig config) {
        List<CleanedPage> cleanedPages = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();

        Chapter currentChapter = Chapter.builder()
                .chapterIndex(1)
                .title("Prologue")
                .startPage(1)
                .build();
        chapters.add(currentChapter);

        StringBuilder paragraphBuffer = new StringBuilder();

        for (PageModel rawPage : rawDocument.getPages()) {
            List<TextBlock> cleanTextBlocks = new ArrayList<>();
            List<ImageBlock> cleanImageBlocks = new ArrayList<>();

            // 1. Filter legitimate text blocks sorted in reading order (top to bottom, left to right)
            List<TextBlock> sortedText = new ArrayList<>(rawPage.getTextBlocks());
            sortedText.sort(Comparator.comparingDouble((TextBlock tb) -> tb.getBoundingBox().getY())
                    .thenComparingDouble(tb -> tb.getBoundingBox().getX()));

            for (TextBlock tb : sortedText) {
                if (!tb.isSpam()) {
                    cleanTextBlocks.add(tb);
                }
            }

            // 2. Filter legitimate images
            for (ImageBlock img : rawPage.getImageBlocks()) {
                if (!img.isSpam()) {
                    cleanImageBlocks.add(img);
                    // Associate image with current active chapter
                    currentChapter.getImages().add(img);
                }
            }

            cleanedPages.add(CleanedPage.builder()
                    .pageNumber(rawPage.getPageNumber())
                    .width(rawPage.getWidth())
                    .height(rawPage.getHeight())
                    .cleanTextBlocks(cleanTextBlocks)
                    .cleanImageBlocks(cleanImageBlocks)
                    .build());

            // 3. Process clean text into structured chapters and paragraphs
            for (int i = 0; i < cleanTextBlocks.size(); i++) {
                TextBlock tb = cleanTextBlocks.get(i);
                String line = tb.getCleanText();
                if (line.isBlank()) continue;

                boolean isHeading = isChapterHeading(tb);

                if (isHeading) {
                    // Flush accumulated paragraph
                    flushParagraph(paragraphBuffer, currentChapter);

                    // Create new chapter if this chapter already has content
                    if (!currentChapter.getParagraphs().isEmpty() || currentChapter.getChapterIndex() == 1) {
                        if (!currentChapter.getParagraphs().isEmpty()) {
                            currentChapter = Chapter.builder()
                                    .chapterIndex(chapters.size() + 1)
                                    .title(line)
                                    .startPage(rawPage.getPageNumber())
                                    .build();
                            chapters.add(currentChapter);
                        } else {
                            currentChapter.setTitle(line);
                        }
                    }
                    continue;
                }

                // Append to paragraph buffer
                if (paragraphBuffer.length() > 0) {
                    char lastChar = paragraphBuffer.charAt(paragraphBuffer.length() - 1);
                    if (lastChar == '-') {
                        // Word hyphenated across line break
                        paragraphBuffer.setLength(paragraphBuffer.length() - 1);
                        paragraphBuffer.append(line);
                    } else if (lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '"' || lastChar == ':') {
                        // Likely paragraph break if line starts indented or with space
                        flushParagraph(paragraphBuffer, currentChapter);
                        paragraphBuffer.append(line);
                    } else {
                        paragraphBuffer.append(" ").append(line);
                    }
                } else {
                    paragraphBuffer.append(line);
                }
            }

            // At page break in fixed-ish mode or between chapters, flush paragraphs
            if (config.getLayoutMode() == com.andemar.pdfcleaner.config.LayoutMode.FIXED_ISH) {
                flushParagraph(paragraphBuffer, currentChapter);
            }
        }

        // Flush any remaining text in the buffer
        flushParagraph(paragraphBuffer, currentChapter);

        // Remove empty initial chapter if subsequent chapters exist
        if (chapters.size() > 1 && chapters.get(0).getParagraphs().isEmpty() && chapters.get(0).getImages().isEmpty()) {
            chapters.remove(0);
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
        if (CHAPTER_HEADING_PATTERN.matcher(text).find()) {
            return true;
        }
        // Prominent large or bold line with short title length
        if (tb.isBold() && tb.getFontSize() >= 14.0f && text.length() < 60) {
            return true;
        }
        return false;
    }

    private void flushParagraph(StringBuilder buffer, Chapter chapter) {
        if (buffer.length() > 0) {
            String p = buffer.toString().trim();
            if (!p.isBlank()) {
                chapter.getParagraphs().add(p);
            }
            buffer.setLength(0);
        }
    }
}

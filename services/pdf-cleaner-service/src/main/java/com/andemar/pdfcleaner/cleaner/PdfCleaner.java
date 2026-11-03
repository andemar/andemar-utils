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
        TextBlock lastTextBlock = null;
        float lastLineY = -1;

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

            PageMetrics pageMetrics = calculatePageMetrics(cleanTextBlocks);
            boolean isFirstItemOnPage = true;

            for (PageItem item : pageItems) {
                if (item.isImage()) {
                    // Flush accumulated paragraph before placing image
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                    lastRawLine = null;
                    lastTextBlock = null;
                    lastLineY = -1;

                    currentChapter.addImage(item.image);
                    continue;
                }

                TextBlock tb = item.textBlock;
                String cleanLine = tb.getCleanText();
                String formattedLine = tb.getFormattedText();
                if (cleanLine.isBlank()) continue;

                float currentY = tb.getBoundingBox().getY();
                boolean isHeading = isChapterHeading(tb);

                if (isHeading) {
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                    lastRawLine = null;
                    lastTextBlock = null;
                    lastLineY = -1;

                    String headingTitle = cleanLine;
                    String normalizedTitle = semanticProcessor.normalizeSpacedLetters(cleanLine);
                    if (CHAPTER_HEADING_PATTERN.matcher(normalizedTitle).find() || tb.getFontSize() >= 13.8f) {
                        headingTitle = normalizedTitle;
                    }
                    if (config.getTextStructure() == com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC) {
                        headingTitle = semanticProcessor.refineParagraph(headingTitle);
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
                    lastTextBlock = tb;
                    lastLineY = currentY;
                    isFirstItemOnPage = false;
                    continue;
                }

                // In SEMANTIC mode: check if we should flush paragraph before appending this line
                float deltaY = (lastLineY >= 0) ? (currentY - lastLineY) : 0;
                boolean isNewPage = isFirstItemOnPage && lastRawLine != null;

                if (paragraphBuffer.length() > 0 && shouldFlushParagraph(
                        lastRawLine,
                        lastTextBlock,
                        cleanLine,
                        tb,
                        deltaY,
                        pageMetrics,
                        isNewPage
                )) {
                    flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                }

                // Append line using semantic line joining (handling word hyphenation across lines)
                semanticProcessor.joinLines(paragraphBuffer, formattedLine);

                lastRawLine = cleanLine;
                lastTextBlock = tb;
                lastLineY = currentY;
                isFirstItemOnPage = false;
            }

            // In fixed-ish mode, flush paragraphs per page
            if (config.getLayoutMode() == com.andemar.pdfcleaner.config.LayoutMode.FIXED_ISH) {
                flushParagraph(paragraphBuffer, currentChapter, config.getTextStructure());
                lastRawLine = null;
                lastTextBlock = null;
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

        String normalized = semanticProcessor.normalizeSpacedLetters(text);
        if (CHAPTER_HEADING_PATTERN.matcher(text).find() || CHAPTER_HEADING_PATTERN.matcher(normalized).find()) {
            return true;
        }

        // Prominent large font heading (e.g. >= 13.8pt) with short text
        if (tb.getFontSize() >= 13.8f && (text.length() < 70 || normalized.length() < 70)) {
            if (!text.endsWith(".") || text.length() < 40) {
                return true;
            }
        }

        // Bold line with font >= 12.5pt and short length
        if (tb.isBold() && tb.getFontSize() >= 12.5f && (text.length() < 50 || normalized.length() < 50)) {
            return true;
        }

        return false;
    }

    private PageMetrics calculatePageMetrics(List<TextBlock> textBlocks) {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return new PageMetrics(50.0f, 550.0f, 500.0f, 19.0f);
        }

        List<Float> xCoords = new ArrayList<>();
        List<Float> rightCoords = new ArrayList<>();
        List<Float> linePitches = new ArrayList<>();

        TextBlock prev = null;
        for (TextBlock tb : textBlocks) {
            String clean = tb.getCleanText();
            // Only consider substantial body lines (not tiny numbers or dividers)
            if (clean.length() >= 15 && !semanticProcessor.isSceneDivider(clean)) {
                xCoords.add(tb.getBoundingBox().getX());
                rightCoords.add(tb.getBoundingBox().getRight());
            }

            if (prev != null) {
                float dy = tb.getBoundingBox().getY() - prev.getBoundingBox().getY();
                if (dy >= 8.0f && dy <= 45.0f) {
                    linePitches.add(dy);
                }
            }
            prev = tb;
        }

        float leftMargin = 50.0f;
        if (!xCoords.isEmpty()) {
            xCoords.sort(Float::compare);
            int idx = Math.min((int) (xCoords.size() * 0.10f), xCoords.size() - 1);
            leftMargin = xCoords.get(idx);
        }

        float maxRight = 550.0f;
        if (!rightCoords.isEmpty()) {
            rightCoords.sort(Float::compare);
            int idx = Math.min((int) (rightCoords.size() * 0.90f), rightCoords.size() - 1);
            maxRight = rightCoords.get(idx);
        }

        float maxLineWidth = Math.max(100.0f, maxRight - leftMargin);

        float standardPitch = 19.0f;
        if (!linePitches.isEmpty()) {
            linePitches.sort(Float::compare);
            standardPitch = linePitches.get(linePitches.size() / 2); // Median pitch
        }

        return new PageMetrics(leftMargin, maxRight, maxLineWidth, standardPitch);
    }

    private boolean shouldFlushParagraph(
            String prevClean,
            TextBlock prevTb,
            String nextClean,
            TextBlock nextTb,
            float deltaY,
            PageMetrics metrics,
            boolean isNewPage
    ) {
        if (prevClean == null || prevClean.isBlank()) {
            return false;
        }
        if (nextClean == null || nextClean.isBlank()) {
            return false;
        }

        // 1. Scene dividers always start their own paragraph
        if (semanticProcessor.isSceneDivider(nextClean) || semanticProcessor.isSceneDivider(prevClean)) {
            return true;
        }

        // 2. Dialogue opener on next line: dialogue turns start on a new paragraph
        if (semanticProcessor.isDialogueOpener(nextClean)) {
            return true;
        }

        // 3. Next line starts with lowercase letter: must be syntactic continuation of the previous sentence
        if (!nextClean.isEmpty() && Character.isLowerCase(nextClean.charAt(0))) {
            return false;
        }

        // 4. If previous line did NOT end with terminal punctuation,
        // the sentence is grammatically incomplete and must not be cut off.
        if (!semanticProcessor.isSentenceTerminated(prevClean)) {
            return false;
        }

        // 5. Previous line DOES end with terminal punctuation.
        // Check typographical paragraph break signals:

        // A. Paragraph first-line indentation:
        if (nextTb != null && metrics != null && nextTb.getBoundingBox().getX() >= metrics.leftMargin + 7.0f) {
            return true;
        }

        // B. Vertical gap (blank line) between lines on the same page:
        if (!isNewPage && metrics != null && deltaY >= metrics.standardLinePitch * 1.30f) {
            return true;
        }

        // C. Short line ending a justified paragraph:
        if (prevTb != null && metrics != null && prevTb.getBoundingBox().getWidth() < metrics.maxLineWidth * 0.82f) {
            char firstChar = nextClean.charAt(0);
            if (Character.isUpperCase(firstChar) || firstChar == '¿' || firstChar == '¡' || firstChar == '“') {
                return true;
            }
        }

        // D. Dialogue finished: previous was dialogue and next line is narration:
        if (semanticProcessor.isDialogueOpener(prevClean)) {
            char firstChar = nextClean.charAt(0);
            if (Character.isUpperCase(firstChar) || firstChar == '¿' || firstChar == '¡') {
                return true;
            }
        }

        // E. Page boundary: previous line ended with terminal punctuation at page bottom
        // and next line on new page starts with uppercase or dialogue opener:
        if (isNewPage) {
            char firstChar = nextClean.charAt(0);
            if (Character.isUpperCase(firstChar) || firstChar == '¿' || firstChar == '¡') {
                return true;
            }
        }

        return false;
    }

    private static class PageMetrics {
        final float leftMargin;
        final float maxRight;
        final float maxLineWidth;
        final float standardLinePitch;

        PageMetrics(float leftMargin, float maxRight, float maxLineWidth, float standardLinePitch) {
            this.leftMargin = leftMargin;
            this.maxRight = maxRight;
            this.maxLineWidth = maxLineWidth;
            this.standardLinePitch = standardLinePitch;
        }
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

package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.model.BoundingBox;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.TextBlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects spam-like text blocks (repeated headers, running headers with page numbers,
 * isolated pagination numbers, slogans, copyright notices, and watermarks).
 */
public class TextSpamDetector {

    private static final List<String> BRANDING_KEYWORDS = List.of(
            "copyright", "all rights reserved", "downloaded from", "prepared for",
            "licensed to", "scanned by", "www.", "http://", "https://",
            "confidential", "internal use only", "do not distribute", "watermark",
            "free ebook", "ebook edition", "retail epub", "converted by"
    );

    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile(
            "^(\\d{1,4}|[ivxlcdm]{1,8}|page\\s+\\d+(\\s+of\\s+\\d+)?|-\\s*\\d+\\s*-|\\d+\\s*/\\s*\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    public void detect(DocumentModel document, CleanerConfig config) {
        if (document == null || document.getPages() == null || document.getPages().isEmpty()) {
            return;
        }

        int totalPages = document.getPageCount();
        float headerMargin = config.getEffectiveHeaderMargin();
        float footerMargin = config.getEffectiveFooterMargin();
        float frequencyThreshold = config.getEffectiveTextFrequency();

        // 1. Gather occurrences of normalized text across pages
        // normalized template -> set of page numbers
        Map<String, Set<Integer>> templatePageMap = new HashMap<>();
        // exact normalized text -> set of page numbers
        Map<String, Set<Integer>> exactPageMap = new HashMap<>();

        for (PageModel page : document.getPages()) {
            float pageHeight = page.getHeight();
            for (TextBlock block : page.getTextBlocks()) {
                String rawText = block.getCleanText();
                if (rawText.isBlank()) continue;

                if (isWhitelisted(rawText, config.getWhitelistPhrases())) {
                    continue;
                }

                BoundingBox bbox = block.getBoundingBox();
                boolean inMargin = bbox.isInTopMargin(pageHeight, headerMargin) ||
                                   bbox.isInBottomMargin(pageHeight, footerMargin);

                if (inMargin) {
                    String exact = rawText.toLowerCase(Locale.ROOT);
                    String template = normalizeTemplate(rawText);

                    exactPageMap.computeIfAbsent(exact, k -> new HashSet<>()).add(page.getPageNumber());
                    templatePageMap.computeIfAbsent(template, k -> new HashSet<>()).add(page.getPageNumber());
                }
            }
        }

        // 2. Identify repeating templates and texts exceeding threshold
        Set<String> repeatingTemplates = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : templatePageMap.entrySet()) {
            double freq = (double) entry.getValue().size() / totalPages;
            // If document has >= 2 pages and template frequency exceeds threshold, or in multi-page doc repeats on >= 2 pages and > 40%
            if (totalPages >= 2 && (freq >= frequencyThreshold || (totalPages >= 3 && freq >= 0.50))) {
                repeatingTemplates.add(entry.getKey());
            }
        }

        Set<String> repeatingExacts = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : exactPageMap.entrySet()) {
            double freq = (double) entry.getValue().size() / totalPages;
            if (totalPages >= 2 && freq >= frequencyThreshold) {
                repeatingExacts.add(entry.getKey());
            }
        }

        // 3. Mark blocks
        for (PageModel page : document.getPages()) {
            float pageHeight = page.getHeight();

            for (TextBlock block : page.getTextBlocks()) {
                String text = block.getCleanText();
                if (text.isBlank()) continue;

                if (isWhitelisted(text, config.getWhitelistPhrases())) {
                    continue;
                }

                BoundingBox bbox = block.getBoundingBox();
                boolean isTop = bbox.isInTopMargin(pageHeight, headerMargin);
                boolean isBottom = bbox.isInBottomMargin(pageHeight, footerMargin);

                if (!isTop && !isBottom) {
                    // Check if branding slogan repeats even outside margins
                    if (containsBrandingKeyword(text)) {
                        String exact = text.toLowerCase(Locale.ROOT);
                        if (exactPageMap.getOrDefault(exact, Collections.emptySet()).size() >= Math.max(2, totalPages * 0.3)) {
                            block.markSpam("BRANDING_KEYWORD_REPEATING");
                        }
                    }
                    continue;
                }

                String lower = text.toLowerCase(Locale.ROOT);
                String template = normalizeTemplate(text);

                // Heuristic A: Repeating header/footer template across pages
                if (repeatingTemplates.contains(template) || repeatingExacts.contains(lower)) {
                    block.markSpam(isTop ? "REPEATING_HEADER" : "REPEATING_FOOTER");
                    continue;
                }

                // Heuristic B: Isolated page numbers in margins
                if (isPageNumber(text)) {
                    // If multi-page doc or at least 2 pages
                    block.markSpam("PAGE_NUMBER_MARGIN");
                    continue;
                }

                // Heuristic C: Branding keywords in margins
                if (containsBrandingKeyword(text)) {
                    block.markSpam("BRANDING_KEYWORD_MARGIN");
                    continue;
                }

                // Heuristic D: Ultra-short margin fragments at extreme margins in documents with >= 3 pages
                if (totalPages >= 3 && text.length() <= 3 && (isTop || isBottom)) {
                    block.markSpam("MARGIN_NUMERIC_OR_SYMBOL");
                }
            }
        }
    }

    private String normalizeTemplate(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        // Replace digit sequences with '#'
        s = s.replaceAll("\\d+", "#");
        // Collapse spaces
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private boolean isPageNumber(String text) {
        String clean = text.trim();
        return PAGE_NUMBER_PATTERN.matcher(clean).matches();
    }

    private boolean containsBrandingKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : BRANDING_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWhitelisted(String text, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String wl : whitelist) {
            if (wl != null && !wl.isBlank()) {
                if (lower.contains(wl.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}

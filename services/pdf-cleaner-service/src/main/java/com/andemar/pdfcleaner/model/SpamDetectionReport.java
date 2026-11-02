package com.andemar.pdfcleaner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Report detailing which text lines and images were flagged as spam and why.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SpamDetectionReport {
    private int totalPages;
    private int totalTextBlocks;
    private int spamTextBlocksCount;
    private int totalImages;
    private int spamImagesCount;
    @Builder.Default
    private List<String> flaggedSpamPhrases = new ArrayList<>();
    @Builder.Default
    private List<String> flaggedSpamImages = new ArrayList<>();
    @Builder.Default
    private List<String> logMessages = new ArrayList<>();

    public void addLog(String message) {
        logMessages.add(message);
    }

    public String toFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("             PDF SPAM DETECTION REPORT             \n");
        sb.append("====================================================\n");
        sb.append(String.format("Total Pages:             %d\n", totalPages));
        sb.append(String.format("Total Text Blocks:       %d\n", totalTextBlocks));
        sb.append(String.format("Flagged Spam Text Lines: %d (%.1f%%)\n", 
                spamTextBlocksCount, totalTextBlocks > 0 ? (spamTextBlocksCount * 100.0 / totalTextBlocks) : 0));
        sb.append(String.format("Total Images:            %d\n", totalImages));
        sb.append(String.format("Flagged Spam Logos:      %d (%.1f%%)\n", 
                spamImagesCount, totalImages > 0 ? (spamImagesCount * 100.0 / totalImages) : 0));
        sb.append("----------------------------------------------------\n");
        sb.append("Top Detected Spam Text Phrases:\n");
        if (flaggedSpamPhrases.isEmpty()) {
            sb.append("  (None detected)\n");
        } else {
            for (String phrase : flaggedSpamPhrases) {
                sb.append("  - ").append(phrase).append("\n");
            }
        }
        sb.append("----------------------------------------------------\n");
        sb.append("Detected Spam Images / Logos:\n");
        if (flaggedSpamImages.isEmpty()) {
            sb.append("  (None detected)\n");
        } else {
            for (String img : flaggedSpamImages) {
                sb.append("  - ").append(img).append("\n");
            }
        }
        sb.append("====================================================\n");
        return sb.toString();
    }
}

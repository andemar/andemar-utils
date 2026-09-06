package com.andemar.pdfcleaner.cleaner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent linguistic and typographic post-processor for book and novel text.
 * Refines punctuation, dialogue formatting, sentence capitalization, commas, semicolons,
 * and paragraph structure while preserving inline markup like <i> and <b> tags.
 */
public class SemanticTextProcessor {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ \\t]{2,}");
    private static final Pattern DUPLICATE_COMMAS = Pattern.compile(",{2,}");
    private static final Pattern DUPLICATE_SEMICOLONS = Pattern.compile(";{2,}");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([,;:?.!])");
    private static final Pattern COMMA_MISSING_SPACE = Pattern.compile("([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])[,]([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");
    private static final Pattern SEMICOLON_MISSING_SPACE = Pattern.compile("([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])[;]([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");
    private static final Pattern COLON_MISSING_SPACE = Pattern.compile("([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])[:]([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");
    private static final Pattern INVERTED_QUESTION_SPACE = Pattern.compile("¿\\s+([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");
    private static final Pattern INVERTED_EXCLAMATION_SPACE = Pattern.compile("¡\\s+([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");
    private static final Pattern DOUBLE_PERIOD_TO_ELLIPSIS = Pattern.compile("(?<!\\.)\\.{2}(?!\\.)");
    private static final Pattern FOUR_OR_MORE_PERIODS = Pattern.compile("\\.{4,}");
    private static final Pattern ELLIPSIS_MISSING_TRAILING_SPACE = Pattern.compile("(\\.\\.\\.|…)([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ])");

    // Opening dialogue patterns: normalize leading hyphens / en-dashes to standard dialogue em-dash
    private static final Pattern OPENING_HYPHEN_DIALOGUE = Pattern.compile("^[-–]\\s*");
    private static final Pattern OPENING_EM_DASH_DIALOGUE = Pattern.compile("^—\\s*");

    // Common Spanish verbs for speech attribution that remain lowercase after dialogue dash:
    private static final Pattern SPEECH_VERB_ATTRIBUTION = Pattern.compile(
            "^(dijo|respondi[o\\u00f3]|pregunt[o\\u00f3]|murmur[o\\u00f3]|susurr[o\\u00f3]|a[n\\u00f1]adi[o\\u00f3]|coment[o\\u00f3]|grit[o\\u00f3]|exclam[o\\u00f3]|pens[o\\u00f3]|replic[o\\u00f3]|continu[o\\u00f3]|admiti[o\\u00f3]|sonri[o\\u00f3]|rio|ri[o\\u00f3]|asinti[o\\u00f3]|neg[o\\u00f3])\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * Refines a single paragraph by applying semantic formatting rules:
     * - Capitalizes initial character and sentence beginnings.
     * - Formats dialogue dashes and quotes.
     * - Fixes comma, semicolon, and colon spacing and duplicate punctuation.
     * - Normalizes ellipses and Spanish punctuation.
     */
    public String refineParagraph(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text.trim();

        // 1. Normalize multiple spaces
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");

        // 2. Normalize dialogue starters: standard em-dash with space (e.g. "— Hola")
        result = normalizeDialogueOpener(result);

        // 3. Fix duplicate punctuation
        result = DUPLICATE_COMMAS.matcher(result).replaceAll(",");
        result = DUPLICATE_SEMICOLONS.matcher(result).replaceAll(";");
        result = FOUR_OR_MORE_PERIODS.matcher(result).replaceAll("...");
        result = DOUBLE_PERIOD_TO_ELLIPSIS.matcher(result).replaceAll("...");

        // 4. Fix space before punctuation (e.g. "palabra ," -> "palabra,")
        result = SPACE_BEFORE_PUNCTUATION.matcher(result).replaceAll("$1");

        // 5. Fix missing space after comma, semicolon, colon (except between digits like 1,000 or 12:00)
        result = COMMA_MISSING_SPACE.matcher(result).replaceAll("$1, $2");
        result = SEMICOLON_MISSING_SPACE.matcher(result).replaceAll("$1; $2");
        result = COLON_MISSING_SPACE.matcher(result).replaceAll("$1: $2");

        // 6. Fix space inside Spanish inverted punctuation (¿ Qué -> ¿Qué, ¡ Vamos -> ¡Vamos)
        result = INVERTED_QUESTION_SPACE.matcher(result).replaceAll("¿$1");
        result = INVERTED_EXCLAMATION_SPACE.matcher(result).replaceAll("¡$1");

        // 7. Fix missing space after ellipsis before next word (e.g. "...hola" -> "... hola")
        result = ELLIPSIS_MISSING_TRAILING_SPACE.matcher(result).replaceAll("$1 $2");

        // 8. Capitalize sentence beginnings and dialogue turns
        result = applySentenceCapitalization(result);

        // 9. Final cleanup of spaces around HTML tags
        result = cleanupFormattingSpaces(result);

        return result.trim();
    }

    /**
     * Normalizes dialogue starters:
     * "- Hola" or "– Hola" or "—Hola" -> "— Hola"
     * "-¿Qué?" -> "—¿Qué?"
     */
    private String normalizeDialogueOpener(String text) {
        if (text.startsWith("- ") || text.startsWith("– ")) {
            String rest = text.substring(2).trim();
            if (!rest.isEmpty() && (rest.charAt(0) == '¿' || rest.charAt(0) == '¡' || rest.charAt(0) == '“' || rest.charAt(0) == '"')) {
                return "—" + rest;
            }
            return "— " + rest;
        }
        if (text.startsWith("-") || text.startsWith("–")) {
            String rest = text.substring(1).trim();
            if (!rest.isEmpty() && (rest.charAt(0) == '¿' || rest.charAt(0) == '¡' || rest.charAt(0) == '“' || rest.charAt(0) == '"')) {
                return "—" + rest;
            }
            return "— " + rest;
        }
        if (text.startsWith("—")) {
            String rest = text.substring(1);
            if (!rest.isEmpty() && rest.charAt(0) == ' ') {
                return text;
            }
            String trimmedRest = rest.trim();
            if (!trimmedRest.isEmpty() && (trimmedRest.charAt(0) == '¿' || trimmedRest.charAt(0) == '¡' || trimmedRest.charAt(0) == '“' || trimmedRest.charAt(0) == '"')) {
                return "—" + trimmedRest;
            }
            if (!trimmedRest.isEmpty() && Character.isLetter(trimmedRest.charAt(0))) {
                return "— " + trimmedRest;
            }
        }
        return text;
    }

    /**
     * Capitalizes initial character of the paragraph as well as after sentence terminators
     * (. ? ! ...) while respecting inline HTML tags and speech attribution dashes.
     */
    public String applySentenceCapitalization(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length());
        boolean capitalizeNextLetter = true;
        boolean inHtmlTag = false;
        char prevNonWhitespace = ' ';

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Handle HTML tags (e.g. <i>, </i>, <b>, </b>) - preserve untouched
            if (c == '<') {
                inHtmlTag = true;
                sb.append(c);
                continue;
            }
            if (inHtmlTag) {
                sb.append(c);
                if (c == '>') {
                    inHtmlTag = false;
                }
                continue;
            }

            // Check for letters
            if (Character.isLetter(c)) {
                if (capitalizeNextLetter) {
                    // Check if this letter is part of a dialogue attribution like "—dijo" after speech
                    if (prevNonWhitespace == '—' || prevNonWhitespace == '–' || prevNonWhitespace == '-') {
                        String remaining = text.substring(i);
                        if (SPEECH_VERB_ATTRIBUTION.matcher(remaining).find()) {
                            // Speech verbs like "dijo", "preguntó" remain lowercase after dialogue incise dash
                            sb.append(Character.toLowerCase(c));
                            capitalizeNextLetter = false;
                            prevNonWhitespace = c;
                            continue;
                        }
                    }
                    sb.append(Character.toUpperCase(c));
                    capitalizeNextLetter = false;
                } else {
                    sb.append(c);
                }
                prevNonWhitespace = c;
                continue;
            }

            sb.append(c);

            // Check if this character triggers capitalization for following sentence
            if (c == '.' || c == '?' || c == '!') {
                // Ignore decimal points in numbers e.g. 3.14
                if (c == '.' && i > 0 && i + 1 < text.length() &&
                    Character.isDigit(text.charAt(i - 1)) && Character.isDigit(text.charAt(i + 1))) {
                    capitalizeNextLetter = false;
                } else {
                    capitalizeNextLetter = true;
                }
            } else if (c == '…') {
                capitalizeNextLetter = true;
            } else if (c == '¿' || c == '¡') {
                // After opening Spanish marks, if at start of sentence, capitalize
                if (capitalizeNextLetter) {
                    // Next letter should still be capitalized
                    capitalizeNextLetter = true;
                }
            } else if (c == '“' || c == '"' || c == '«') {
                // Opening quote preserves pending capitalization
            } else if (c == '—' && (i == 0 || prevNonWhitespace == ' ')) {
                // Leading dialogue dash preserves pending capitalization
            }

            if (!Character.isWhitespace(c)) {
                prevNonWhitespace = c;
            }
        }

        return sb.toString();
    }

    /**
     * Cleans up unwanted spaces inside HTML tags, like <i> text </i> -> <i>text</i>.
     */
    private String cleanupFormattingSpaces(String text) {
        String res = text;
        res = res.replace("<i> ", " <i>");
        res = res.replace(" </i>", "</i> ");
        res = res.replace("<b> ", " <b>");
        res = res.replace(" </b>", "</b> ");
        return MULTIPLE_SPACES.matcher(res).replaceAll(" ");
    }
}

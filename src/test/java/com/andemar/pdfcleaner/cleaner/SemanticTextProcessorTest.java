package com.andemar.pdfcleaner.cleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SemanticTextProcessorTest {

    private SemanticTextProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SemanticTextProcessor();
    }

    @Test
    void testCapitalizationAtParagraphStart() {
        assertEquals("Hola mundo.", processor.refineParagraph("hola mundo."));
        assertEquals("— Hola, ¿cómo estás?", processor.refineParagraph("— hola, ¿cómo estás?"));
        assertEquals("— Hola a todos.", processor.refineParagraph("- hola a todos."));
        assertEquals("— Hola a todos.", processor.refineParagraph("– hola a todos."));
    }

    @Test
    void testSentenceCapitalizationAfterPunctuation() {
        String input = "Primera frase. segunda frase. ¿tercera frase? ¡cuarta frase! fin.";
        String expected = "Primera frase. Segunda frase. ¿Tercera frase? ¡Cuarta frase! Fin.";
        assertEquals(expected, processor.refineParagraph(input));
    }

    @Test
    void testCommaSpacingAndNormalization() {
        assertEquals("Uno, dos, tres, cuatro.", processor.refineParagraph("Uno ,dos ,,tres,cuatro."));
        // Decimal numbers should not have space inserted
        assertEquals("El valor es 3.14 o 1,000 unidades.", processor.refineParagraph("El valor es 3.14 o 1,000 unidades."));
    }

    @Test
    void testSemicolonAndColonSpacing() {
        assertEquals("Parte uno; parte dos: detalle final.",
                processor.refineParagraph("Parte uno ;parte dos :detalle final."));
        assertEquals("Nota: importante; revisar.",
                processor.refineParagraph("Nota:importante;;revisar."));
    }

    @Test
    void testEllipsisNormalization() {
        assertEquals("Espera... Ya voy...", processor.refineParagraph("Espera..ya voy...."));
    }

    @Test
    void testSpanishInvertedPunctuation() {
        assertEquals("¿Cómo estás? ¡Muy bien!", processor.refineParagraph("¿ Cómo estás? ¡ Muy bien!"));
    }

    @Test
    void testDialogueAttributionLowercasePreserved() {
        String input = "—¿Por qué hiciste eso? —preguntó ella con curiosidad.";
        assertEquals(input, processor.refineParagraph(input));

        String input2 = "—No lo sé —dijo Taiki mientras suspiraba.";
        String expected2 = "— No lo sé —dijo Taiki mientras suspiraba.";
        assertEquals(expected2, processor.refineParagraph(input2));
    }

    @Test
    void testHtmlTagsPreservation() {
        String input = "<i>hola</i> mundo, <b>esto</b> es una prueba.";
        String refined = processor.refineParagraph(input);
        assertTrue(refined.contains("<i>Hola</i>"), "Should capitalize inside HTML tag at sentence start");
        assertTrue(refined.contains("<b>esto</b>"), "Should preserve bold tags intact");
    }

    @Test
    void testIsSentenceTerminated() {
        // Incomplete sentences should not be terminated
        assertFalse(processor.isSentenceTerminated("Si bien uno es consciente de las condiciones para ser odiado, no conoce muy bien las"));
        assertFalse(processor.isSentenceTerminated("uno, dos,"));
        assertFalse(processor.isSentenceTerminated("interro-"));
        assertFalse(processor.isSentenceTerminated("palabra"));
        assertFalse(processor.isSentenceTerminated("Capítulo 1:"));

        // Abbreviations ending with dot should not be treated as sentence terminations
        assertFalse(processor.isSentenceTerminated("el Dr."));
        assertFalse(processor.isSentenceTerminated("ver pág."));
        assertFalse(processor.isSentenceTerminated("vol."));

        // Complete sentences should be terminated
        assertTrue(processor.isSentenceTerminated("cualidades para ser amado."));
        assertTrue(processor.isSentenceTerminated("“¿Qué pasa?”"));
        assertTrue(processor.isSentenceTerminated("¡Increíble!"));
        assertTrue(processor.isSentenceTerminated("Espera..."));
        assertTrue(processor.isSentenceTerminated("Final de frase.»"));
    }

    @Test
    void testIsDialogueOpener() {
        assertTrue(processor.isDialogueOpener("— Hola, ¿cómo estás?"));
        assertTrue(processor.isDialogueOpener("- Hola a todos"));
        assertTrue(processor.isDialogueOpener("– Hola"));
        assertTrue(processor.isDialogueOpener("“¿Qué pasa?”"));
        assertTrue(processor.isDialogueOpener("—¿De verdad?"));

        assertFalse(processor.isDialogueOpener("Hola"));
        assertFalse(processor.isDialogueOpener("Era una mañana tranquila."));
    }

    @Test
    void testIsSceneDivider() {
        assertTrue(processor.isSceneDivider("***"));
        assertTrue(processor.isSceneDivider("* * *"));
        assertTrue(processor.isSceneDivider("---"));
        assertTrue(processor.isSceneDivider("◆ ◆ ◆"));
        assertTrue(processor.isSceneDivider("★★★"));

        assertFalse(processor.isSceneDivider("Capítulo 1"));
        assertFalse(processor.isSceneDivider("Texto normal"));
    }

    @Test
    void testNormalizeSpacedLetters() {
        assertEquals("Prólogo", processor.normalizeSpacedLetters("P r ó l o g o"));
        assertEquals("Capítulo", processor.normalizeSpacedLetters("C a p í t u l o"));
        assertEquals("Texto normal", processor.normalizeSpacedLetters("Texto normal"));
    }

    @Test
    void testJoinLines() {
        StringBuilder sb = new StringBuilder();
        processor.joinLines(sb, "Primera línea");
        processor.joinLines(sb, "segunda línea");
        assertEquals("Primera línea segunda línea", sb.toString());

        StringBuilder sbHyphen = new StringBuilder();
        processor.joinLines(sbHyphen, "interro-");
        processor.joinLines(sbHyphen, "guen al testigo.");
        assertEquals("interroguen al testigo.", sbHyphen.toString());
    }
}

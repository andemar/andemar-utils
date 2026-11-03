package com.andemar.pdfcleaner.epub;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.config.LayoutMode;
import com.andemar.pdfcleaner.model.Chapter;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.CleanedPage;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.TextBlock;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubBuilderTest {

    @Test
    void testEpubStructureAndMimetypeCompliance() throws IOException {
        EpubBuilder builder = new EpubBuilder();
        CleanedDocument doc = buildSampleCleanedDoc();
        CleanerConfig config = CleanerConfig.builder().layoutMode(LayoutMode.REFLOWABLE).build();

        byte[] epubBytes = builder.buildToByteArray(doc, config);
        assertNotNull(epubBytes);
        assertTrue(epubBytes.length > 0);

        List<String> entryNames = new ArrayList<>();
        byte[] mimetypeContent = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(epubBytes))) {
            ZipEntry entry;
            boolean isFirst = true;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                if (isFirst) {
                    assertEquals("mimetype", entry.getName(), "First entry in EPUB must be 'mimetype'");
                    assertEquals(ZipEntry.STORED, entry.getMethod(), "Mimetype entry must be stored uncompressed");
                    mimetypeContent = zis.readAllBytes();
                    isFirst = false;
                }
                zis.closeEntry();
            }
        }

        assertNotNull(mimetypeContent);
        assertEquals("application/epub+zip", new String(mimetypeContent, StandardCharsets.US_ASCII));

        // Required EPUB structure items
        assertTrue(entryNames.contains("META-INF/container.xml"));
        assertTrue(entryNames.contains("OEBPS/content.opf"));
        assertTrue(entryNames.contains("OEBPS/nav.xhtml"));
        assertTrue(entryNames.contains("OEBPS/toc.ncx"));
        assertTrue(entryNames.contains("OEBPS/styles.css"));
        assertTrue(entryNames.contains("OEBPS/chapter_001.xhtml"));
        assertTrue(entryNames.contains("OEBPS/images/image_1.png"));
    }

    @Test
    void testFixedIshModeGeneratesPageSections() throws IOException {
        EpubBuilder builder = new EpubBuilder();
        CleanedDocument doc = buildSampleCleanedDoc();
        CleanerConfig config = CleanerConfig.builder().layoutMode(LayoutMode.FIXED_ISH).build();

        byte[] epubBytes = builder.buildToByteArray(doc, config);
        assertNotNull(epubBytes);

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(epubBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertTrue(entryNames.contains("OEBPS/page_001.xhtml"));
    }

    private CleanedDocument buildSampleCleanedDoc() {
        DocumentMetadata meta = DocumentMetadata.builder()
                .title("Sample Clean Book")
                .author("Jane Author")
                .language("en")
                .build();

        ImageBlock img = ImageBlock.builder()
                .id("figure-1")
                .mimeType("image/png")
                .rawBytes(new byte[]{ (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0 })
                .build();

        Chapter ch1 = Chapter.builder()
                .chapterIndex(1)
                .title("Chapter 1: The New Horizon")
                .paragraphs(List.of("The journey began early in the autumn morning.", "Leaves rustled underfoot."))
                .images(List.of(img))
                .build();

        CleanedPage page1 = CleanedPage.builder()
                .pageNumber(1)
                .width(600)
                .height(800)
                .cleanTextBlocks(List.of(
                        TextBlock.builder().text("Chapter 1: The New Horizon").build(),
                        TextBlock.builder().text("The journey began early in the autumn morning.").build()
                ))
                .cleanImageBlocks(List.of(img))
                .build();

        return CleanedDocument.builder()
                .metadata(meta)
                .chapters(List.of(ch1))
                .pages(List.of(page1))
                .build();
    }
}

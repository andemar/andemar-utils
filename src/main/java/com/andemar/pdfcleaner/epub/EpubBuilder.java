package com.andemar.pdfcleaner.epub;

import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.config.LayoutMode;
import com.andemar.pdfcleaner.model.Chapter;
import com.andemar.pdfcleaner.model.CleanedDocument;
import com.andemar.pdfcleaner.model.CleanedPage;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.TextBlock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Standards-compliant EPUB 3 / EPUB 2 generator that packages the cleaned document
 * into a valid, validated EPUB archive.
 * Supports both REFLOWABLE mode (paragraphs, semantic headings, inline figures)
 * and FIXED_ISH mode (page-divided semantic sections).
 */
public class EpubBuilder {

    private final com.andemar.pdfcleaner.cleaner.SemanticTextProcessor semanticProcessor = new com.andemar.pdfcleaner.cleaner.SemanticTextProcessor();

    public void build(CleanedDocument cleanedDoc, File outputFile, CleanerConfig config) throws IOException {
        byte[] epubBytes = buildToByteArray(cleanedDoc, config);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(epubBytes);
        }
    }

    public byte[] buildToByteArray(CleanedDocument cleanedDoc, CleanerConfig config) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. First entry MUST be uncompressed 'mimetype'
            writeMimetypeEntry(zos);

            // 2. Write META-INF/container.xml
            writeContainerXml(zos);

            // 3. Write CSS styles
            writeStylesCss(zos);

            // 4. Collect and write all embedded images
            Map<String, String> imagePathMap = writeImages(zos, cleanedDoc);

            // 5. Detect Cover Image
            String coverImageId = findCoverImageId(cleanedDoc);

            // 6. Generate chapter/page XHTML documents
            List<EpubSection> sections = generateSections(cleanedDoc, imagePathMap, config, coverImageId);
            for (EpubSection sec : sections) {
                writeZipEntry(zos, "OEBPS/" + sec.filename, sec.xhtmlContent.getBytes(StandardCharsets.UTF_8));
            }

            // 7. Generate and write Nav document (EPUB 3 nav.xhtml)
            String navContent = generateNavXhtml(cleanedDoc.getMetadata(), sections);
            writeZipEntry(zos, "OEBPS/nav.xhtml", navContent.getBytes(StandardCharsets.UTF_8));

            // 8. Generate and write NCX document (EPUB 2 toc.ncx)
            String ncxContent = generateTocNcx(cleanedDoc.getMetadata(), sections);
            writeZipEntry(zos, "OEBPS/toc.ncx", ncxContent.getBytes(StandardCharsets.UTF_8));

            // 9. Generate and write Package OPF (content.opf)
            String opfContent = generateContentOpf(cleanedDoc.getMetadata(), sections, imagePathMap, coverImageId);
            writeZipEntry(zos, "OEBPS/content.opf", opfContent.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private String findCoverImageId(CleanedDocument cleanedDoc) {
        if (cleanedDoc == null || cleanedDoc.getPages() == null) return null;
        for (CleanedPage page : cleanedDoc.getPages()) {
            if (page.getPageNumber() == 1 && !page.getCleanImageBlocks().isEmpty()) {
                return page.getCleanImageBlocks().get(0).getId();
            }
        }
        // Fallback: first clean image if on early page
        for (CleanedPage page : cleanedDoc.getPages()) {
            if (page.getPageNumber() <= 2 && !page.getCleanImageBlocks().isEmpty()) {
                return page.getCleanImageBlocks().get(0).getId();
            }
        }
        return null;
    }

    private void writeMimetypeEntry(ZipOutputStream zos) throws IOException {
        byte[] bytes = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    private void writeContainerXml(ZipOutputStream zos) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                "  <rootfiles>\n" +
                "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                "  </rootfiles>\n" +
                "</container>";
        writeZipEntry(zos, "META-INF/container.xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    private void writeStylesCss(ZipOutputStream zos) throws IOException {
        String css = """
                body {
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                  line-height: 1.65;
                  color: #1a1a1a;
                  margin: 1.5em;
                  padding: 0;
                }
                h1, h2, h3 {
                  font-family: inherit;
                  line-height: 1.25;
                  color: #0f172a;
                  margin-top: 1.8em;
                  margin-bottom: 0.8em;
                }
                h1 { font-size: 1.75em; border-bottom: 2px solid #e2e8f0; padding-bottom: 0.3em; }
                h2 { font-size: 1.4em; }
                p {
                  margin: 0 0 0.85em 0;
                  text-align: justify;
                  text-justify: inter-word;
                }
                figure {
                  margin: 1.8em 0;
                  text-align: center;
                }
                figure img {
                  max-width: 100%;
                  height: auto;
                  border-radius: 4px;
                  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
                }
                figcaption {
                  font-size: 0.85em;
                  color: #64748b;
                  margin-top: 0.5em;
                  font-style: italic;
                }
                .cover-container {
                  display: flex;
                  justify-content: center;
                  align-items: center;
                  min-height: 95vh;
                  text-align: center;
                }
                .cover-img {
                  max-width: 100%;
                  max-height: 98vh;
                  height: auto;
                  object-fit: contain;
                }
                .page-container {
                  border-bottom: 1px dashed #cbd5e1;
                  padding-bottom: 1.5em;
                  margin-bottom: 2em;
                }
                .page-number-label {
                  font-size: 0.75em;
                  color: #94a3b8;
                  text-transform: uppercase;
                  letter-spacing: 0.05em;
                  margin-bottom: 1em;
                }
                """;
        writeZipEntry(zos, "OEBPS/styles.css", css.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> writeImages(ZipOutputStream zos, CleanedDocument cleanedDoc) throws IOException {
        Map<String, String> map = new HashMap<>();
        int imgCount = 0;

        for (CleanedPage page : cleanedDoc.getPages()) {
            for (ImageBlock img : page.getCleanImageBlocks()) {
                if (img.getRawBytes() != null && img.getRawBytes().length > 0) {
                    imgCount++;
                    String ext = img.getMimeType().contains("jpeg") ? "jpg" : "png";
                    String fileName = String.format("image_%d.%s", imgCount, ext);
                    String fullPath = "OEBPS/images/" + fileName;
                    writeZipEntry(zos, fullPath, img.getRawBytes());
                    map.put(img.getId(), "images/" + fileName);
                }
            }
        }
        return map;
    }

    private List<EpubSection> generateSections(CleanedDocument cleanedDoc, Map<String, String> imageMap, CleanerConfig config, String coverImageId) {
        List<EpubSection> sections = new ArrayList<>();

        // Add Cover Page if cover image exists
        if (coverImageId != null && imageMap.containsKey(coverImageId)) {
            String coverRelPath = imageMap.get(coverImageId);
            String coverHtml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                    <head>
                    <title>Cover</title>
                    <link rel="stylesheet" type="text/css" href="styles.css"/>
                    <style type="text/css">
                      body.cover-body { margin: 0; padding: 0; text-align: center; }
                      img.cover-img { max-width: 100%%; max-height: 100vh; height: auto; object-fit: contain; }
                    </style>
                    </head>
                    <body class="cover-body">
                    <section epub:type="cover" class="cover-container">
                      <img class="cover-img" src="%s" alt="Cover"/>
                    </section>
                    </body>
                    </html>
                    """.formatted(coverRelPath);
            sections.add(new EpubSection("cover.xhtml", "Cover", coverHtml));
        }

        if (config.getLayoutMode() == LayoutMode.FIXED_ISH) {
            // Page-by-page layout
            for (CleanedPage page : cleanedDoc.getPages()) {
                StringBuilder html = new StringBuilder();
                html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                html.append("<!DOCTYPE html>\n");
                html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n");
                html.append("<head>\n<title>Page ").append(page.getPageNumber()).append("</title>\n");
                html.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/>\n</head>\n<body>\n");
                html.append("<section class=\"page-container\" id=\"page-").append(page.getPageNumber()).append("\">\n");
                html.append("<div class=\"page-number-label\">Original Page ").append(page.getPageNumber()).append("</div>\n");

                if (config.getTextStructure() == com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC) {
                    List<String> pageParagraphs = formatPageBlocksSemantically(page.getCleanTextBlocks());
                    for (String p : pageParagraphs) {
                        html.append("<p>").append(p).append("</p>\n");
                    }
                } else {
                    for (TextBlock tb : page.getCleanTextBlocks()) {
                        html.append("<p>").append(tb.getFormattedText()).append("</p>\n");
                    }
                }

                for (ImageBlock img : page.getCleanImageBlocks()) {
                    String relPath = imageMap.get(img.getId());
                    if (relPath != null) {
                        html.append("<figure>\n  <img src=\"").append(relPath)
                                .append("\" alt=\"Illustration from Page ").append(page.getPageNumber()).append("\"/>\n</figure>\n");
                    }
                }

                html.append("</section>\n</body>\n</html>");
                String filename = String.format("page_%03d.xhtml", page.getPageNumber());
                sections.add(new EpubSection(filename, "Page " + page.getPageNumber(), html.toString()));
            }
        } else {
            // Reflowable Chapter-based layout
            List<Chapter> chapters = cleanedDoc.getChapters();
            if (chapters.isEmpty()) {
                chapters = List.of(Chapter.builder().chapterIndex(1).title("Document").build());
            }

            for (int i = 0; i < chapters.size(); i++) {
                Chapter ch = chapters.get(i);
                StringBuilder html = new StringBuilder();
                html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                html.append("<!DOCTYPE html>\n");
                html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n");
                html.append("<head>\n<title>").append(escapeXml(ch.getTitle())).append("</title>\n");
                html.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/>\n</head>\n<body>\n");
                html.append("<section class=\"chapter\" id=\"ch-").append(ch.getChapterIndex()).append("\">\n");
                html.append("<h1>").append(escapeXml(ch.getTitle())).append("</h1>\n");

                if (!ch.getElements().isEmpty()) {
                    for (Chapter.Element el : ch.getElements()) {
                        if (el instanceof Chapter.ParagraphElement pe) {
                            html.append("<p>").append(pe.getText()).append("</p>\n");
                        } else if (el instanceof Chapter.ImageElement ie) {
                            String imgId = ie.getImage().getId();
                            // Skip re-rendering cover image inside front matter if already rendered in cover.xhtml
                            if (coverImageId != null && coverImageId.equals(imgId)) {
                                continue;
                            }
                            String relPath = imageMap.get(imgId);
                            if (relPath != null) {
                                html.append("<figure>\n  <img src=\"").append(relPath)
                                        .append("\" alt=\"Illustration\"/>\n</figure>\n");
                            }
                        }
                    }
                } else {
                    for (String p : ch.getParagraphs()) {
                        html.append("<p>").append(p).append("</p>\n");
                    }
                    for (ImageBlock img : ch.getImages()) {
                        if (coverImageId != null && coverImageId.equals(img.getId())) {
                            continue;
                        }
                        String relPath = imageMap.get(img.getId());
                        if (relPath != null) {
                            html.append("<figure>\n  <img src=\"").append(relPath)
                                    .append("\" alt=\"Illustration\"/>\n</figure>\n");
                        }
                    }
                }

                html.append("</section>\n</body>\n</html>");
                String filename = String.format("chapter_%03d.xhtml", ch.getChapterIndex());
                sections.add(new EpubSection(filename, ch.getTitle(), html.toString()));
            }
        }

        return sections;
    }

    private String generateNavXhtml(DocumentMetadata meta, List<EpubSection> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n");
        sb.append("<head><title>Table of Contents</title><link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/></head>\n");
        sb.append("<body>\n<nav epub:type=\"toc\" id=\"toc\">\n<h1>Table of Contents</h1>\n<ol>\n");
        for (EpubSection sec : sections) {
            sb.append("  <li><a href=\"").append(sec.filename).append("\">").append(escapeXml(sec.title)).append("</a></li>\n");
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>");
        return sb.toString();
    }

    private String generateTocNcx(DocumentMetadata meta, List<EpubSection> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n");
        sb.append("<head>\n");
        sb.append("  <meta name=\"dtb:uid\" content=\"urn:uuid:").append(UUID.randomUUID()).append("\"/>\n");
        sb.append("  <meta name=\"dtb:depth\" content=\"1\"/>\n");
        sb.append("  <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n");
        sb.append("  <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n");
        sb.append("</head>\n");
        sb.append("<docTitle><text>").append(escapeXml(meta.getTitle())).append("</text></docTitle>\n");
        sb.append("<navMap>\n");
        int playOrder = 1;
        for (EpubSection sec : sections) {
            sb.append("  <navPoint id=\"navpoint-").append(playOrder).append("\" playOrder=\"").append(playOrder).append("\">\n");
            sb.append("    <navLabel><text>").append(escapeXml(sec.title)).append("</text></navLabel>\n");
            sb.append("    <content src=\"").append(sec.filename).append("\"/>\n");
            sb.append("  </navPoint>\n");
            playOrder++;
        }
        sb.append("</navMap>\n</ncx>");
        return sb.toString();
    }

    private String generateContentOpf(DocumentMetadata meta, List<EpubSection> sections, Map<String, String> imageMap, String coverImageId) {
        StringBuilder sb = new StringBuilder();
        String bookId = "urn:uuid:" + UUID.randomUUID();
        String modifiedTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"BookId\">\n");
        sb.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("  <dc:identifier id=\"BookId\">").append(bookId).append("</dc:identifier>\n");
        sb.append("  <dc:title>").append(escapeXml(meta.getTitle())).append("</dc:title>\n");
        sb.append("  <dc:creator>").append(escapeXml(meta.getAuthor())).append("</dc:creator>\n");
        sb.append("  <dc:language>").append(escapeXml(meta.getLanguage())).append("</dc:language>\n");
        sb.append("  <meta property=\"dcterms:modified\">").append(modifiedTime).append("</meta>\n");
        if (coverImageId != null && imageMap.containsKey(coverImageId)) {
            sb.append("  <meta name=\"cover\" content=\"cover-image\"/>\n");
        }
        sb.append("</metadata>\n");

        sb.append("<manifest>\n");
        sb.append("  <item id=\"styles\" href=\"styles.css\" media-type=\"text/css\"/>\n");
        sb.append("  <item id=\"toc\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
        sb.append("  <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");

        for (int i = 0; i < sections.size(); i++) {
            EpubSection sec = sections.get(i);
            sb.append("  <item id=\"sec_").append(i).append("\" href=\"").append(sec.filename)
                    .append("\" media-type=\"application/xhtml+xml\"/>\n");
        }

        int imgIdx = 0;
        for (Map.Entry<String, String> entry : imageMap.entrySet()) {
            String imgId = entry.getKey();
            String relPath = entry.getValue();
            String mime = relPath.endsWith(".jpg") ? "image/jpeg" : "image/png";
            boolean isCover = imgId.equals(coverImageId);
            String itemProps = isCover ? " properties=\"cover-image\"" : "";
            String itemId = isCover ? "cover-image" : ("img_" + (imgIdx++));
            sb.append("  <item id=\"").append(itemId).append("\" href=\"").append(relPath)
                    .append("\" media-type=\"").append(mime).append("\"").append(itemProps).append("/>\n");
        }
        sb.append("</manifest>\n");

        sb.append("<spine toc=\"toc\">\n");
        for (int i = 0; i < sections.size(); i++) {
            EpubSection sec = sections.get(i);
            sb.append("  <itemref idref=\"sec_").append(i).append("\"/>\n");
        }
        sb.append("</spine>\n");
        sb.append("</package>");
        return sb.toString();
    }

    private List<String> formatPageBlocksSemantically(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> paragraphs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String prevClean = null;

        for (TextBlock tb : blocks) {
            String clean = tb.getCleanText();
            String formatted = tb.getFormattedText();
            if (clean.isBlank()) continue;

            if (buffer.length() > 0) {
                boolean shouldFlush = semanticProcessor.isSceneDivider(clean)
                        || semanticProcessor.isDialogueOpener(clean)
                        || (semanticProcessor.isSentenceTerminated(prevClean) && !Character.isLowerCase(clean.charAt(0)));

                if (shouldFlush) {
                    String p = semanticProcessor.refineParagraph(buffer.toString());
                    if (!p.isBlank()) {
                        paragraphs.add(p);
                    }
                    buffer.setLength(0);
                }
            }

            semanticProcessor.joinLines(buffer, formatted);
            prevClean = clean;
        }

        if (buffer.length() > 0) {
            String p = semanticProcessor.refineParagraph(buffer.toString());
            if (!p.isBlank()) {
                paragraphs.add(p);
            }
        }
        return paragraphs;
    }

    private void writeZipEntry(ZipOutputStream zos, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static class EpubSection {
        final String filename;
        final String title;
        final String xhtmlContent;

        EpubSection(String filename, String title, String xhtmlContent) {
            this.filename = filename;
            this.title = title;
            this.xhtmlContent = xhtmlContent;
        }
    }
}

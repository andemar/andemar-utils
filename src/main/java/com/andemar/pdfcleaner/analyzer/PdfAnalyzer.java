package com.andemar.pdfcleaner.analyzer;

import com.andemar.pdfcleaner.model.BoundingBox;
import com.andemar.pdfcleaner.model.DocumentMetadata;
import com.andemar.pdfcleaner.model.DocumentModel;
import com.andemar.pdfcleaner.model.ImageBlock;
import com.andemar.pdfcleaner.model.PageModel;
import com.andemar.pdfcleaner.model.TextBlock;
import com.andemar.pdfcleaner.spam.PerceptualHash;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PDF Analyzer using Apache PDFBox 3.x to extract text fragments with exact coordinates,
 * fonts, and bounding boxes, as well as raster images with bounding boxes and perceptual hashes.
 */
public class PdfAnalyzer {

    public DocumentModel analyze(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return extractDocumentModel(document);
        }
    }

    public DocumentModel analyze(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extractDocumentModel(document);
        }
    }

    public DocumentModel analyze(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return analyze(bytes);
    }

    private DocumentModel extractDocumentModel(PDDocument document) throws IOException {
        DocumentMetadata metadata = extractMetadata(document);
        List<PageModel> pages = new ArrayList<>();

        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage pdPage = document.getPage(i);
            int pageNumber = i + 1;
            PDRectangle mediaBox = pdPage.getMediaBox();
            float pageWidth = mediaBox.getWidth();
            float pageHeight = mediaBox.getHeight();

            // Extract text blocks for this page
            List<TextBlock> textBlocks = extractTextBlocks(document, pageNumber, pageWidth, pageHeight);

            // Extract image blocks for this page
            List<ImageBlock> imageBlocks = extractImageBlocks(pdPage, pageNumber, pageHeight);

            pages.add(PageModel.builder()
                    .pageNumber(pageNumber)
                    .width(pageWidth)
                    .height(pageHeight)
                    .textBlocks(textBlocks)
                    .imageBlocks(imageBlocks)
                    .build());
        }

        return DocumentModel.builder()
                .metadata(metadata)
                .pages(pages)
                .build();
    }

    private DocumentMetadata extractMetadata(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        String title = "Untitled Document";
        String author = "Unknown";
        String subject = null;
        String producer = null;

        if (info != null) {
            if (info.getTitle() != null && !info.getTitle().isBlank()) {
                title = info.getTitle().trim();
            }
            if (info.getAuthor() != null && !info.getAuthor().isBlank()) {
                author = info.getAuthor().trim();
            }
            subject = info.getSubject();
            producer = info.getProducer();
        }

        return DocumentMetadata.builder()
                .title(title)
                .author(author)
                .subject(subject)
                .producer(producer)
                .pageCount(document.getNumberOfPages())
                .language("en")
                .build();
    }

    private List<TextBlock> extractTextBlocks(PDDocument document, int pageNumber, float pageWidth, float pageHeight) throws IOException {
        TextLineCollectorStripper stripper = new TextLineCollectorStripper(pageNumber);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.setSortByPosition(true);
        // Stripper internally invokes writeString
        stripper.getText(document);
        return stripper.getCollectedBlocks();
    }

    private List<ImageBlock> extractImageBlocks(PDPage page, int pageNumber, float pageHeight) {
        try {
            ImageExtractorEngine engine = new ImageExtractorEngine(page, pageNumber, pageHeight);
            engine.processPage(page);
            return engine.getExtractedImages();
        } catch (Exception e) {
            // Return empty list if image extraction encounters malformed streams
            return new ArrayList<>();
        }
    }

    /**
     * Custom PDFTextStripper that collects individual text lines with exact bounding boxes and font metadata.
     */
    private static class TextLineCollectorStripper extends PDFTextStripper {
        private final int pageNumber;
        private final List<TextBlock> collectedBlocks = new ArrayList<>();

        public TextLineCollectorStripper(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public List<TextBlock> getCollectedBlocks() {
            return collectedBlocks;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
                return;
            }

            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            float fontSizeSum = 0;
            String fontName = "Default";
            boolean isBold = false;

            for (TextPosition tp : textPositions) {
                float x = tp.getXDirAdj();
                float y = tp.getYDirAdj();
                float width = tp.getWidthDirAdj();
                float height = tp.getHeightDir();

                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x + width > maxX) maxX = x + width;
                if (y + height > maxY) maxY = y + height;

                fontSizeSum += tp.getFontSizeInPt();
                if (tp.getFont() != null) {
                    fontName = tp.getFont().getName();
                    if (fontName.toLowerCase().contains("bold")) {
                        isBold = true;
                    }
                }
            }

            float avgFontSize = fontSizeSum / textPositions.size();
            float blockWidth = Math.max(0.0f, maxX - minX);
            float blockHeight = Math.max(0.0f, maxY - minY);

            // PDFTextStripper uses top-down Y coordinates (where 0 is top of page)
            BoundingBox bbox = BoundingBox.builder()
                    .x(minX)
                    .y(minY)
                    .width(blockWidth)
                    .height(blockHeight)
                    .build();

            TextBlock block = TextBlock.builder()
                    .text(text.trim())
                    .boundingBox(bbox)
                    .pageNumber(pageNumber)
                    .fontSize(avgFontSize)
                    .fontName(fontName)
                    .bold(isBold)
                    .isSpam(false)
                    .build();

            collectedBlocks.add(block);
        }
    }

    /**
     * Graphics stream engine intercepting drawImage to calculate bounding boxes and extract pixel data.
     */
    private static class ImageExtractorEngine extends PDFGraphicsStreamEngine {
        private final int pageNumber;
        private final float pageHeight;
        private final List<ImageBlock> extractedImages = new ArrayList<>();
        private int imageCounter = 0;

        public ImageExtractorEngine(PDPage page, int pageNumber, float pageHeight) {
            super(page);
            this.pageNumber = pageNumber;
            this.pageHeight = pageHeight;
        }

        public List<ImageBlock> getExtractedImages() {
            return extractedImages;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            imageCounter++;
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            float x = ctm.getTranslateX();
            float y = ctm.getTranslateY();
            float width = Math.abs(ctm.getScalingFactorX());
            float height = Math.abs(ctm.getScalingFactorY());

            // Convert PDF user space (origin at bottom-left) to top-down coordinates (origin at top-left)
            float topDownY = pageHeight - (y + height);

            BoundingBox bbox = BoundingBox.builder()
                    .x(x)
                    .y(topDownY)
                    .width(width)
                    .height(height)
                    .build();

            BufferedImage bImage = pdImage.getImage();
            byte[] imageBytes = null;
            String mimeType = "image/png";
            long dHash = 0L;
            long aHash = 0L;
            int pixelWidth = pdImage.getWidth();
            int pixelHeight = pdImage.getHeight();

            if (bImage != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "png", baos);
                imageBytes = baos.toByteArray();
                dHash = PerceptualHash.computeDHash(bImage);
                aHash = PerceptualHash.computeAHash(bImage);
                pixelWidth = bImage.getWidth();
                pixelHeight = bImage.getHeight();
            }

            ImageBlock block = ImageBlock.builder()
                    .id(String.format("img-p%d-%d", pageNumber, imageCounter))
                    .boundingBox(bbox)
                    .pageNumber(pageNumber)
                    .imageIndex(imageCounter)
                    .rawBytes(imageBytes)
                    .mimeType(mimeType)
                    .pixelWidth(pixelWidth)
                    .pixelHeight(pixelHeight)
                    .dHash(dHash)
                    .aHash(aHash)
                    .isSpam(false)
                    .build();

            extractedImages.add(block);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}

        @Override
        public void clip(int windingRule) {}

        @Override
        public void moveTo(float x, float y) {}

        @Override
        public void lineTo(float x, float y) {}

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {}

        @Override
        public void endPath() {}

        @Override
        public void strokePath() {}

        @Override
        public void fillPath(int windingRule) {}

        @Override
        public void fillAndStrokePath(int windingRule) {}

        @Override
        public void shadingFill(COSName shadingName) {}
    }
}

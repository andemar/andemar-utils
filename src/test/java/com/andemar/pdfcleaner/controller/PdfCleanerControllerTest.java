package com.andemar.pdfcleaner.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PdfCleanerControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PdfCleanerController()).build();
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("pdf-cleaner"));
    }

    @Test
    void testDryRunEndpoint() throws Exception {
        byte[] samplePdf = createMinimalPdf("CONFIDENTIAL REPORT", "Chapter 1 Body Text");

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", samplePdf
        );

        mockMvc.perform(multipart("/api/dry-run").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void testProcessEndpointReturnsEpub() throws Exception {
        byte[] samplePdf = createMinimalPdf("HEADER LINE", "Sample Book Content Paragraph");

        MockMultipartFile file = new MockMultipartFile(
                "file", "book.pdf", "application/pdf", samplePdf
        );

        mockMvc.perform(multipart("/process").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"book.cleaned.epub\""))
                .andExpect(content().contentType("application/epub+zip"));
    }

    private byte[] createMinimalPdf(String headerText, String bodyText) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                cs.beginText();
                cs.setFont(font, 10);
                cs.newLineAtOffset(50, 750);
                cs.showText(headerText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 400);
                cs.showText(bodyText);
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

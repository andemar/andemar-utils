# PDF Cleaner & EPUB Converter (`clean2epub`)

A high-performance, local Java 17+ utility and REST service that automatically detects and eliminates spam-like headers and footers (repetitive logos, watermarks, taglines, company slogans, pagination numbers, running headers) from PDFs and converts the cleaned text and authentic book illustrations into standards-compliant EPUB format.

---

## Key Features

- **Apache PDFBox 3.x Extraction**: Fast, accurate extraction of text positions, fonts, line coordinates, and raster image bounding boxes directly from the PDF graphics stream.
- **Dual Heuristic Spam Detection**:
  - **Text Spam Detection**: Analyzes top/bottom margins, cross-page frequency templates, page numbers (digits, roman numerals), and branding keywords.
  - **Image Spam Detection**: Perceptual difference hashing (dHash) and average hashing (aHash) clustering to detect repeated logos/watermarks across pages, branding margin corner detection, and text-spam proximity filtering.
  - **Legitimate Illustration Safeguard**: Large, central, single-occurrence diagrams and illustrations are explicitly protected and retained in the book content.
- **Standards-Compliant EPUB 3 / EPUB 2 Generation**:
  - Valid uncompressed `mimetype` as the first ZIP entry.
  - `META-INF/container.xml`, `OEBPS/content.opf`, `OEBPS/toc.ncx`, and `OEBPS/nav.xhtml`.
  - Supports **Reflowable** (paragraphs, headings, inline figures) and **Fixed-ish** (page-segmented) layout modes.
- **Interfaces**:
  - Direct CLI: `clean2epub input.pdf` or `java -jar pdf-cleaner.jar input.pdf`
  - Spring Boot HTTP REST API: `POST /process` (file download) and `POST /api/dry-run` (JSON diagnostics).
- **Pluggable Architecture**: Strategy interfaces for external ML logo classifiers (e.g. CLIP microservices) while running 100% locally by default without native OpenCV or cloud dependencies.

---

## Quick Start

### 1. Build

```bash
# Windows
.\gradlew bootJar

# Linux / macOS
./gradlew bootJar
```

### 2. Run Tests

```bash
.\gradlew test
```

### 3. Command-Line Usage (CLI)

```bash
# Default conversion (Reflowable EPUB, Balanced aggressiveness)
clean2epub my_book.pdf

# Fixed-layout-ish mode with custom output path
clean2epub my_book.pdf --layout-mode=fixedish -o my_book.epub

# Dry run with diagnostic report
clean2epub my_book.pdf --dry-run --report-path=diagnostics.txt

# Preserve specific chapter title or running header via whitelist
clean2epub my_book.pdf --whitelist-phrase="Introduction to Quantum Mechanics"

# Optional external ML/CLIP logo classification microservice
clean2epub my_book.pdf --ml-endpoint-url="http://localhost:5000/classify"
```

Or invoke the JAR directly:

```bash
java -jar build/libs/pdf-cleaner-0.0.1-SNAPSHOT.jar my_book.pdf [options]
```

### 4. HTTP Server Mode

Run the JAR without file arguments or with `--server`:

```bash
java -jar build/libs/pdf-cleaner-0.0.1-SNAPSHOT.jar --server
```

#### Endpoints

- `GET /api/health`
  - Returns service status.
- `POST /process`
  - Multipart form upload with `file` field.
  - Query parameters:
    - `aggressiveness`: `CONSERVATIVE` | `BALANCED` | `AGGRESSIVE`
    - `layoutMode`: `REFLOWABLE` | `FIXEDISH`
    - `whitelist`: comma-separated phrases to preserve
  - Returns: Cleaned `.epub` file attachment (`application/epub+zip`).
- `POST /api/dry-run`
  - Multipart form upload with `file` field.
  - Returns: JSON object containing detection statistics, list of flagged phrases, and flagged images.

---

## Project Structure

```
src/main/java/com/andemar/pdfcleaner/
├── analyzer/
│   └── PdfAnalyzer.java          # PDFBox 3.x text position & image extractor
├── cleaner/
│   └── PdfCleaner.java           # Paragraph regrouping, heading & illustration preservation
├── cli/
│   └── Clean2EpubCli.java        # CLI command runner & argument parser
├── config/
│   ├── Aggressiveness.java       # CONSERVATIVE, BALANCED, AGGRESSIVE presets
│   ├── CleanerConfig.java        # Configuration parameters and thresholds
│   └── LayoutMode.java           # REFLOWABLE vs FIXED_ISH
├── controller/
│   └── PdfCleanerController.java # Spring Boot REST Controller
├── epub/
│   └── EpubBuilder.java          # Standards-compliant EPUB packaging engine
├── model/
│   ├── BoundingBox.java          # Normalized 2D coordinates and margin calculations
│   ├── Chapter.java              # Structured book chapter with text and figures
│   ├── CleanedDocument.java      # Cleaned document representation
│   ├── CleanedPage.java          # Filtered page model
│   ├── DocumentMetadata.java     # Title, author, language metadata
│   ├── DocumentModel.java        # Parsed raw document representation
│   ├── ImageBlock.java           # Extracted image with dHash and dimensions
│   ├── PageModel.java            # Raw page representation
│   ├── SpamDetectionReport.java  # Diagnostic report
│   └── TextBlock.java            # Extracted text line with coordinates and font
├── pipeline/
│   └── CleanerPipelineOrchestrator.java # Ingest -> Analyze -> Detect -> Clean -> Build EPUB
├── spam/
│   ├── HttpClipLogoClassifier.java  # Optional external ML/CLIP service client
│   ├── ImageSpamDetector.java       # Perceptual hash clustering & margin logo detector
│   ├── MlLogoClassifier.java        # Strategy interface for ML logo classification
│   ├── NoOpMlLogoClassifier.java    # Default local no-op classifier
│   ├── PerceptualHash.java          # Pure Java 64-bit dHash & aHash implementation
│   ├── SpamDetectionEngine.java     # Coordinates text and image spam detection
│   └── TextSpamDetector.java        # Position, cross-page template & slogan detector
└── PdfCleanerApplication.java       # Dual CLI / Web entry point
```

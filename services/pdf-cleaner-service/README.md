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
# Default conversion (Reflowable EPUB, Balanced aggressiveness, Semantic text structuring)
clean2epub my_book.pdf

# Keep exact verbatim line breaks and punctuation structure from PDF
clean2epub my_book.pdf --text-structure=exact -o exact_book.epub
# Or using shorthand flag:
clean2epub my_book.pdf --exact-structure

# Apply intelligent semantic language structuring (commas, semicolons, capital letters, dialogue breaks)
clean2epub my_book.pdf --text-structure=semantic

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

The application starts by default on port `8080`.

#### Endpoints Overview

| Endpoint | Method | Input | Output | Description |
|---|---|---|---|---|
| `/api/health` | `GET` | None | JSON | Returns service status and version. |
| `/process` | `POST` | `multipart/form-data` | `application/epub+zip` | Processes PDF and downloads the converted EPUB. |
| `/api/dry-run` | `POST` | `multipart/form-data` | `application/json` | Analyzes PDF and returns spam diagnostics without building an EPUB. |

---

#### Server Mode Query Parameters & Form Fields

Both `POST /process` and `POST /api/dry-run` accept multipart form submissions with the following parameters:

| Parameter | Type | In | Required | Default | Allowed Values | Description |
|---|---|---|---|---|---|---|
| `file` | Binary | Form-Data | **Yes** | — | Valid `.pdf` file | The input PDF document to clean and convert. |
| `aggressiveness` | String | Query / Form | No | `BALANCED` | `CONSERVATIVE`, `BALANCED`, `AGGRESSIVE` | Controls detection thresholds for repetitive headers, footers, and logos. |
| `layoutMode` | String | Query / Form | No | `REFLOWABLE` | `REFLOWABLE`, `FIXEDISH` | Determines structural output layout format for the EPUB. *(Applied to `/process`)* |
| `textStructure` | String | Query / Form | No | `SEMANTIC` | `SEMANTIC`, `EXACT` | Determines whether to apply intelligent semantic language structuring or keep exact PDF lines verbatim. |
| `whitelist` | String | Query / Form | No | *None* | Comma-separated strings | Phrases or keywords to explicitly protect from removal even if in margins. |
| `mlEndpointUrl` | String | Query / Form | No | *None* | Valid HTTP/HTTPS URL | Optional external ML/CLIP logo classification microservice endpoint. |

##### Parameter Details

1. **`aggressiveness`**:
   - **`CONSERVATIVE`**: Uses narrower header/footer margin thresholds (8% top, 7% bottom) and requires high cross-page repetition frequency (65% for text, 60% for images). Recommended for dense textbooks, research papers, or documents with valuable running headers where false positives must be strictly avoided.
   - **`BALANCED`** *(Default)*: Uses standard margin thresholds (12% top, 10% bottom) and balanced frequency thresholds (45% for text, 40% for images). Recommended for general fiction, non-fiction, light novels, and manuals.
   - **`AGGRESSIVE`**: Uses wider margin detection (16% top, 14% bottom) and lower frequency thresholds (30% for text, 25% for images). Ideal for heavily watermarked PDFs, scanlation releases, distributor-stamped books, or documents with persistent promotional slogans.

2. **`layoutMode`**:
   - **`REFLOWABLE`** *(Default)*: Packages content into semantic chapters (`chapter_001.xhtml`, etc.) with properly formatted paragraphs, dialogue indentation, preserved italics/bold styling, in-sequence illustrations, and table of contents. Optimized for e-readers (Kindle, Kobo, Apple Books, Calibre) and responsive reading.
   - **`FIXEDISH`**: Packages content page-by-page (`page_001.xhtml`, etc.) preserving original PDF page boundaries (`Original Page N`). Ideal for textbooks, presentation slide decks, or documents where original page numbers must be cited directly.

3. **`textStructure`** *(or `structure`)*:
   - **`SEMANTIC`** *(Default)*: Intelligently reconstructs paragraphs across soft line wraps, removes broken hyphens, formats novel dialogue with standard em-dashes (`—`), normalizes spacing around commas, semicolons, and colons, fixes ellipses (`...`), and enforces capital letters at sentence starts and dialogue turns.
   - **`EXACT`**: Retains every non-spam line break verbatim as extracted from the original PDF without paragraph merging or punctuation modification. Preserves exact line-by-line structure and raw casing.

4. **`whitelist`**:
   - Comma-separated list of phrases that will never be flagged as spam, regardless of position or recurrence frequency.
   - *Example*: `whitelist=She Recommends the Harem,Chapter 1,Introduction`

5. **`mlEndpointUrl`**:
   - Optional URL for an external ML service implementing logo detection (e.g. CLIP). Borderline candidates will be posted to this endpoint for classification.
   - *Example*: `mlEndpointUrl=http://localhost:5000/classify`

---

#### Server Mode Usage Examples

##### Example 1: Standard Conversion (cURL)
Convert a PDF using default balanced settings and reflowable layout, saving output to `book.epub`:

```bash
curl -X POST "http://localhost:8080/process" \
  -F "file=@my_book.pdf" \
  -o "my_book.cleaned.epub"
```

##### Example 2: Conservative Aggressiveness with Whitelist (cURL)
Preserve running headers that contain the book title and author name:

```bash
curl -X POST "http://localhost:8080/process?aggressiveness=CONSERVATIVE&whitelist=Quantum%20Mechanics,David%20Griffiths" \
  -F "file=@physics_textbook.pdf" \
  -o "physics_textbook.cleaned.epub"
```

##### Example 3: Fixed-ish Layout Mode (cURL)
Keep page-by-page segmentation for a reference manual:

```bash
curl -X POST "http://localhost:8080/process?layoutMode=FIXEDISH" \
  -F "file=@user_manual.pdf" \
  -o "user_manual.cleaned.epub"
```

##### Example 4: Exact Text Structure Mode (cURL)
Retain exact verbatim lines and original layout from the PDF:

```bash
curl -X POST "http://localhost:8080/process?textStructure=EXACT" \
  -F "file=@novel.pdf" \
  -o "novel.exact.epub"
```

##### Example 5: Dry-Run Diagnostics (cURL)
Inspect spam detection statistics and flagged phrases as JSON without generating an EPUB binary:

```bash
curl -X POST "http://localhost:8080/api/dry-run?aggressiveness=BALANCED" \
  -F "file=@my_book.pdf"
```
*Sample JSON Response:*
```json
{
  "totalPages": 132,
  "spamTextBlocksCount": 396,
  "spamImagesCount": 396,
  "flaggedPhrases": [
    "Ella recomienda el harem",
    "TAMASHI’S PROJECT"
  ],
  "flaggedImageDetails": [
    "Image img-p1-1 (158x74) [REPEATED_LOGO_HASH (freq=100% on 132 pages), p.1]"
  ]
}
```

##### Example 5: PowerShell (`Invoke-RestMethod`)
Convert a file and download directly on Windows PowerShell:

```powershell
$form = @{
    file = Get-Item -Path ".\my_book.pdf"
}
Invoke-RestMethod -Uri "http://localhost:8080/process?aggressiveness=BALANCED&layoutMode=REFLOWABLE" `
    -Method Post `
    -Form $form `
    -OutFile ".\my_book.cleaned.epub"
```

---

### 5. Running with Docker

The project includes a `Dockerfile` based on `eclipse-temurin:26-jdk` and runs as an unprivileged user (`appuser`, UID 1000). Gradle wrapper and dependencies are pre-cached during build time.

#### Build the Image

From the `services/pdf-cleaner-service` directory:

```bash
docker build -t pdf-cleaner-service .
```

#### Run the HTTP Server Container

Expose port `8080` to access the REST API:

```bash
docker run --rm -it -p 8080:8080 --name pdf-cleaner-service pdf-cleaner-service
```

For **local development**, bind-mount the `src/` directory so code modifications are immediately available inside the container without rebuilding the image:

* **Windows PowerShell:**
  ```powershell
  docker run --rm -it -p 8080:8080 -v "${PWD}/src:/app/src" --name pdf-cleaner-service pdf-cleaner-service
  ```
* **Linux / macOS (Bash / Zsh):**
  ```bash
  docker run --rm -it -p 8080:8080 -v "$PWD/src:/app/src" --name pdf-cleaner-service pdf-cleaner-service
  ```

#### Run CLI Mode via Docker

Mount your local directory to `/data` to process files directly from the command line:

* **Windows PowerShell:**
  ```powershell
  docker run --rm -v "${PWD}:/data" pdf-cleaner-service ./gradlew bootRun --no-daemon --args="/data/my_book.pdf -o /data/my_book.cleaned.epub"
  ```
* **Linux / macOS (Bash / Zsh):**
  ```bash
  docker run --rm -v "$PWD:/data" pdf-cleaner-service ./gradlew bootRun --no-daemon --args="/data/my_book.pdf -o /data/my_book.cleaned.epub"
  ```

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

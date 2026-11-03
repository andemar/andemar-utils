# Andemar Utils

A monorepo collection of standalone utility services, scrapers, converters, and ML tools unified by a central **Application Hub** platform.

---

## ⚡ Central Application Hub Platform

The repository includes an orchestrated Hub platform consisting of an **Angular SPA** (`core/hub-ui`) and a **Node.js/TypeScript Orchestrator** (`core/hub-backend`). Instead of running multiple heavy services simultaneously, the Hub dynamically discovers declarative `manifest.json` configurations in `/services`, starts containers on demand, streams CLI logs in real-time with `xterm.js`, and reverse-proxies Web and REST APIs.

### Quick Start (Hub Platform)
```bash
# 1. Copy sample environment file
cp .env.example .env

# 2. Build the child service images (as needed)
make build-all
# Or individually: make build-spam, make build-anitracker, make build-pdf

# 3. Launch the Application Hub
docker compose up -d

# 4. Open in browser
# Hub UI:      http://localhost (or http://localhost:80)
# Hub Backend: http://localhost:4000/api/services
```

---

## Table of Contents

- [Central Application Hub Platform](#-central-application-hub-platform)
- [Overview](#overview)
- [Services](#services)
  - [1. AniTracker App Service (`anitracker-app-service`)](#1-anitracker-app-service-anitracker-app-service)
  - [2. Image Downloader Service (`image-downloader-service`)](#2-image-downloader-service-image-downloader-service)
  - [3. PDF Cleaner Service (`pdf-cleaner-service`)](#3-pdf-cleaner-service-pdf-cleaner-service)
  - [4. Spam Service (`spam-service`)](#4-spam-service-spam-service)
- [Quick Reference](#quick-reference)

---

## Overview

| Service | Directory | Tech Stack | Default Port | Description |
|---|---|---|---|---|
| **AniTracker App** | [`services/anitracker-app-service`](services/anitracker-app-service) | Next.js 16, React 19, TypeScript, Tailwind CSS | `3000` | Anime schedule & countdown tracker with Nyaa torrent search |
| **Image Downloader** | [`services/image-downloader-service`](services/image-downloader-service) | Python 3.10+, FastAPI, Jinja2, BeautifulSoup4 | `8000` | Web UI for downloading and organizing manga chapters |
| **PDF Cleaner & EPUB Converter** | [`services/pdf-cleaner-service`](services/pdf-cleaner-service) | Java 17+, Spring Boot 3, Apache PDFBox 3.x | `8080` | Removes PDF watermark/header/footer spam and converts to EPUB |
| **Spam Service** | [`services/spam-service`](services/spam-service) | Python 3.11+, PyTorch, EfficientNet-B4, CUDA | N/A (CLI) | GPU-accelerated binary classifier to detect & filter scanlation ad spam |

---

## Services

### 1. AniTracker App Service (`anitracker-app-service`)

#### What does it do?
A modern web application built with **Next.js 16**, **React 19**, and **Tailwind CSS** that tracks anime airing schedules and countdowns. It integrates directly with the **AniList GraphQL API** for anime metadata and provides a built-in scraper API for searching episode releases on **Nyaa**. Users can filter by season/year, track airing countdowns, manage viewing preferences, and find torrent downloads.

#### How to build and containerize (Commands)

* **Build the Docker image:**
  ```bash
  # From repository root
  docker build -t anitracker-app-service:dev services/anitracker-app-service

  # Or from the service directory (services/anitracker-app-service)
  docker build -t anitracker-app-service:dev .
  ```

* **Run the Docker container:**
  ```bash
  # Standalone container
  docker run --rm -it -p 3000:3000 --name anitracker-app anitracker-app-service:dev
  ```

  ```powershell
  # Live development with hot reloading (Windows PowerShell - repository root)
  docker run --rm -it `
    -p 3000:3000 `
    -v "${PWD}\services\anitracker-app-service:/app" `
    -v /app/node_modules `
    -v /app/.next `
    --name anitracker-app `
    anitracker-app-service:dev
  ```

  ```bash
  # Live development with hot reloading (Linux / macOS - from service directory)
  docker run --rm -it \
    -p 3000:3000 \
    -v "$PWD:/app" \
    -v /app/node_modules \
    -v /app/.next \
    --name anitracker-app \
    anitracker-app-service:dev
  ```

* **Local native development (without Docker):**
  ```bash
  cd services/anitracker-app-service
  npm install
  npm run dev
  ```

#### How to use it
1. Start the container or local dev server.
2. Open your browser and navigate to `http://localhost:3000`.
3. **Browse Anime:** View seasonal schedules, popular airing titles, and episode release countdown badges.
4. **Filter:** Filter releases by season (WINTER, SPRING, SUMMER, FALL) and release year.
5. **Anime Details & Torrent Search:** Click on an anime card to view detailed episode lists, synopsis, and search associated releases via the integrated Nyaa torrent search dialog.

---

### 2. Image Downloader Service (`image-downloader-service`)

#### What does it do?
A local, web-based application built with **FastAPI** and **Jinja2** designed to scrape and download manga chapters. It employs an extensible Plugin/Strategy architecture (`requests` and `BeautifulSoup4`) that allows adding new scraper modules for different websites. Images are downloaded asynchronously and organized automatically in a clean directory hierarchy:
```text
<Save Folder>/<Manga Name>/Chapter <Number>/<ImageNumber>.png
```

#### How to build and containerize (Commands)

* **Build the Docker image:**
  ```bash
  # From repository root
  docker build -t image-downloader-service:dev services/image-downloader-service

  # Or from the service directory (services/image-downloader-service)
  docker build -t image-downloader-service:dev .
  ```

* **Run the Docker container:**
  ```powershell
  # Windows PowerShell
  docker run --rm -it `
    -p 8000:8000 `
    -v "${PWD}\services\image-downloader-service:/app" `
    -v "${local_folder}:/downloads" `
    --name image-downloader `
    image-downloader-service:dev
  ```

  ```bash
  # Linux / macOS
  docker run --rm -it \
    -p 8000:8000 \
    -v "$PWD/services/image-downloader-service:/app" \
    -v "/path/to/downloads:/downloads" \
    --name image-downloader \
    image-downloader-service:dev
  ```
  *(Replace `${local_folder}` or `/path/to/downloads` with your desired host storage folder).*

* **Local native run (without Docker):**
  ```bash
  cd services/image-downloader-service
  python -m venv .venv

  # Activate venv:
  # Windows: .\.venv\Scripts\Activate.ps1
  # Linux/macOS: source .venv/bin/activate

  pip install -r requirements.txt
  python run.py
  # Or: uvicorn app.main:app --reload --port 8000
  ```

#### How to use it
1. Start the service and open `http://127.0.0.1:8000` in your web browser.
2. **Manga URL:** Enter the web URL of the manga you want to download (for testing, enter any URL containing `mockmanga.com`, e.g., `https://mockmanga.com/test`).
3. **Save Folder:** Specify the target folder on your system (if running inside Docker, set this to `/downloads` or click **Use /downloads**).
4. **Chapter Range:** (Optional) Enter Start and End chapter limits to download specific ranges.
5. Click **Download** to begin fetching chapters.

---

### 3. PDF Cleaner Service (`pdf-cleaner-service`)

#### What does it do?
A high-performance **Java 17+ / Spring Boot 3** utility (`clean2epub`) that operates both as a **command-line tool (CLI)** and a **REST API**. It parses PDF documents using **Apache PDFBox 3.x** and removes repetitive spam, running headers, watermarks, sponsor slogans, and ad logos using perceptual image hashing (dHash/aHash) and spatial layout analysis. It then exports the cleaned content, preserving authentic chapter illustrations and headings, into valid, standards-compliant **EPUB 3 / EPUB 2** files.

#### How to build and containerize (Commands)

* **Build the JAR locally (Gradle):**
  ```bash
  # Windows
  cd services/pdf-cleaner-service
  .\gradlew bootJar

  # Linux / macOS
  cd services/pdf-cleaner-service
  ./gradlew bootJar
  ```

* **Build the Docker image:**
  ```bash
  # From repository root
  docker build -t pdf-cleaner-service services/pdf-cleaner-service

  # Or from the service directory (services/pdf-cleaner-service)
  docker build -t pdf-cleaner-service .
  ```

* **Run the Docker container (HTTP REST Server Mode):**
  ```bash
  docker run --rm -it -p 8080:8080 --name pdf-cleaner-service pdf-cleaner-service
  ```

* **Run CLI Mode via Docker:**
  ```powershell
  # Windows PowerShell
  docker run --rm -v "${PWD}:/data" pdf-cleaner-service `
    ./gradlew bootRun --no-daemon --args="/data/input.pdf -o /data/output.epub"
  ```
  ```bash
  # Linux / macOS
  docker run --rm -v "$PWD:/data" pdf-cleaner-service \
    ./gradlew bootRun --no-daemon --args="/data/input.pdf -o /data/output.epub"
  ```

#### How to use it

##### A. Command-Line Interface (CLI)
```bash
# Basic conversion (Reflowable EPUB with balanced aggressiveness)
clean2epub my_book.pdf -o my_book.cleaned.epub

# Run directly via JAR
java -jar build/libs/pdf-cleaner-0.0.1-SNAPSHOT.jar my_book.pdf [options]

# Useful Flags:
#   --exact-structure                Keep exact PDF line breaks verbatim without paragraph joining
#   --text-structure=semantic|exact  Choose semantic restructuring vs exact lines
#   --layout-mode=reflowable|fixedish Choose reflowable chapters or page-by-page layout
#   --aggressiveness=CONSERVATIVE|BALANCED|AGGRESSIVE Set spam detection threshold
#   --whitelist-phrase="Chapter"     Protect specific phrases from being stripped
#   --dry-run --report-path=rep.txt  Analyze and output spam report without generating EPUB
```

##### B. HTTP REST API (Port 8080)
Start the server with `java -jar pdf-cleaner-0.0.1-SNAPSHOT.jar --server` or via Docker:

* **Process PDF to EPUB (`POST /process`):**
  ```bash
  curl -X POST "http://localhost:8080/process?aggressiveness=BALANCED&layoutMode=REFLOWABLE" \
    -F "file=@my_book.pdf" \
    -o "my_book.cleaned.epub"
  ```

* **Dry-Run Analysis (`POST /api/dry-run`):**
  ```bash
  curl -X POST "http://localhost:8080/api/dry-run?aggressiveness=BALANCED" \
    -F "file=@my_book.pdf"
  ```
  Returns a JSON report detailing total pages, flagged spam text blocks, and detected repeated logo hashes.

* **Health Check (`GET /api/health`):**
  ```bash
  curl http://localhost:8080/api/health
  ```

---

### 4. Spam Service (`spam-service`)

#### What does it do?
An **EfficientNet-B4** binary image classifier implemented in **PyTorch** with **NVIDIA CUDA GPU acceleration**. It is specifically trained to detect and separate **spam images** (scanlation credit/recruitment pages, promotional ads, distributor watermarks) from **clean manga panels**. It provides automated batch processing to either move or rename flagged spam files within directories.

#### How to build and containerize (Commands)

* **Build the Docker image:**
  ```bash
  # From repository root
  docker build -t spam-service services/spam-service

  # Or from the service directory (services/spam-service)
  docker build -t spam-service .
  ```
  *(Alternatively, run `.\spam-service.ps1 build` on Windows or `./spam-service.sh build` on Linux).*

* **Run via CLI Helper Scripts (Recommended):**
  Helper scripts handle GPU passthrough and directory volume mappings automatically:

  * **Training:**
    ```powershell
    # Windows PowerShell
    .\services\spam-service\spam-service.ps1 train `
      -SpamDir "F:\AI\Training\manga_spam" `
      -NotSpamDir "F:\AI\Training\manga_images" `
      -OutputDir "F:\AI\Training\output" `
      -Epochs 30 `
      -BatchSize 32
    ```
    ```bash
    # Linux / WSL
    ./services/spam-service/spam-service.sh train \
      --spam-dir "/path/to/manga_spam" \
      --not-spam-dir "/path/to/manga_images" \
      --output-dir "/path/to/output" \
      --epochs 30 \
      --batch_size 32
    ```

  * **Scanning a Folder:**
    ```powershell
    # Windows PowerShell - Move detected spam to 'spam_detected/' folder
    .\services\spam-service\spam-service.ps1 scan `
      -Folder "F:\Manga\Chapter01" `
      -Model "F:\AI\Training\output\best_model.pt" `
      -MoveSpam `
      -Threshold 0.83
    ```
    ```bash
    # Linux / WSL
    ./services/spam-service/spam-service.sh scan \
      --folder "/path/to/chapter_images" \
      --model "/path/to/output/best_model.pt" \
      --move-spam \
      --threshold 0.83
    ```

* **Direct Docker Commands (Manual):**
  ```bash
  # Train with GPU:
  docker run --rm -it --gpus all --shm-size=4g \
    -v "/host/spam:/dataset/spam:ro" \
    -v "/host/not_spam:/dataset/not_spam:ro" \
    -v "/host/output:/output" \
    spam-service train --epochs 30 --batch_size 32

  # Scan with GPU:
  docker run --rm -it --gpus all --shm-size=4g \
    -v "/host/chapter_images:/scan" \
    -v "/host/models:/models:ro" \
    spam-service scan --model /models/best_model.pt --rename_spam --threshold 0.83
  ```

#### How to use it
1. **Model Training:**
   - Prepare two image folders: one containing spam/credit pages, and another containing clean manga pages.
   - Run the `train` command specifying epochs and batch size. The trained weights `best_model.pt` and metrics `training_log.csv` will be saved to the specified output directory.
2. **Folder Scanning (Inference):**
   - Point the `scan` command to an uncleaned image folder and specify the path to `best_model.pt`.
   - Set the classification confidence threshold using `--threshold` (e.g. `0.83`).
   - Use `-MoveSpam` / `--move-spam` to isolate flagged images into a `spam_detected/` subdirectory, or `-RenameSpam` / `--rename_spam` to append `-spam` to the filenames in place (e.g., `page01.jpg` -> `page01-spam.jpg`).

---

## Quick Reference

| Action | `anitracker-app-service` | `image-downloader-service` | `pdf-cleaner-service` | `spam-service` |
|---|---|---|---|---|
| **Build Docker** | `docker build -t anitracker-app-service:dev services/anitracker-app-service` | `docker build -t image-downloader-service:dev services/image-downloader-service` | `docker build -t pdf-cleaner-service services/pdf-cleaner-service` | `docker build -t spam-service services/spam-service` |
| **Run Container** | `docker run -p 3000:3000 anitracker-app-service:dev` | `docker run -p 8000:8000 -v "${local_folder}:/downloads" image-downloader-service:dev` | `docker run -p 8080:8080 pdf-cleaner-service` | `.\spam-service.ps1 scan -Folder ... -Model ...` |
| **Primary Interface** | Web Browser (`http://localhost:3000`) | Web Browser (`http://localhost:8000`) | CLI / REST API (`http://localhost:8080`) | CLI / Docker GPU Runner |


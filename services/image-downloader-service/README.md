# Local Manga Downloader

A flexible, local web-based application designed to download manga chapters. It features a simple web UI and is built using a highly extensible Plugin/Strategy architecture, allowing you to easily add support for new manga websites in the future.

## Prerequisites

- **Python 3.10+** (Python 3.13 is fully supported)
- Standard local environment (Windows/macOS/Linux)

## Architecture

- **Backend:** FastAPI
- **Frontend:** Jinja2 Templates (HTML/CSS)
- **Scraping logic:** Modular plugin system using `requests` and `BeautifulSoup4`.
- **Extensibility:** The HTTP client is wrapped in a dedicated class, so it can easily be swapped out for headless browser tools (like Playwright or Selenium) if sites add heavy anti-bot protections.

---

## Installation

**1. Clone or download the repository**
Ensure you are in the project folder in your terminal:
```bash
cd ImageDownloader
```

**2. Create a Virtual Environment**
It is highly recommended to use a virtual environment to keep dependencies isolated:
```bash
python -m venv .venv
```

**3. Activate the Virtual Environment**
- **Windows (PowerShell):**
  ```powershell
  .\.venv\Scripts\Activate.ps1
  ```
- **Windows (Command Prompt):**
  ```cmd
  .\.venv\Scripts\activate.bat
  ```
- **macOS / Linux:**
  ```bash
  source .venv/bin/activate
  ```

*(You should see `(.venv)` appear at the beginning of your terminal prompt indicating it is active).*

**4. Install Dependencies**
```bash
pip install -r requirements.txt
```

---

## Running the Application

Once the dependencies are installed and the virtual environment is activated, you can start the application by running:

```bash
python run.py
```

*(Alternatively, you can run `uvicorn app.main:app --reload`)*

The terminal should output something like:
```text
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
```

---

## Usage

1. Open your web browser and navigate to `http://127.0.0.1:8000`.
2. **Manga URL:** Enter the URL of the manga you wish to download.
   - *For testing purposes, you can use the built-in mock scraper by entering any URL containing `mockmanga.com` (e.g., `https://mockmanga.com/test`).*
3. **Save Folder:** Provide an absolute path on your local computer where you want the images saved (e.g., `C:\MangaDownloads` on Windows or `/Users/YourName/Manga` on macOS/Linux).
4. **Start / End Chapter:** (Optional) You can limit which chapters are downloaded by providing chapter bounds.
5. Click **Download**.

The application will strictly organize your downloads using the following folder structure:
```text
<Save Folder>/<Manga Name>/Chapter <Number>/<ImageNumber>.png
```

---

## Adding New Scrapers

To support a new website:
1. Create a new Python file inside the `scrapers/` folder.
2. Inherit from `BaseMangaScraper` located in `scrapers/base.py`.
3. Implement the required interface methods (`can_handle`, `get_manga_title`, `get_chapter_list`, `get_chapter_images`).
4. Register your new scraper inside `core/factory.py` under the `_scrapers` list.

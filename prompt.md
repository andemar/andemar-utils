# Manga Scraper Plugin Request

**Context:**
I have a local web-based Manga Downloader application built with FastAPI in Python. The application uses a Plugin/Strategy architecture to support multiple manga websites. The downloading, file saving, and UI logic are already fully implemented and decoupled from the scraping logic.

**Task:**
I need you to write a new scraper plugin for a specific manga website: `[INSERT MANGA WEBSITE URL HERE]`

**Technical Details & Constraints:**
1. **Base Class:** Your scraper must inherit from `BaseMangaScraper` (located in `scrapers/base.py`).
2. **Library:** Use `BeautifulSoup4` for HTML parsing. 
3. **Methods to Implement:**
   - `@classmethod def can_handle(cls, url: str) -> bool`: Returns `True` if the URL matches the domain of the target website.
   - `def get_manga_title(self, url: str, html: str) -> str`: Parses and returns the title of the manga.
   - `def get_chapter_list(self, url: str, html: str) -> List[Dict[str, str]]`: Returns a list of dictionaries, where each dictionary contains `chapter_number` (as a string) and `url` (the absolute URL to the chapter page).
   - `def get_chapter_images(self, chapter_url: str, html: str) -> List[str]`: Parses a specific chapter's page and returns an ordered list of absolute image URLs.
4. **Output Format:**
   - Provide the complete Python code for the new scraper file. Name the file appropriately (e.g., `scrapers/my_new_site_scraper.py`).
   - Provide the exact lines of code needed to import and register this new scraper into the `_scrapers` list inside `core/factory.py`.

**What NOT to do:**
- Do NOT rewrite or modify the core downloading logic (`core/downloader.py`) or the web server (`app/main.py`).
- Do NOT include `requests` logic inside your methods. The `html` string of the page will be passed to your methods automatically by the existing `app/main.py` controller. 
- *Note:* If your scraper requires fetching secondary pages (like pagination for images inside a chapter), you may use the standard `requests` library locally within `get_chapter_images`, but try to parse from the provided `html` first if possible.

**Reference - The Base Class (`scrapers/base.py`):**
```python
from abc import ABC, abstractmethod
from typing import List, Dict

class BaseMangaScraper(ABC):
    @classmethod
    @abstractmethod
    def can_handle(cls, url: str) -> bool:
        pass

    @abstractmethod
    def get_manga_title(self, url: str, html: str) -> str:
        pass

    @abstractmethod
    def get_chapter_list(self, url: str, html: str) -> List[Dict[str, str]]:
        pass

    @abstractmethod
    def get_chapter_images(self, chapter_url: str, html: str) -> List[str]:
        pass
```

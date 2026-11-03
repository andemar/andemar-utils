import requests
from playwright.sync_api import sync_playwright

class HttpClient:
    """
    A modular HTTP client wrapper.
    Now utilizes sync Playwright for fetching HTML to bypass JavaScript-rendered content,
    while retaining `requests` for fast binary file downloading.
    """
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
            "Referer": "https://platinumlilyscan.com/"
        })
        self._playwright_cm = None
        self._playwright = None
        self._browser = None

    def _ensure_browser(self):
        if self._browser is None:
            self._playwright_cm = sync_playwright()
            self._playwright = self._playwright_cm.start()
            self._browser = self._playwright.chromium.launch(headless=True)

    def get_html(self, url: str) -> str:
        """Fetches the HTML content of a given URL using Playwright."""
        self._ensure_browser()
        page = self._browser.new_page()
        try:
            # wait_until='networkidle' ensures JS has finished executing and loading images
            page.goto(url, wait_until='networkidle', timeout=60000)
            return page.content()
        finally:
            page.close()

    def get_content(self, url: str) -> bytes:
        """Fetches raw binary content synchronously using requests."""
        response = self.session.get(url, timeout=15)
        response.raise_for_status()
        return response.content

    def close(self):
        if self._browser:
            self._browser.close()
            self._browser = None
        if self._playwright:
            self._playwright.stop()
            self._playwright = None
            self._playwright_cm = None

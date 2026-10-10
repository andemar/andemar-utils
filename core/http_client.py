import requests

class HttpClient:
    """
    A modular HTTP client wrapper. 
    Currently utilizes `requests`, but designed so that it can easily 
    be replaced with Playwright, Selenium, or httpx in the future 
    for bypassing anti-bot measures.
    """
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        })

    def get_html(self, url: str) -> str:
        """Fetches the HTML content of a given URL."""
        response = self.session.get(url, timeout=15)
        response.raise_for_status()
        return response.text

    def get_content(self, url: str) -> bytes:
        """Fetches raw binary content (e.g., for images)."""
        response = self.session.get(url, timeout=15)
        response.raise_for_status()
        return response.content

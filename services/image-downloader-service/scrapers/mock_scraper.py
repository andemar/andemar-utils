from bs4 import BeautifulSoup
from typing import List, Dict
from scrapers.base import BaseMangaScraper

class MockScraper(BaseMangaScraper):
    """
    A mock implementation of BaseMangaScraper to demonstrate the architecture.
    Assumes a hypothetical DOM structure for a fictional manga site 'mockmanga.com'.
    """

    @classmethod
    def can_handle(cls, url: str) -> bool:
        return "mockmanga.com" in url

    def get_manga_title(self, url: str, html: str) -> str:
        # In a real scraper, you might use:
        # soup = BeautifulSoup(html, 'html.parser')
        # return soup.find('h1', class_='manga-title').text.strip()
        return "Mock Manga Adventure"

    def get_chapter_list(self, url: str, html: str) -> List[Dict[str, str]]:
        # In a real scraper, parse the chapters from HTML.
        # Returning mock data for demonstration.
        return [
            {"chapter_number": "001", "url": f"{url}/chapter-1"},
            {"chapter_number": "002", "url": f"{url}/chapter-2"},
            {"chapter_number": "003", "url": f"{url}/chapter-3"},
        ]

    def get_chapter_images(self, chapter_url: str, html: str) -> List[str]:
        # In a real scraper, parse the image sources from HTML.
        # For demonstration, returning placeholder image URLs.
        return [
            "https://via.placeholder.com/600x800.png?text=Page+1",
            "https://via.placeholder.com/600x800.png?text=Page+2",
            "https://via.placeholder.com/600x800.png?text=Page+3"
        ]

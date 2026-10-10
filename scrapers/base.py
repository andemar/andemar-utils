from abc import ABC, abstractmethod
from typing import List, Dict

class BaseMangaScraper(ABC):
    """
    Abstract base class for all manga scrapers.
    Defines the contract that any new site scraper must implement.
    """
    
    @classmethod
    @abstractmethod
    def can_handle(cls, url: str) -> bool:
        """Determines if this scraper can handle the given URL."""
        pass

    @abstractmethod
    def get_manga_title(self, url: str, html: str) -> str:
        """Parses the manga title from the main manga page."""
        pass

    @abstractmethod
    def get_chapter_list(self, url: str, html: str) -> List[Dict[str, str]]:
        """
        Parses the chapter list.
        Returns a list of dictionaries with keys like 'chapter_number' and 'url'.
        Order usually should be sorted or well-defined (e.g., oldest to newest).
        """
        pass

    @abstractmethod
    def get_chapter_images(self, chapter_url: str, html: str) -> List[str]:
        """
        Parses the image URLs for a specific chapter.
        Returns a list of image URLs.
        """
        pass

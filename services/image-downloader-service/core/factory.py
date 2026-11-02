from typing import Type, List
from scrapers.base import BaseMangaScraper
from scrapers.mock_scraper import MockScraper
from scrapers.platinumlily_scraper import PlatinumLilyScraper

class ScraperFactory:
    """
    Factory to retrieve the appropriate scraper based on the URL domain.
    """
    
    # Registry of all available scrapers
    _scrapers: List[Type[BaseMangaScraper]] = [
        MockScraper,
        PlatinumLilyScraper,
        # Add new scraper classes here as they are created
    ]

    @classmethod
    def get_scraper(cls, url: str) -> BaseMangaScraper:
        """
        Iterates over the registered scrapers to find one that can handle the URL.
        Returns an instance of the matched scraper.
        """
        for scraper_cls in cls._scrapers:
            if scraper_cls.can_handle(url):
                return scraper_cls()
        raise ValueError(f"No registered scraper found for URL: {url}")

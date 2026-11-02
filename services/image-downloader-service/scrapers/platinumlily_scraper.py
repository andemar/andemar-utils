from bs4 import BeautifulSoup
from typing import List, Dict
from scrapers.base import BaseMangaScraper
import re

class PlatinumLilyScraper(BaseMangaScraper):
    """
    Scraper for Platinum Lily Scan (platinumlilyscan.com).
    """

    @classmethod
    def can_handle(cls, url: str) -> bool:
        return "platinumlilyscan.com" in url

    def get_manga_title(self, url: str, html: str) -> str:
        soup = BeautifulSoup(html, 'html.parser')
        
        # Prefer the OpenGraph title since the first <h1> is often the site logo
        meta_title = soup.find('meta', property='og:title')
        if meta_title and meta_title.get('content'):
            title = meta_title['content']
            # Remove the site branding if present
            return title.replace(' | Platinum Lily Scan', '').strip()
            
        # Fallback to the largest heading
        title_tag = soup.find('h1', class_=re.compile('text-2xl'))
        if title_tag:
            return title_tag.text.strip()
            
        return "Unknown Title"

    def get_chapter_list(self, url: str, html: str) -> List[Dict[str, str]]:
        soup = BeautifulSoup(html, 'html.parser')
        chapters = []
        
        # Find all chapter links
        for a_tag in soup.find_all('a', href=True):
            href = a_tag['href']
            if '/chapter/' in href:
                # The chapter number is usually inside a span with 'font-bold'
                span = a_tag.find('span', class_=re.compile('font-bold'))
                if span:
                    # Extracts '#040' -> '040' or '#038.5' -> '038.5'
                    ch_num = span.text.strip().replace('#', '')
                    
                    # Ensure absolute URL
                    full_url = href
                    if full_url.startswith('/'):
                        # e.g., get https://platinumlilyscan.com from the passed url
                        domain = url.split('/series/')[0]
                        full_url = domain + href
                        
                    # Avoid duplicates if the site lists the same chapter twice
                    if not any(c['chapter_number'] == ch_num for c in chapters):
                        chapters.append({
                            "chapter_number": ch_num,
                            "url": full_url
                        })
                        
        # The site lists newest first (e.g., #040 down to #001)
        # Reversing to maintain chronological order from oldest to newest
        chapters.reverse()
        return chapters

    def get_chapter_images(self, chapter_url: str, html: str) -> List[str]:
        soup = BeautifulSoup(html, 'html.parser')
        images = []
        
        # 1. Attempt to find images in standard DOM
        # The site uses '/uploads/chapters/' for manga pages
        for img in soup.find_all('img'):
            src = img.get('src')
            if src and '/uploads/chapters/' in src:
                if src.startswith('/'):
                    src = "https://platinumlilyscan.com" + src
                if src not in images:
                    images.append(src)
                    
        # 2. Check if JavaScript rendering is blocking us
        if not images and "Cargando cap" in html:
            print(f"\n[!] WARNING: Platinum Lily Scan loads chapter images dynamically via JavaScript.")
            print(f"[!] The current `HttpClient` using `requests` cannot execute JS.")
            print(f"[!] To download images from this site, `core/http_client.py` must be upgraded to use Playwright or Selenium.\n")
            
        return images

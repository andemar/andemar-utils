import os
import re
from pathlib import Path
from core.http_client import HttpClient

class Downloader:
    """
    Handles the generic downloading and file-system operations.
    """
    def __init__(self, base_folder: str, http_client: HttpClient):
        self.base_folder = Path(base_folder)
        self.http_client = http_client

    def sanitize_filename(self, name: str) -> str:
        """Removes invalid characters for directory/file names."""
        return re.sub(r'[\\/*?:"<>|]', "", name).strip()

    def download_image(self, url: str, manga_name: str, chapter_number: str, image_number: int) -> str:
        """
        Downloads an image and saves it to the strictly defined hierarchy:
        <MangaName>/Chapter <Number>/<ImageNumber>.png
        """
        clean_manga_name = self.sanitize_filename(manga_name)
        # Ensure chapter string doesn't have path issues
        clean_chapter = self.sanitize_filename(chapter_number)
        
        chapter_dir = self.base_folder / clean_manga_name / f"Chapter {clean_chapter}"
        chapter_dir.mkdir(parents=True, exist_ok=True)
        
        # Save exclusively as .png
        image_filename = f"{image_number:02d}.png"
        image_path = chapter_dir / image_filename
        
        try:
            image_bytes = self.http_client.get_content(url)
            with open(image_path, "wb") as f:
                f.write(image_bytes)
            return str(image_path)
        except Exception as e:
            # Basic error handling
            print(f"Failed to download {url}: {e}")
            raise

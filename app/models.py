from pydantic import BaseModel
from typing import Optional

class DownloadRequest(BaseModel):
    url: str
    save_folder: str
    start_chapter: Optional[str] = None
    end_chapter: Optional[str] = None
    exact_chapters: Optional[str] = None  # Comma-separated list

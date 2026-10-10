import os
from fastapi import FastAPI, Request, Form
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from core.http_client import HttpClient
from core.factory import ScraperFactory
from core.downloader import Downloader

app = FastAPI(title="Local Manga Downloader")

# Setup template directory
base_dir = os.path.dirname(os.path.abspath(__file__))
templates = Jinja2Templates(directory=os.path.join(base_dir, "templates"))

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    """Renders the main UI page."""
    return templates.TemplateResponse(request=request, name="index.html")

@app.post("/download", response_class=HTMLResponse)
async def download_manga(
    request: Request,
    url: str = Form(...),
    save_folder: str = Form(...),
    start_chapter: str = Form(None),
    end_chapter: str = Form(None),
):
    """
    Handles the downloading process based on user input.
    """
    try:
        # 1. Initialize core components
        http_client = HttpClient()
        scraper = ScraperFactory.get_scraper(url)
        downloader = Downloader(base_folder=save_folder, http_client=http_client)

        # 2. Fetch manga info
        main_page_html = http_client.get_html(url)
        manga_title = scraper.get_manga_title(url, main_page_html)
        chapters = scraper.get_chapter_list(url, main_page_html)

        # 3. Filter chapters based on input
        # Note: A more robust implementation would parse integers for proper comparison
        filtered_chapters = []
        for ch in chapters:
            ch_num = ch["chapter_number"]
            if start_chapter and ch_num < start_chapter:
                continue
            if end_chapter and ch_num > end_chapter:
                continue
            filtered_chapters.append(ch)

        # 4. Download images for selected chapters
        logs = []
        for ch in filtered_chapters:
            ch_url = ch["url"]
            ch_num = ch["chapter_number"]
            
            # Note: Fetch chapter HTML to parse images. For MockScraper, html is ignored.
            ch_html = "" 
            # If a real scraper requires html context for images:
            # ch_html = http_client.get_html(ch_url)
            
            images = scraper.get_chapter_images(ch_url, ch_html)
            for idx, img_url in enumerate(images, start=1):
                downloader.download_image(
                    url=img_url,
                    manga_name=manga_title,
                    chapter_number=ch_num,
                    image_number=idx
                )
            logs.append(f"Chapter {ch_num} downloaded successfully.")

        message = f"Download complete for {manga_title}! Saved to {save_folder}"
        return templates.TemplateResponse(request=request, name="index.html", context={
            "message": message,
            "logs": logs
        })

    except Exception as e:
        error_msg = f"An error occurred: {str(e)}"
        return templates.TemplateResponse(request=request, name="index.html", context={
            "error": error_msg
        })

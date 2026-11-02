import os
import sys
import asyncio
import subprocess

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())
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


def open_folder_dialog(initial_dir: str = "") -> dict:
    """
    Opens a native folder selection dialog on the host system,
    or returns the mounted container directory when running in Docker.
    """
    # 0. Detect Docker container environment
    if os.path.exists("/.dockerenv") or os.path.isdir("/downloads"):
        return {
            "path": "/downloads",
            "is_container": True,
            "message": "Running inside Docker container. Using host-mounted '/downloads' directory."
        }

    norm_initial = ""
    if initial_dir and os.path.isdir(initial_dir):
        norm_initial = os.path.abspath(initial_dir)

    # 1. Tkinter in isolated subprocess (clean, topmost, native OS dialog)
    try:
        code = (
            "import os, sys\n"
            "try:\n"
            "    import tkinter as tk\n"
            "    from tkinter import filedialog\n"
            "    root = tk.Tk()\n"
            "    root.withdraw()\n"
            "    root.attributes('-topmost', True)\n"
            "    root.lift()\n"
            "    root.focus_force()\n"
            "    init_dir = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else None\n"
            "    path = filedialog.askdirectory(title='Select Save Folder', initialdir=init_dir)\n"
            "    root.destroy()\n"
            "    if path:\n"
            "        print(path)\n"
            "except Exception:\n"
            "    sys.exit(1)\n"
        )
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        res = subprocess.run(
            [sys.executable, "-c", code, norm_initial],
            capture_output=True,
            text=True,
            timeout=120,
            creationflags=creationflags
        )
        if res.returncode == 0:
            selected = res.stdout.strip()
            if selected:
                return {"path": os.path.normpath(selected), "is_container": False}
            return {"path": "", "is_container": False}
    except Exception:
        pass

    # 2. Windows Fallback via PowerShell
    if sys.platform == "win32":
        try:
            ps_script = (
                "$form = New-Object System.Windows.Forms.FolderBrowserDialog; "
                "$form.Description = 'Select Manga Download Folder'; "
                "$form.ShowNewFolderButton = $true; "
            )
            if norm_initial:
                ps_script += f"$form.SelectedPath = '{norm_initial}'; "
            ps_script += (
                "$top = New-Object System.Windows.Forms.Form; "
                "$top.TopMost = $true; "
                "if ($form.ShowDialog($top) -eq [System.Windows.Forms.DialogResult]::OK) { "
                "    Write-Output $form.SelectedPath "
                "}"
            )
            res = subprocess.run(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", f"Add-Type -AssemblyName System.Windows.Forms; {ps_script}"],
                capture_output=True,
                text=True,
                timeout=120,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
            )
            if res.returncode == 0:
                selected = res.stdout.strip()
                if selected:
                    return {"path": os.path.normpath(selected), "is_container": False}
        except Exception:
            pass

    return {"path": "", "is_container": False}


@app.get("/api/select-folder")
async def select_folder_endpoint(initial_dir: str = ""):
    """Returns selected folder or container-mounted folder path."""
    return await asyncio.to_thread(open_folder_dialog, initial_dir)


@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    """Renders the main UI page."""
    return templates.TemplateResponse(request=request, name="index.html")


@app.post("/download", response_class=HTMLResponse)
def download_manga(
    request: Request,
    url: str = Form(...),
    save_folder: str = Form(...),
    start_chapter: str = Form(None),
    end_chapter: str = Form(None),
):
    """
    Handles the downloading process based on user input.
    """
    http_client = None
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

            try:
                ch_html = http_client.get_html(ch_url)
            except Exception as e:
                logs.append(f"Chapter {ch_num} failed to fetch HTML: {e}")
                continue

            images = scraper.get_chapter_images(ch_url, ch_html)
            if not images:
                logs.append(f"Chapter {ch_num} skipped: No images found (Anti-bot or JS required).")
                continue

            for idx, img_url in enumerate(images, start=1):
                downloader.download_image(
                    url=img_url,
                    manga_name=manga_title,
                    chapter_number=ch_num,
                    image_number=idx,
                )
            logs.append(f"Chapter {ch_num} downloaded successfully.")

        message = f"Download complete for {manga_title}! Saved to {save_folder}"
        return templates.TemplateResponse(
            request=request,
            name="index.html",
            context={
                "url": url,
                "save_folder": save_folder,
                "start_chapter": start_chapter or "",
                "end_chapter": end_chapter or "",
                "message": message,
                "logs": logs,
            },
        )

    except Exception as e:
        import traceback

        traceback.print_exc()
        error_msg = f"An error occurred: {str(e)}"
        return templates.TemplateResponse(
            request=request,
            name="index.html",
            context={
                "url": url,
                "save_folder": save_folder,
                "start_chapter": start_chapter or "",
                "end_chapter": end_chapter or "",
                "error": error_msg,
            },
        )
    finally:
        if http_client:
            http_client.close()


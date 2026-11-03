from playwright.sync_api import sync_playwright

def test():
    print("starting")
    p = sync_playwright().start()
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto("https://example.com")
    print(page.title())
    browser.close()
    p.stop()
    print("done")

if __name__ == "__main__":
    test()

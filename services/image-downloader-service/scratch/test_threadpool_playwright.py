from core.http_client import HttpClient
import concurrent.futures
import traceback

def test_download():
    client = None
    try:
        print("Initializing HttpClient...")
        client = HttpClient()
        print("Getting HTML...")
        html = client.get_html("https://example.com")
        print(f"HTML retrieved, length: {len(html)}")
    except Exception as e:
        print(f"Error string: {str(e)}")
        traceback.print_exc()
    finally:
        if client:
            client.close()
            print("Client closed.")

if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        future = executor.submit(test_download)
        future.result()

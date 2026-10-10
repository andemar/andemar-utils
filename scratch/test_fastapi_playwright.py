import requests
from fastapi import FastAPI
from fastapi.testclient import TestClient
from core.http_client import HttpClient

app = FastAPI()

@app.get("/test")
def test_endpoint():
    try:
        client = HttpClient()
        html = client.get_html("https://example.com")
        client.close()
        return {"status": "ok", "html_len": len(html)}
    except Exception as e:
        return {"status": "error", "error": str(e)}

if __name__ == "__main__":
    client = TestClient(app)
    response = client.get("/test")
    print("Response:", response.json())

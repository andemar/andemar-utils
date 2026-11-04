.PHONY: help hub-up hub-down hub-build build-all build-spam build-anitracker build-pdf build-downloader clean

help:
	@echo "Available commands:"
	@echo "  make hub-up        - Start the Application Hub (UI + Backend Orchestrator)"
	@echo "  make hub-down      - Stop the Application Hub"
	@echo "  make hub-build     - Rebuild the Hub Backend and UI containers"
	@echo "  make build-all     - Build all child microservice Docker images"
	@echo "  make build-spam    - Build spam-service:latest (GPU classifier)"
	@echo "  make build-anitracker - Build anitracker-app-service:latest (Web UI)"
	@echo "  make build-pdf     - Build pdf-cleaner-service:latest (REST API)"
	@echo "  make build-downloader - Build image-downloader-service:latest (Web UI)"
	@echo "  make clean         - Prune dangling containers and networks"

hub-up:
	docker compose up -d

hub-down:
	docker compose down

hub-build:
	docker compose build

build-spam:
	docker build -t spam-service:latest services/spam-service

build-anitracker:
	docker build -t anitracker-app-service:latest services/anitracker-app-service

build-pdf:
	docker build -t pdf-cleaner-service:latest services/pdf-cleaner-service

build-downloader:
	docker build -t image-downloader-service:latest services/image-downloader-service

build-all: build-spam build-anitracker build-pdf build-downloader

clean:
	docker container prune -f


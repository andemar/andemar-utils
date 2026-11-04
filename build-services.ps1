<#
.SYNOPSIS
    Builds child microservice Docker images for the Application Hub.
.EXAMPLE
    .\build-services.ps1 -All
    .\build-services.ps1 -Spam
    .\build-services.ps1 -AniTracker
    .\build-services.ps1 -Pdf
    .\build-services.ps1 -Downloader
#>
param(
    [switch]$All,
    [switch]$Spam,
    [switch]$AniTracker,
    [switch]$Pdf,
    [switch]$Downloader
)

# If no specific flag is provided, display menu or build all
if (-not ($All -or $Spam -or $AniTracker -or $Pdf -or $Downloader)) {
    Write-Host "No specific service specified. Building all services..." -ForegroundColor Cyan
    $All = $true
}

if ($All -or $Spam) {
    Write-Host "`n[1/4] Building spam-service:latest..." -ForegroundColor Green
    docker build -t spam-service:latest services/spam-service
}

if ($All -or $AniTracker) {
    Write-Host "`n[2/4] Building anitracker-app-service:latest..." -ForegroundColor Green
    docker build -t anitracker-app-service:latest services/anitracker-app-service
}

if ($All -or $Pdf) {
    Write-Host "`n[3/4] Building pdf-cleaner-service:latest..." -ForegroundColor Green
    docker build -t pdf-cleaner-service:latest services/pdf-cleaner-service
}

if ($All -or $Downloader) {
    Write-Host "`n[4/4] Building image-downloader-service:latest..." -ForegroundColor Green
    docker build -t image-downloader-service:latest services/image-downloader-service
}

Write-Host "`nBuild complete! Verify with: docker images" -ForegroundColor Cyan


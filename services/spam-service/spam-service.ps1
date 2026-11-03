<#
.SYNOPSIS
    CLI wrapper for spam-service Docker container with NVIDIA GPU.
.DESCRIPTION
    Exposes the 'train' and 'scan' utilities outside the Docker container.
    Handles host directory path resolution, Docker volume mounting, and NVIDIA GPU runtime configuration.
.EXAMPLE
    # Train using separate spam and not_spam folders:
    .\spam-service.ps1 train -SpamDir "F:\AI\manga_spam" -NotSpamDir "F:\AI\manga_images" -OutputDir "F:\AI\output" -Epochs 30

    # Train using a root folder containing 'spam/' and 'not_spam/':
    .\spam-service.ps1 train -DataDir "F:\AI\dataset" -OutputDir "F:\AI\output" -Epochs 30

    # Scan a folder of images with detected spam moved to subfolder:
    .\spam-service.ps1 scan -Folder "F:\AI\my_manga" -Model "F:\AI\output\best_model.pt" -MoveSpam

    # Scan with custom threshold and in-place renaming:
    .\spam-service.ps1 scan -Folder "F:\AI\my_manga" -Model "F:\AI\output\best_model.pt" -RenameSpam -Threshold 0.83
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('train', 'scan', 'build', 'help')]
    [string]$Action,

    # --- Training parameters ---
    [string]$SpamDir,
    [string]$NotSpamDir,
    [string]$DataDir,
    [string]$OutputDir = "./output",
    [int]$Epochs,
    [int]$BatchSize,
    [double]$LR,
    [int]$ImgSize,

    # --- Scanning parameters ---
    [string]$Folder,
    [string]$Model = "./output/best_model.pt",
    [double]$Threshold,
    [switch]$MoveSpam,
    [switch]$RenameSpam,

    # --- Docker image tag ---
    [string]$ImageTag = "spam-service"
)

function Resolve-FullPath {
    param([string]$Path, [bool]$MustExist = $true)
    if (-not $Path) { return $null }
    if ($MustExist -and -not (Test-Path $Path)) {
        throw "Path does not exist: $Path"
    }
    if (Test-Path $Path) {
        return (Get-Item -LiteralPath $Path).FullName
    }
    # If doesn't exist yet (e.g. output dir to be created)
    $parent = Split-Path -Parent $Path
    if (-not $parent) { $parent = Get-Location }
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    return [System.IO.Path]::GetFullPath($Path)
}

switch ($Action) {
    'build' {
        Write-Host "Building Docker image '$ImageTag'..." -ForegroundColor Cyan
        $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
        docker build -t $ImageTag $scriptDir
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Image '$ImageTag' built successfully!" -ForegroundColor Green
        }
        break
    }

    'train' {
        $mountArgs = @()
        $trainArgs = @()

        # Output directory mount
        $resolvedOutput = Resolve-FullPath -Path $OutputDir -MustExist $false
        if (-not (Test-Path $resolvedOutput)) {
            New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
        }
        $mountArgs += "-v", "`"${resolvedOutput}:/output`""

        # Dataset mounts
        if ($SpamDir -and $NotSpamDir) {
            $resolvedSpam = Resolve-FullPath -Path $SpamDir -MustExist $true
            $resolvedNotSpam = Resolve-FullPath -Path $NotSpamDir -MustExist $true
            $mountArgs += "-v", "`"${resolvedSpam}:/dataset/spam:ro`""
            $mountArgs += "-v", "`"${resolvedNotSpam}:/dataset/not_spam:ro`""
        } elseif ($DataDir) {
            $resolvedData = Resolve-FullPath -Path $DataDir -MustExist $true
            $mountArgs += "-v", "`"${resolvedData}:/dataset:ro`""
        } else {
            throw "You must provide either -SpamDir and -NotSpamDir OR -DataDir to train."
        }

        if ($Epochs) { $trainArgs += "--epochs", $Epochs }
        if ($BatchSize) { $trainArgs += "--batch_size", $BatchSize }
        if ($LR) { $trainArgs += "--lr", $LR }
        if ($ImgSize) { $trainArgs += "--img_size", $ImgSize }

        Write-Host "Starting training with NVIDIA GPU inside Docker..." -ForegroundColor Cyan
        $ttyArg = if ([System.Console]::IsInputRedirected) { "-i" } else { "-it" }
        $cmd = "docker run --rm $ttyArg --gpus all --shm-size=4g $mountArgs $ImageTag train $trainArgs"
        Invoke-Expression $cmd
        break
    }

    'scan' {
        if (-not $Folder) {
            throw "Parameter -Folder is required for scan action."
        }
        $resolvedFolder = Resolve-FullPath -Path $Folder -MustExist $true

        $mountArgs = @("-v", "`"${resolvedFolder}:/scan`"")
        $scanArgs = @()

        if ($Model) {
            $resolvedModel = Resolve-FullPath -Path $Model -MustExist $true
            $modelItem = Get-Item -LiteralPath $resolvedModel
            if ($modelItem.PSIsContainer) {
                $mountArgs += "-v", "`"${resolvedModel}:/models:ro`""
                $scanArgs += "--model", "/models/best_model.pt"
            } else {
                $modelDir = $modelItem.DirectoryName
                $modelFile = $modelItem.Name
                $mountArgs += "-v", "`"${modelDir}:/models:ro`""
                $scanArgs += "--model", "/models/$modelFile"
            }
        }

        if ($Threshold) { $scanArgs += "--threshold", $Threshold }
        if ($MoveSpam) { $scanArgs += "--move_spam" }
        if ($RenameSpam) { $scanArgs += "--rename_spam" }

        Write-Host "Starting scan with NVIDIA GPU inside Docker..." -ForegroundColor Cyan
        $ttyArg = if ([System.Console]::IsInputRedirected) { "-i" } else { "-it" }
        $cmd = "docker run --rm $ttyArg --gpus all --shm-size=4g $mountArgs $ImageTag scan $scanArgs"
        Invoke-Expression $cmd
        break
    }

    'help' {
        Get-Help $MyInvocation.MyCommand.Path -Detailed
        break
    }
}

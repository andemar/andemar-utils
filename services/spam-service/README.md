# Docker Usage (NVIDIA GPU Accelerated)

This service is containerized with full NVIDIA CUDA GPU acceleration, allowing you to train models and scan folders directly from your host machine without local PyTorch installations, and without copying dataset files into the container.

---

## 1. Build the Docker Image

From the `services/spam-service` directory:

```bash
docker build -t spam-service .
```
*(Or use `.\spam-service.ps1 build` on Windows / `./spam-service.sh build` on Linux).*

---

## 2. Using the CLI Helper Scripts (Recommended)

Helper scripts are provided to automatically map host folders as Docker volume mounts and pass through your NVIDIA GPU.

### Training (`train`)
Point directly to your 2 separate host dataset folders (spam and not spam) and your desired output directory. **No files are copied into the container!**

```powershell
# Windows (PowerShell)
.\spam-service.ps1 train `
  -SpamDir "F:\AI\Training\manga_spam" `
  -NotSpamDir "F:\AI\Training\manga_images" `
  -OutputDir "F:\AI\Training\output" `
  -Epochs 30 `
  -BatchSize 32
```

```bash
# Linux / WSL (Bash)
./spam-service.sh train \
  --spam-dir "/path/to/manga_spam" \
  --not-spam-dir "/path/to/manga_images" \
  --output-dir "/path/to/output" \
  --epochs 30 \
  --batch_size 32
```

> **Note:** The trained `best_model.pt` and `training_log.csv` are written directly to your host's `-OutputDir`.

---

### Scanning a Folder (`scan`)
Point to any folder of images on your computer to scan with the trained model:

```powershell
# Windows (PowerShell) - Flag and move detected spam to 'spam_detected/' subfolder
.\spam-service.ps1 scan `
  -Folder "F:\AI\MangaChapters\Chapter01" `
  -Model "F:\AI\Training\output\best_model.pt" `
  -MoveSpam `
  -Threshold 0.83
```

```powershell
# Windows (PowerShell) - Rename detected spam in-place (e.g. page01.jpg -> page01-spam.jpg)
.\spam-service.ps1 scan `
  -Folder "F:\AI\MangaChapters\Chapter01" `
  -Model "F:\AI\Training\output\best_model.pt" `
  -RenameSpam `
  -Threshold 0.83
```

```bash
# Linux / WSL (Bash)
./spam-service.sh scan \
  --folder "/path/to/chapter_images" \
  --model "/path/to/output/best_model.pt" \
  --move-spam \
  --threshold 0.83
```

---

## 3. Direct Docker Commands (Manual)

### Training
Mount your spam folder to `/dataset/spam:ro`, clean images to `/dataset/not_spam:ro`, and output folder to `/output`:

```powershell
docker run --rm -it --gpus all --shm-size=4g `
  -v "F:\AI\Training\manga_spam:/dataset/spam:ro" `
  -v "F:\AI\Training\manga_images:/dataset/not_spam:ro" `
  -v "F:\AI\Training\output:/output" `
  spam-service train --epochs 30 --batch_size 32
```

### Scan (Inference)
Mount your images folder to `/scan` and the trained model folder to `/models:ro`:

```powershell
docker run --rm -it --gpus all --shm-size=4g `
  -v "G:\Books\Source\manga\workspace\test:/scan" `
  -v "F:\AI\Training\output:/models:ro" `
  spam-service scan --model /models/best_model.pt --rename_spam --threshold 0.83
```

---

# Quick Commands Summary

### Docker (Recommended — GPU Accelerated)
```powershell
# Build image
docker build -t spam-service .

# Training:
docker run --rm -it --gpus all --shm-size=4g `
  -v "F:\AI\Training\manga_spam:/dataset/spam:ro" `
  -v "F:\AI\Training\manga_images:/dataset/not_spam:ro" `
  -v "F:\AI\Training\output:/output" `
  spam-service train --epochs 30 --batch_size 32

# Scan:
docker run --rm -it --gpus all --shm-size=4g `
  -v "G:\Books\Source\manga\workspace\test:/scan" `
  -v "F:\AI\Training\output:/models:ro" `
  spam-service scan --model /models/best_model.pt --rename_spam --threshold 0.83
```

### Local / Without Docker (Legacy)
```bash
pip install torch==2.3.0 torchvision==0.18.0 --index-url https://download.pytorch.org/whl/cu121
pip install -r requirements.txt

Copy-Item "F:\AI\Training\manga_spam\*"   -Destination ".\dataset\spam\"     -Recurse
Copy-Item "F:\AI\Training\manga_images\*" -Destination ".\dataset\not_spam\" -Recurse

python train.py --epochs 30
python scan_folder.py --folder ./my_manga_folder --move_spam
python scan_folder.py --folder './my_manga_folder' --rename_spam --threshold 0.83
```

# Spam vs. Manga Binary Image Classifier

An EfficientNet-B4 based binary classifier that detects **spam images** (translator site pages, ad-heavy pages, watermarked images) vs. **clean manga panels**.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Python | 3.11+ |
| CUDA | 12.x (NVIDIA drivers 525+) |
| GPU | NVIDIA GPU with ≥8 GB VRAM (tested on RTX 4070 Ti SUPER) |

> **IMPORTANT:** CPU training is not supported. The scripts will raise an error if no CUDA GPU is detected.

---

## 1. Install Dependencies

### Step 1 — Install PyTorch with CUDA 12.1 support

```bash
pip install torch==2.3.0 torchvision==0.18.0 --index-url https://download.pytorch.org/whl/cu121
```

### Step 2 — Install remaining packages

```bash
pip install -r requirements.txt
```

### Verify your GPU is recognized

```bash
python -c "import torch; print('CUDA available:', torch.cuda.is_available()); print('GPU:', torch.cuda.get_device_name(0))"
```

---

## 2. Organize the Dataset

The dataset must follow this folder structure:

```
dataset/
├── spam/         ← Spam images (translator pages, watermarks, ads)
└── not_spam/     ← Clean manga panel images
```

### Option A: Copy images into the dataset folder

```
# Windows (PowerShell)
Copy-Item "F:\AI\Training\manga_spam\*"   -Destination ".\dataset\spam\"     -Recurse
Copy-Item "F:\AI\Training\manga_images\*" -Destination ".\dataset\not_spam\" -Recurse
```

### Option B: Point `--data_dir` directly at your source

```bash
python train.py --data_dir F:\AI\Training --epochs 30
```

In this case, your `F:\AI\Training` folder must contain `spam\` and `not_spam\` subfolders (or rename `manga_spam` → `spam` and `manga_images` → `not_spam`).

### Supported image formats

`jpg`, `jpeg`, `png`, `webp`, `bmp`, `gif`

---

## 3. Train the Model

### Basic training (recommended defaults)

```bash
python train.py --epochs 30
```

### Full options

```bash
python train.py \
  --data_dir  ./dataset \
  --epochs    30 \
  --batch_size 32 \
  --lr        1e-4 \
  --output_dir ./output \
  --img_size  380
```

| Argument | Default | Description |
|---|---|---|
| `--data_dir` | `./dataset` | Root folder with `spam/` and `not_spam/` |
| `--epochs` | `30` | Total training epochs |
| `--batch_size` | `32` | Images per batch (reduce if OOM) |
| `--lr` | `1e-4` | Phase 1 learning rate |
| `--output_dir` | `./output` | Where to save checkpoint and log |
| `--img_size` | `380` | Input resolution (EfficientNet-B4 native) |

### What to expect during training

```
[GPU] Using: NVIDIA GeForce RTX 4070 Ti SUPER
[Dataset] Total images: 2400
[Dataset] spam: 400 | not_spam: 2000
[Dataset] Imbalance ratio: 5.0x (not_spam/spam)
[Loss] BCEWithLogitsLoss pos_weight = 5.0000

[Phase 1] Frozen base — trainable params: 3,202
...
Epoch [  1/ 30] (P1) — 18.3s
  Train | Loss: 0.4821 | Acc: 0.7812 | P: 0.7500 | R: 0.8200 | F1: 0.7835
  Val   | Loss: 0.3912 | Acc: 0.8400 | P: 0.8100 | R: 0.8700 | F1: 0.8389
  [Checkpoint] Saved best model → ./output/best_model.pt  (epoch 1, val F1=0.8389)
```

### Output files

```
output/
├── best_model.pt       ← Best checkpoint (highest validation F1)
└── training_log.csv    ← Per-epoch metrics (loss, acc, precision, recall, F1, GPU mem)
```

---

## 4. Run Inference — Scan a Folder

```bash
python scan_folder.py --folder ./my_manga_folder
```

### With optional flags

```bash
# Move detected spam to a subfolder automatically
python scan_folder.py --folder ./my_manga_folder --move_spam

# Adjust confidence threshold (default 0.5)
python scan_folder.py --folder ./my_manga_folder --threshold 0.65

# Point to a specific model checkpoint
python scan_folder.py --folder ./my_manga_folder --model ./output/best_model.pt
```

| Argument | Default | Description |
|---|---|---|
| `--folder` | *(required)* | Folder of images to scan |
| `--model` | `./output/best_model.pt` | Path to trained checkpoint |
| `--threshold` | `0.5` | Spam confidence threshold |
| `--move_spam` | off | Move spam images to `spam_detected/` |
| `--img_size` | `380` | Must match training resolution |

---

## 5. Interpret the Output Report

```
=================================================================
  SPAM DETECTION REPORT
  Folder    : C:\Users\you\my_manga_folder
  Scanned   : 150 images
  Threshold : 0.50
  Flagged   : 3 spam image(s)
=================================================================

  Confidence  File
  ----------  --------------------------------------------------
       97.3%  chapter_01_p001.jpg
       88.1%  ad_page_translator.jpg
       61.4%  suspicious_watermark.png

=================================================================
```

- **Confidence** — the model's estimated probability that the image is spam (0–100%).
- Images are sorted from most to least suspicious.
- Adjust `--threshold` up (e.g., `0.65`) to reduce false positives, or down to catch more borderline cases.

---

## 6. Add More Training Images and Retrain

The dataset is folder-based — just drop new images into the right subfolder:

```
dataset/spam/       ← add new spam examples here
dataset/not_spam/   ← add new clean manga examples here
```

Then retrain from scratch (the best checkpoint is automatically overwritten):

```bash
python train.py --epochs 30
```

> **Tip:** If you add a large batch of images and the class balance changes significantly, the `pos_weight` in the loss function updates automatically — no code changes needed.

---

## Project Structure

```
training-spam-ai/
├── dataset/
│   ├── spam/           ← spam training images
│   └── not_spam/       ← manga training images
├── output/
│   ├── best_model.pt   ← saved by train.py
│   └── training_log.csv
├── model.py            ← EfficientNet-B4 model definition
├── dataset.py          ← Dataset class + image transforms
├── utils.py            ← metrics, class weights, checkpoint helpers
├── train.py            ← CLI training script
├── scan_folder.py      ← CLI inference / folder scanner
├── requirements.txt    ← Python dependencies
└── README.md           ← this file
```

---

## How Class Imbalance Is Handled

With ~400 spam and ~2000 manga images (5:1 ratio), two strategies are combined:

1. **`WeightedRandomSampler`** — During training, spam images are oversampled so each batch sees a roughly 50/50 class distribution.
2. **`BCEWithLogitsLoss(pos_weight=5.0)`** — The loss function penalizes the model 5× more for missing a spam image (false negative) than for falsely flagging a clean image.

Both are recalculated automatically when the dataset grows.

---

## Two-Phase Fine-Tuning

| Phase | Epochs | Trainable Layers | Learning Rate |
|---|---|---|---|
| Phase 1 | 1–10 | Classifier head only | `1e-4` (CLI `--lr`) |
| Phase 2 | 11–30 | Last 2 EfficientNet blocks + head | `1e-5` (fixed) |

This prevents catastrophic forgetting of ImageNet features early in training.

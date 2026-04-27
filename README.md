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

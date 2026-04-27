"""
scan_folder.py
--------------
Inference script for the spam vs. manga binary image classifier.

Scans a folder of images, runs each through the trained EfficientNet-B4 model,
and reports which images are classified as SPAM with their confidence scores.

Usage:
    python scan_folder.py --folder ./my_manga_folder
    python scan_folder.py --folder ./my_manga_folder --move_spam
    python scan_folder.py --folder ./my_manga_folder --threshold 0.6 --model ./output/best_model.pt

Requirements:
  - A trained model checkpoint at --model path (produced by train.py).
  - CUDA GPU is required for inference.
"""

import argparse
import shutil
import sys
from pathlib import Path
from typing import List, Tuple

import torch
from PIL import Image
from tqdm import tqdm

from model import build_model
from dataset import get_val_transforms
from utils import load_checkpoint

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
DEFAULT_MODEL_PATH: str = "./output/best_model.pt"
DEFAULT_THRESHOLD: float = 0.83
SPAM_SUBDIR: str = "spam_detected"

# Supported image extensions to scan
SCAN_EXTENSIONS: Tuple[str, ...] = (".jpg", ".jpeg", ".png", ".webp")


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan a folder of images and flag spam using the trained classifier"
    )
    parser.add_argument(
        "--folder",
        type=str,
        required=True,
        help="Path to the folder of images to scan",
    )
    parser.add_argument(
        "--model",
        type=str,
        default=DEFAULT_MODEL_PATH,
        help=f"Path to the trained model checkpoint (default: {DEFAULT_MODEL_PATH})",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=DEFAULT_THRESHOLD,
        help=(
            f"Confidence threshold for spam decision (default: {DEFAULT_THRESHOLD}). "
            "Images with spam probability >= threshold are flagged."
        ),
    )
    parser.add_argument(
        "--move_spam",
        action="store_true",
        default=False,
        help=(
            "If set, move flagged spam images into a 'spam_detected/' subfolder "
            "inside --folder."
        ),
    )
    parser.add_argument(
        "--rename_spam",
        action="store_true",
        default=False,
        help="If set, rename flagged spam images in-place by appending '-spam' to the filename (e.g. page01.jpg → page01-spam.jpg).",
    )
    parser.add_argument(
        "--img_size",
        type=int,
        default=380,
        help="Image size expected by the model (default: 380)",
    )
    return parser.parse_args()


# ---------------------------------------------------------------------------
# Image discovery
# ---------------------------------------------------------------------------

def find_images(folder: Path) -> List[Path]:
    """
    Recursively collect all supported image files under `folder`.

    Args:
        folder: Root directory to search.

    Returns:
        Sorted list of image Paths.
    """
    images: List[Path] = []
    for ext in SCAN_EXTENSIONS:
        # glob is case-sensitive on Linux; on Windows it's not, but we handle both
        images.extend(folder.rglob(f"*{ext}"))
        images.extend(folder.rglob(f"*{ext.upper()}"))

    # Deduplicate (can happen on case-insensitive file systems)
    seen = set()
    unique: List[Path] = []
    for p in images:
        key = str(p).lower()
        if key not in seen:
            seen.add(key)
            unique.append(p)

    return sorted(unique)


# ---------------------------------------------------------------------------
# Inference
# ---------------------------------------------------------------------------

def run_inference(
    model: torch.nn.Module,
    image_paths: List[Path],
    transform,
    device: torch.device,
    threshold: float,
) -> List[Tuple[Path, float, bool]]:
    """
    Run the model on every image and return classification results.

    Args:
        model       : Trained model (already on `device`, eval mode).
        image_paths : List of image file paths to process.
        transform   : Validation transform pipeline.
        device      : CUDA device.
        threshold   : Sigmoid threshold for spam decision.

    Returns:
        List of (image_path, spam_probability, is_spam) tuples.
    """
    results: List[Tuple[Path, float, bool]] = []

    for img_path in tqdm(image_paths, desc="Scanning images", unit="img"):
        try:
            image = Image.open(img_path).convert("RGB")
        except Exception as exc:
            tqdm.write(f"  [WARNING] Could not open {img_path.name}: {exc}")
            continue

        # Apply validation transforms (resize + normalize)
        tensor = transform(image).unsqueeze(0)  # Add batch dimension: (1, C, H, W)
        tensor = tensor.to(device, non_blocking=True)

        with torch.no_grad():
            # AMP autocast for faster inference on RTX GPUs
            with torch.cuda.amp.autocast():
                logit = model(tensor).squeeze()        # scalar logit
            prob = torch.sigmoid(logit).item()         # spam probability [0, 1]

        is_spam = prob >= threshold
        results.append((img_path, prob, is_spam))

    return results


# ---------------------------------------------------------------------------
# Report + optional move
# ---------------------------------------------------------------------------

def print_report(
    results: List[Tuple[Path, float, bool]],
    threshold: float,
    folder: Path,
) -> int:
    """
    Print the spam detection report and return the count of flagged images.

    Args:
        results   : Output of run_inference().
        threshold : The threshold that was used.
        folder    : The scanned folder (for display).

    Returns:
        Number of images flagged as spam.
    """
    spam_results = [(p, prob) for p, prob, is_spam in results if is_spam]
    total = len(results)
    spam_count = len(spam_results)

    print("\n" + "=" * 65)
    print(f"  SPAM DETECTION REPORT")
    print(f"  Folder    : {folder}")
    print(f"  Scanned   : {total} images")
    print(f"  Threshold : {threshold:.2f}")
    print(f"  Flagged   : {spam_count} spam image(s)")
    print("=" * 65)

    if spam_count == 0:
        print("  No spam detected. All images look clean!")
    else:
        print(f"\n  {'Confidence':>10}  File")
        print(f"  {'-'*10}  {'-'*50}")
        # Sort by confidence descending (most suspicious first)
        for img_path, prob in sorted(spam_results, key=lambda x: x[1], reverse=True):
            print(f"  {prob:>9.1%}  {img_path.name}")

    print("=" * 65)
    return spam_count


def move_spam_images(
    results: List[Tuple[Path, float, bool]],
    folder: Path,
) -> None:
    """
    Move all flagged spam images into `<folder>/spam_detected/`.

    Args:
        results : Output of run_inference().
        folder  : The scanned root folder.
    """
    spam_dir = folder / SPAM_SUBDIR
    spam_dir.mkdir(exist_ok=True)

    moved = 0
    for img_path, prob, is_spam in results:
        if not is_spam:
            continue
        dest = spam_dir / img_path.name
        # Handle filename collisions by appending a counter
        counter = 1
        while dest.exists():
            dest = spam_dir / f"{img_path.stem}_{counter}{img_path.suffix}"
            counter += 1
        shutil.move(str(img_path), str(dest))
        moved += 1

    print(f"\n[Move] {moved} spam image(s) moved → {spam_dir}")


def rename_spam_images(
    results: List[Tuple[Path, float, bool]],
) -> None:
    """
    Rename flagged spam images in-place by appending '-spam' before the extension.

    Example: page01.jpg → page01-spam.jpg

    Already-suffixed files (ending in '-spam') are skipped to avoid double-renaming.

    Args:
        results : Output of run_inference().
    """
    renamed = 0
    for img_path, prob, is_spam in results:
        if not is_spam:
            continue
        # Skip if the file was already renamed in a previous run
        if img_path.stem.endswith(".spam"):
            tqdm.write(f"  [Rename] Skipped (already suffixed): {img_path.name}")
            continue
        new_name = img_path.parent / f"{img_path.stem}.spam{img_path.suffix}"
        # Handle collisions by appending a counter before the suffix
        counter = 1
        while new_name.exists():
            new_name = img_path.parent / f"{img_path.stem}.spam_{counter}{img_path.suffix}"
            counter += 1
        img_path.rename(new_name)
        renamed += 1

    print(f"\n[Rename] {renamed} spam image(s) renamed with '.spam' suffix")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    args = parse_args()

    # ---- GPU enforcement ----
    if not torch.cuda.is_available():
        print(
            "\n[ERROR] No CUDA GPU detected!\n"
            "This inference script requires an NVIDIA GPU with CUDA support.\n"
            "Ensure your PyTorch CUDA build is correctly installed.\n"
            "Verify: python -c \"import torch; print(torch.cuda.is_available())\""
        )
        sys.exit(1)

    device = torch.device("cuda")
    print(f"[GPU] Using: {torch.cuda.get_device_name(0)}")

    # ---- Validate inputs ----
    folder = Path(args.folder)
    if not folder.is_dir():
        print(f"[ERROR] Folder not found: {folder}")
        sys.exit(1)

    model_path = Path(args.model)
    if not model_path.is_file():
        print(f"[ERROR] Model checkpoint not found: {model_path}")
        print("Train the model first: python train.py --epochs 30")
        sys.exit(1)

    # ---- Load model ----
    print(f"[Model] Loading checkpoint: {model_path}")
    model = build_model()
    meta = load_checkpoint(model, str(model_path), device)
    model = model.to(device)
    model.eval()
    print(f"[Model] Loaded — trained epoch: {meta.get('epoch', '?')} | val F1: {meta.get('val_f1', '?'):.4f}")

    # ---- Find images ----
    print(f"\n[Scan] Searching for images in: {folder}")
    image_paths = find_images(folder)

    # Exclude the spam_detected subfolder to avoid re-scanning moved files
    spam_dir = folder / SPAM_SUBDIR
    image_paths = [p for p in image_paths if spam_dir not in p.parents and p.parent != spam_dir]

    if not image_paths:
        print("[Scan] No supported images found. Supported formats: jpg, jpeg, png, webp")
        sys.exit(0)

    print(f"[Scan] Found {len(image_paths)} image(s) to process")

    # ---- Run inference ----
    transform = get_val_transforms(args.img_size)
    results = run_inference(model, image_paths, transform, device, args.threshold)

    # ---- Print report ----
    spam_count = print_report(results, args.threshold, folder)

    # ---- Optionally move spam images ----
    if args.move_spam and spam_count > 0:
        move_spam_images(results, folder)
    elif args.move_spam and spam_count == 0:
        print("\n[Move] No spam images to move.")

    # ---- Optionally rename spam images in-place ----
    if args.rename_spam and spam_count > 0:
        rename_spam_images(results)
    elif args.rename_spam and spam_count == 0:
        print("\n[Rename] No spam images to rename.")


if __name__ == "__main__":
    main()

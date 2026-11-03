"""
dataset.py
----------
Dataset class and image transforms for the spam vs. manga classifier.

Expected folder structure:
    <data_dir>/
        spam/       <- spam images (label = 1)
        not_spam/   <- manga/clean images (label = 0)

Supported image formats: jpg, jpeg, png, webp, bmp, gif (PIL-readable).
"""

import os
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import torch
from torch.utils.data import Dataset
from torchvision import transforms
from PIL import Image

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
IMAGENET_MEAN: List[float] = [0.485, 0.456, 0.406]
IMAGENET_STD: List[float] = [0.229, 0.224, 0.225]

# Augmentation parameters
ROTATION_DEGREES: float = 15.0
COLOR_JITTER_BRIGHTNESS: float = 0.3
COLOR_JITTER_CONTRAST: float = 0.3
COLOR_JITTER_SATURATION: float = 0.3
COLOR_JITTER_HUE: float = 0.05
RANDOM_ERASE_PROB: float = 0.3   # Simulates watermarks / ad overlays

# Class name → integer label mapping
CLASS_TO_IDX: Dict[str, int] = {
    "not_spam": 0,
    "spam": 1,
}

# Supported image extensions (lowercase)
SUPPORTED_EXTENSIONS: Tuple[str, ...] = (".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")


# ---------------------------------------------------------------------------
# Dataset
# ---------------------------------------------------------------------------

class SpamDataset(Dataset):
    """
    PyTorch Dataset that loads spam and not_spam images from a directory.

    Args:
        data_dir  : Root folder containing 'spam/' and 'not_spam/' subfolders.
        transform : Optional torchvision transform pipeline applied to each image.
        indices   : Optional list of integer indices to use (for train/val splits).
    """

    def __init__(
        self,
        data_dir: str,
        transform: Optional[Callable] = None,
        indices: Optional[List[int]] = None,
    ) -> None:
        self.data_dir = Path(data_dir)
        self.transform = transform

        self.samples: List[Tuple[Path, int]] = []  # (image_path, label)
        self._load_samples()

        # Apply subset indices if provided (used for train/val splitting)
        if indices is not None:
            self.samples = [self.samples[i] for i in indices]

    def _load_samples(self) -> None:
        """Walk each class subfolder and collect (path, label) pairs."""
        for class_name, label in CLASS_TO_IDX.items():
            class_dir = self.data_dir / class_name
            if not class_dir.is_dir():
                raise FileNotFoundError(
                    f"Expected class folder not found: {class_dir}\n"
                    f"Make sure '{class_name}/' exists inside '{self.data_dir}'."
                )
            for file_path in sorted(class_dir.iterdir()):
                if file_path.suffix.lower() in SUPPORTED_EXTENSIONS:
                    self.samples.append((file_path, label))

        if len(self.samples) == 0:
            raise RuntimeError(
                f"No images found in {self.data_dir}. "
                "Check that 'spam/' and 'not_spam/' folders contain images."
            )

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor]:
        img_path, label = self.samples[idx]

        # Load image and convert to RGB (handles grayscale, RGBA, palette modes)
        try:
            image = Image.open(img_path).convert("RGB")
        except Exception as exc:
            raise RuntimeError(f"Failed to open image: {img_path}") from exc

        if self.transform is not None:
            image = self.transform(image)

        # Label as float32 tensor — required by BCEWithLogitsLoss
        return image, torch.tensor(label, dtype=torch.float32)

    def get_labels(self) -> List[int]:
        """Return all integer labels in dataset order (used for stratified split)."""
        return [label for _, label in self.samples]


# ---------------------------------------------------------------------------
# Transforms
# ---------------------------------------------------------------------------

def get_train_transforms(img_size: int = 380) -> transforms.Compose:
    """
    Augmentation pipeline for training images.

    Augmentations applied:
      - Resize to (img_size, img_size)
      - Random horizontal flip (p=0.5)
      - Random rotation ±ROTATION_DEGREES
      - Color jitter (brightness, contrast, saturation, hue)
      - Convert to tensor and normalize with ImageNet stats
      - Random erasing (simulates watermarks / ad overlays)

    Note: RandomErasing is applied AFTER ToTensor because it operates on tensors.

    Args:
        img_size: Target square image size (default 380 for EfficientNet-B4).

    Returns:
        A torchvision Compose transform pipeline.
    """
    return transforms.Compose([
        transforms.Resize((img_size, img_size)),
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomRotation(degrees=ROTATION_DEGREES),
        transforms.ColorJitter(
            brightness=COLOR_JITTER_BRIGHTNESS,
            contrast=COLOR_JITTER_CONTRAST,
            saturation=COLOR_JITTER_SATURATION,
            hue=COLOR_JITTER_HUE,
        ),
        transforms.ToTensor(),
        transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        # RandomErasing works on tensors; scale simulates small-to-medium occlusions
        transforms.RandomErasing(
            p=RANDOM_ERASE_PROB,
            scale=(0.02, 0.2),
            ratio=(0.3, 3.3),
            value=0,
        ),
    ])


def get_val_transforms(img_size: int = 380) -> transforms.Compose:
    """
    Minimal transform pipeline for validation / inference.

    Only resizes and normalizes — no augmentation to get clean metrics.

    Args:
        img_size: Target square image size (default 380 for EfficientNet-B4).

    Returns:
        A torchvision Compose transform pipeline.
    """
    return transforms.Compose([
        transforms.Resize((img_size, img_size)),
        transforms.ToTensor(),
        transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
    ])


# ---------------------------------------------------------------------------
# Class weight helper (for WeightedRandomSampler)
# ---------------------------------------------------------------------------

def get_sample_weights(labels: List[int]) -> List[float]:
    """
    Compute per-sample weights for WeightedRandomSampler.

    Samples from the minority class (spam) receive a higher weight so that
    each training batch sees a roughly balanced class distribution.

    Args:
        labels: List of integer labels (0 or 1) for every sample in the dataset.

    Returns:
        A list of float weights, one per sample.
    """
    class_counts: Dict[int, int] = {}
    for lbl in labels:
        class_counts[lbl] = class_counts.get(lbl, 0) + 1

    # Weight for each class is inversely proportional to its frequency
    class_weights: Dict[int, float] = {
        cls: 1.0 / count for cls, count in class_counts.items()
    }

    sample_weights = [class_weights[lbl] for lbl in labels]
    return sample_weights

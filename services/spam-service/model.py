"""
model.py
--------
Defines the EfficientNet-B4 binary classifier for spam vs. manga detection.

Architecture:
  - EfficientNet-B4 backbone pretrained on ImageNet (torchvision)
  - Custom binary classifier head: Linear(in_features, 1)
  - Output: raw logit (use BCEWithLogitsLoss during training, sigmoid for inference)

Two-phase fine-tuning strategy:
  - Phase 1: freeze_base()  → only the new head is trained
  - Phase 2: unfreeze_last_blocks(n=2) → last N feature blocks + head are trained
"""

import torch
import torch.nn as nn
from torchvision import models
from torchvision.models import EfficientNet_B4_Weights

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
NUM_CLASSES: int = 1          # Binary output (logit); sigmoid applied at inference
DROPOUT_RATE: float = 0.4     # EfficientNet-B4 default dropout before classifier


def _require_gpu() -> None:
    """Raise a RuntimeError immediately if no CUDA-capable GPU is available."""
    if not torch.cuda.is_available():
        raise RuntimeError(
            "No CUDA GPU detected. This project requires an NVIDIA GPU with CUDA.\n"
            "Ensure your CUDA drivers and PyTorch CUDA build are correctly installed.\n"
            "Run `python -c \"import torch; print(torch.cuda.is_available())\"` to debug."
        )


def build_model() -> nn.Module:
    """
    Build and return an EfficientNet-B4 model with a binary classifier head.

    The base network is loaded with ImageNet pretrained weights.
    The original classifier head is replaced with:
        Dropout(0.4) -> Linear(1792, 1)

    Returns:
        model (nn.Module): Ready-to-train model (on CPU; caller moves to GPU).
    """
    _require_gpu()

    # Load EfficientNet-B4 with the latest recommended ImageNet pretrained weights
    model = models.efficientnet_b4(weights=EfficientNet_B4_Weights.IMAGENET1K_V1)

    # Replace the classifier head for binary classification
    # EfficientNet-B4's classifier is: Sequential(Dropout(p=0.4), Linear(1792, 1000))
    in_features: int = model.classifier[1].in_features  # 1792

    model.classifier = nn.Sequential(
        nn.Dropout(p=DROPOUT_RATE, inplace=True),
        nn.Linear(in_features, NUM_CLASSES),
    )

    return model


def freeze_base(model: nn.Module) -> None:
    """
    Freeze all parameters EXCEPT the classifier head.

    Call this before Phase 1 training so only the new head learns.

    Args:
        model: The EfficientNet-B4 model returned by build_model().
    """
    # Freeze everything first
    for param in model.parameters():
        param.requires_grad = False

    # Unfreeze only the classifier head
    for param in model.classifier.parameters():
        param.requires_grad = True


def unfreeze_last_blocks(model: nn.Module, n_blocks: int = 2) -> None:
    """
    Unfreeze the last `n_blocks` EfficientNet feature blocks and the classifier head.

    EfficientNet-B4 has 9 feature blocks (model.features[0] through model.features[8]).
    Unfreezing the last 2 means model.features[7] and model.features[8].

    Call this at the start of Phase 2 training.

    Args:
        model:    The EfficientNet-B4 model.
        n_blocks: Number of trailing feature blocks to unfreeze (default: 2).
    """
    # Unfreeze the classifier head
    for param in model.classifier.parameters():
        param.requires_grad = True

    # model.features is a Sequential of blocks; unfreeze last n_blocks
    feature_blocks = list(model.features.children())
    for block in feature_blocks[-n_blocks:]:
        for param in block.parameters():
            param.requires_grad = True


def count_trainable_params(model: nn.Module) -> int:
    """Return the number of parameters that will be updated during training."""
    return sum(p.numel() for p in model.parameters() if p.requires_grad)

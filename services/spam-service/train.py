"""
train.py
--------
CLI training script for the spam vs. manga binary image classifier.

Usage:
    python train.py --data_dir ./dataset --epochs 30 --batch_size 32

Two-phase fine-tuning strategy:
  Phase 1 (epochs 1–10)   : Only the classifier head is trained (base frozen).
  Phase 2 (epochs 11–end) : Last 2 EfficientNet blocks + head are unfrozen,
                             trained at a lower LR (1e-5).

Requirements:
  - CUDA GPU is MANDATORY. The script will raise an error and stop if none is found.
  - See requirements.txt for package dependencies.
"""

import argparse
import os
import sys
import time
from pathlib import Path
from typing import List, Tuple, Dict

import torch
import torch.nn as nn
from torch.cuda.amp import GradScaler, autocast
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingLR
from torch.utils.data import DataLoader, WeightedRandomSampler
from sklearn.model_selection import train_test_split

from model import build_model, freeze_base, unfreeze_last_blocks, count_trainable_params
from dataset import SpamDataset, get_train_transforms, get_val_transforms, get_sample_weights
from utils import (
    compute_metrics,
    get_pos_weight,
    save_checkpoint,
    log_epoch,
    print_gpu_memory,
    format_metrics_str,
)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
PHASE1_EPOCHS: int = 10          # Epochs to train with frozen base
PHASE2_LR: float = 1e-5          # Lower LR for fine-tuning in Phase 2
RANDOM_SEED: int = 42            # For reproducible train/val splits
VAL_SPLIT: float = 0.2           # 20% of data used for validation
NUM_WORKERS: int = 4             # DataLoader worker processes


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train EfficientNet-B4 spam vs. manga binary classifier"
    )
    parser.add_argument(
        "--data_dir",
        type=str,
        default="./dataset",
        help="Root folder with 'spam/' and 'not_spam/' subfolders (default: ./dataset)",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=30,
        help="Total number of training epochs (default: 30)",
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=32,
        help="Training batch size (default: 32)",
    )
    parser.add_argument(
        "--lr",
        type=float,
        default=1e-4,
        help="Learning rate for Phase 1 (default: 1e-4)",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="Directory to save model checkpoint and training log (default: ./output)",
    )
    parser.add_argument(
        "--img_size",
        type=int,
        default=380,
        help="Input image size in pixels (default: 380 for EfficientNet-B4)",
    )
    return parser.parse_args()


# ---------------------------------------------------------------------------
# Data loading helpers
# ---------------------------------------------------------------------------

def build_dataloaders(
    data_dir: str,
    img_size: int,
    batch_size: int,
    seed: int,
) -> Tuple[DataLoader, DataLoader, List[int]]:
    """
    Build train and validation DataLoaders with stratified split.

    Returns:
        train_loader, val_loader, train_labels (for pos_weight calculation)
    """
    # Load the full dataset once (without transform) to collect labels for splitting
    full_dataset = SpamDataset(data_dir=data_dir)
    all_labels = full_dataset.get_labels()
    all_indices = list(range(len(full_dataset)))

    print(f"\n[Dataset] Total images: {len(all_labels)}")
    spam_count = sum(1 for l in all_labels if l == 1)
    not_spam_count = sum(1 for l in all_labels if l == 0)
    print(f"[Dataset] spam: {spam_count} | not_spam: {not_spam_count}")
    print(f"[Dataset] Imbalance ratio: {not_spam_count / max(spam_count, 1):.1f}x (not_spam/spam)")

    # Stratified split — preserves class ratio in both subsets
    train_indices, val_indices = train_test_split(
        all_indices,
        test_size=VAL_SPLIT,
        random_state=seed,
        stratify=all_labels,
    )

    # Build datasets with appropriate transforms
    train_dataset = SpamDataset(
        data_dir=data_dir,
        transform=get_train_transforms(img_size),
        indices=train_indices,
    )
    val_dataset = SpamDataset(
        data_dir=data_dir,
        transform=get_val_transforms(img_size),
        indices=val_indices,
    )

    print(f"[Dataset] Train: {len(train_dataset)} | Val: {len(val_dataset)}")

    # --- WeightedRandomSampler: oversample spam in training batches ---
    train_labels = train_dataset.get_labels()
    sample_weights = get_sample_weights(train_labels)
    sampler = WeightedRandomSampler(
        weights=sample_weights,
        num_samples=len(sample_weights),
        replacement=True,  # Allow oversampling of minority class
    )

    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        sampler=sampler,           # WeightedRandomSampler replaces shuffle=True
        num_workers=NUM_WORKERS,
        pin_memory=True,           # Faster CPU→GPU data transfer
        drop_last=True,            # Avoid partial batches (stabilizes BatchNorm)
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=NUM_WORKERS,
        pin_memory=True,
    )

    return train_loader, val_loader, train_labels


# ---------------------------------------------------------------------------
# Training / validation loops
# ---------------------------------------------------------------------------

def run_epoch(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
    optimizer: torch.optim.Optimizer = None,
    scaler: GradScaler = None,
    is_train: bool = True,
) -> Tuple[float, List[int], List[float]]:
    """
    Run one full epoch (train or validation).

    Args:
        model     : The model.
        loader    : DataLoader for this phase.
        criterion : Loss function (BCEWithLogitsLoss).
        device    : CUDA device.
        optimizer : Required when is_train=True.
        scaler    : AMP GradScaler; required when is_train=True.
        is_train  : If True, perform backpropagation.

    Returns:
        avg_loss (float), all_labels (List[int]), all_logits (List[float])
    """
    model.train() if is_train else model.eval()

    total_loss = 0.0
    all_labels: List[int] = []
    all_logits: List[float] = []

    context = torch.enable_grad() if is_train else torch.no_grad()

    with context:
        for images, labels in loader:
            # Move data to GPU
            images = images.to(device, non_blocking=True)
            labels = labels.to(device, non_blocking=True)

            if is_train:
                optimizer.zero_grad(set_to_none=True)  # Faster than zero_grad()

            # --- Forward pass with AMP (automatic mixed precision) ---
            # autocast uses float16 for eligible ops → faster on RTX cards
            with autocast():
                logits = model(images).squeeze(1)  # shape: (batch,)
                loss = criterion(logits, labels)

            if is_train:
                # --- Backward pass with AMP scaler ---
                # Scaler prevents float16 underflow gradients
                scaler.scale(loss).backward()
                scaler.step(optimizer)
                scaler.update()

            total_loss += loss.item() * images.size(0)

            # Collect predictions for metric computation (move to CPU)
            all_labels.extend(labels.cpu().int().tolist())
            all_logits.extend(logits.detach().cpu().float().tolist())

    avg_loss = total_loss / len(loader.dataset)
    return avg_loss, all_labels, all_logits


# ---------------------------------------------------------------------------
# Main training entry point
# ---------------------------------------------------------------------------

def train(args: argparse.Namespace) -> None:
    # ---- GPU enforcement ----
    if not torch.cuda.is_available():
        print(
            "\n[ERROR] No CUDA GPU detected!\n"
            "This training script requires an NVIDIA GPU with CUDA support.\n"
            "Install the correct PyTorch CUDA build: https://pytorch.org/get-started/locally/\n"
            "Verify your setup: python -c \"import torch; print(torch.cuda.is_available())\""
        )
        sys.exit(1)

    device = torch.device("cuda")
    gpu_name = torch.cuda.get_device_name(0)
    print(f"\n[GPU] Using: {gpu_name}")
    print(f"[GPU] CUDA version: {torch.version.cuda}")

    os.makedirs(args.output_dir, exist_ok=True)
    csv_log_path = os.path.join(args.output_dir, "training_log.csv")

    # ---- Data ----
    train_loader, val_loader, train_labels = build_dataloaders(
        data_dir=args.data_dir,
        img_size=args.img_size,
        batch_size=args.batch_size,
        seed=RANDOM_SEED,
    )

    # ---- Model ----
    model = build_model()
    model = model.to(device)

    # ---- Phase 1 setup: freeze base, train only the head ----
    freeze_base(model)
    print(f"\n[Phase 1] Frozen base — trainable params: {count_trainable_params(model):,}")

    optimizer = AdamW(
        filter(lambda p: p.requires_grad, model.parameters()),
        lr=args.lr,
        weight_decay=1e-4,
    )
    # Cosine annealing decays LR to near-zero over Phase 1
    scheduler = CosineAnnealingLR(optimizer, T_max=PHASE1_EPOCHS, eta_min=1e-6)

    # ---- Loss function ----
    # pos_weight penalizes false negatives (missing spam) more heavily
    pos_weight = get_pos_weight(train_labels, device)
    print(f"[Loss] BCEWithLogitsLoss pos_weight = {pos_weight.item():.4f}")
    criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)

    # ---- AMP scaler (for mixed precision training) ----
    scaler = GradScaler()

    best_val_f1: float = 0.0
    phase2_started: bool = False

    print(f"\n[Training] Starting {args.epochs} epochs | Batch size: {args.batch_size} | LR: {args.lr}")
    print("=" * 75)

    for epoch in range(1, args.epochs + 1):
        epoch_start = time.time()

        # ---- Phase transition: unfreeze last 2 blocks at epoch PHASE1_EPOCHS+1 ----
        if epoch == PHASE1_EPOCHS + 1 and not phase2_started:
            print(f"\n[Phase 2] Unfreezing last 2 EfficientNet blocks — LR → {PHASE2_LR}")
            unfreeze_last_blocks(model, n_blocks=2)
            print(f"[Phase 2] Trainable params: {count_trainable_params(model):,}")

            # New optimizer and scheduler for Phase 2 (lower LR)
            optimizer = AdamW(
                filter(lambda p: p.requires_grad, model.parameters()),
                lr=PHASE2_LR,
                weight_decay=1e-4,
            )
            phase2_epochs = args.epochs - PHASE1_EPOCHS
            scheduler = CosineAnnealingLR(optimizer, T_max=phase2_epochs, eta_min=1e-7)
            phase2_started = True
            print("=" * 75)

        # ---- Train ----
        train_loss, train_labels_ep, train_logits = run_epoch(
            model, train_loader, criterion, device,
            optimizer=optimizer, scaler=scaler, is_train=True
        )
        train_metrics = compute_metrics(train_labels_ep, train_logits)

        # ---- Validate ----
        val_loss, val_labels, val_logits = run_epoch(
            model, val_loader, criterion, device, is_train=False
        )
        val_metrics = compute_metrics(val_labels, val_logits)

        # Advance LR scheduler after each epoch
        scheduler.step()

        elapsed = time.time() - epoch_start
        phase_label = "P1" if epoch <= PHASE1_EPOCHS else "P2"

        print(f"\nEpoch [{epoch:3d}/{args.epochs}] ({phase_label}) — {elapsed:.1f}s")
        print(format_metrics_str("Train", train_loss, train_metrics))
        print(format_metrics_str("Val  ", val_loss, val_metrics))
        print_gpu_memory(f"Epoch {epoch}")

        # ---- Save best checkpoint (by validation F1) ----
        val_f1 = val_metrics["f1"]
        if val_f1 > best_val_f1:
            best_val_f1 = val_f1
            save_checkpoint(model, args.output_dir, epoch, val_f1)

        # ---- Log to CSV ----
        log_epoch(csv_log_path, {
            "epoch":          epoch,
            "phase":          phase_label,
            "lr":             scheduler.get_last_lr()[0],
            "train_loss":     round(train_loss, 6),
            "train_acc":      train_metrics["accuracy"],
            "train_prec":     train_metrics["precision"],
            "train_recall":   train_metrics["recall"],
            "train_f1":       train_metrics["f1"],
            "val_loss":       round(val_loss, 6),
            "val_acc":        val_metrics["accuracy"],
            "val_prec":       val_metrics["precision"],
            "val_recall":     val_metrics["recall"],
            "val_f1":         val_metrics["f1"],
            "gpu_mem_mb":     round(torch.cuda.memory_allocated() / 1024 ** 2, 1),
        })

    print("\n" + "=" * 75)
    print(f"[Done] Training complete. Best Val F1: {best_val_f1:.4f}")
    print(f"[Done] Best model saved → {os.path.join(args.output_dir, 'best_model.pt')}")
    print(f"[Done] Training log    → {csv_log_path}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    args = parse_args()
    train(args)

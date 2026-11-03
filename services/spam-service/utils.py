"""
utils.py
--------
Helper functions for the spam vs. manga classifier:
  - Metric computation (accuracy, precision, recall, F1)
  - pos_weight calculation for BCEWithLogitsLoss
  - Model checkpoint saving/loading
  - CSV training log helper
  - GPU memory reporting
"""

import csv
import math
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

import torch
import torch.nn as nn

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
DEFAULT_THRESHOLD: float = 0.5   # Sigmoid threshold for binary decision


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------

def compute_metrics(
    y_true: List[int],
    y_logits: List[float],
    threshold: float = DEFAULT_THRESHOLD,
) -> Dict[str, float]:
    """
    Compute binary classification metrics from raw logits.

    Args:
        y_true    : Ground-truth integer labels (0 or 1).
        y_logits  : Raw model output logits (before sigmoid).
        threshold : Decision boundary after applying sigmoid (default 0.5).

    Returns:
        Dictionary with keys: accuracy, precision, recall, f1.
    """
    # Convert logits → probabilities → binary predictions
    probs = [_sigmoid(l) for l in y_logits]
    preds = [1 if p >= threshold else 0 for p in probs]

    tp = sum(1 for t, p in zip(y_true, preds) if t == 1 and p == 1)
    tn = sum(1 for t, p in zip(y_true, preds) if t == 0 and p == 0)
    fp = sum(1 for t, p in zip(y_true, preds) if t == 0 and p == 1)
    fn = sum(1 for t, p in zip(y_true, preds) if t == 1 and p == 0)

    total = len(y_true)
    accuracy = (tp + tn) / total if total > 0 else 0.0

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall    = tp / (tp + fn) if (tp + fn) > 0 else 0.0

    f1 = (
        2 * precision * recall / (precision + recall)
        if (precision + recall) > 0
        else 0.0
    )

    return {
        "accuracy":  round(accuracy, 4),
        "precision": round(precision, 4),
        "recall":    round(recall, 4),
        "f1":        round(f1, 4),
    }


def _sigmoid(x: float) -> float:
    """Numerically stable sigmoid for a single float value."""
    if x >= 0:
        return 1.0 / (1.0 + math.exp(-x))
    else:
        exp_x = math.exp(x)
        return exp_x / (1.0 + exp_x)


# ---------------------------------------------------------------------------
# pos_weight for BCEWithLogitsLoss
# ---------------------------------------------------------------------------

def get_pos_weight(labels: List[int], device: torch.device) -> torch.Tensor:
    """
    Compute the pos_weight scalar for BCEWithLogitsLoss.

    pos_weight = num_negatives / num_positives

    This penalizes the model more for false negatives (missing spam), which is
    critical because spam is the minority class.

    Args:
        labels : List of integer labels (0 = not_spam, 1 = spam).
        device : Target device for the returned tensor.

    Returns:
        A 1-element float tensor suitable for BCEWithLogitsLoss(pos_weight=...).
    """
    num_pos = sum(1 for l in labels if l == 1)   # spam count
    num_neg = sum(1 for l in labels if l == 0)   # not_spam count

    if num_pos == 0:
        raise ValueError("No positive (spam) samples found in the dataset.")
    if num_neg == 0:
        raise ValueError("No negative (not_spam) samples found in the dataset.")

    pos_weight_value = num_neg / num_pos
    return torch.tensor([pos_weight_value], dtype=torch.float32, device=device)


# ---------------------------------------------------------------------------
# Checkpoint helpers
# ---------------------------------------------------------------------------

def save_checkpoint(
    model: nn.Module,
    output_dir: str,
    epoch: int,
    val_f1: float,
    filename: str = "best_model.pt",
) -> None:
    """
    Save the model state dict along with training metadata.

    The saved file contains:
      - "model_state_dict"  : model weights
      - "epoch"             : epoch number when saved
      - "val_f1"            : validation F1 score at save time

    Args:
        model      : Trained PyTorch model.
        output_dir : Directory where the checkpoint file is written.
        epoch      : Current epoch number (1-indexed).
        val_f1     : Validation F1 score that triggered this save.
        filename   : Output filename (default: best_model.pt).
    """
    os.makedirs(output_dir, exist_ok=True)
    save_path = Path(output_dir) / filename
    torch.save(
        {
            "model_state_dict": model.state_dict(),
            "epoch": epoch,
            "val_f1": val_f1,
        },
        save_path,
    )
    print(f"  [Checkpoint] Saved best model → {save_path}  (epoch {epoch}, val F1={val_f1:.4f})")


def load_checkpoint(model: nn.Module, checkpoint_path: str, device: torch.device) -> Dict[str, Any]:
    """
    Load model weights from a checkpoint file.

    Args:
        model           : The model architecture (must match the saved one).
        checkpoint_path : Path to the .pt checkpoint file.
        device          : Device to load the weights onto.

    Returns:
        The metadata dict (keys: epoch, val_f1).
    """
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["model_state_dict"])
    return {k: v for k, v in checkpoint.items() if k != "model_state_dict"}


# ---------------------------------------------------------------------------
# CSV training logger
# ---------------------------------------------------------------------------

def log_epoch(csv_path: str, row: Dict[str, Any]) -> None:
    """
    Append one row of training metrics to a CSV log file.

    If the file does not exist, it is created with a header row.

    Args:
        csv_path : Absolute or relative path to the CSV file.
        row      : Dictionary where keys become column names.
    """
    csv_path = Path(csv_path)
    os.makedirs(csv_path.parent, exist_ok=True)

    file_exists = csv_path.is_file()
    with open(csv_path, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(row.keys()))
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)


# ---------------------------------------------------------------------------
# GPU memory reporting
# ---------------------------------------------------------------------------

def print_gpu_memory(label: str = "") -> None:
    """
    Print current GPU memory usage in MB.

    Args:
        label: Optional prefix string shown before the memory stats.
    """
    if not torch.cuda.is_available():
        return
    allocated_mb = torch.cuda.memory_allocated() / 1024 ** 2
    reserved_mb  = torch.cuda.memory_reserved()  / 1024 ** 2
    prefix = f"[{label}] " if label else ""
    print(f"  {prefix}GPU memory — allocated: {allocated_mb:.1f} MB | reserved: {reserved_mb:.1f} MB")


# ---------------------------------------------------------------------------
# Misc
# ---------------------------------------------------------------------------

def format_metrics_str(phase: str, loss: float, metrics: Dict[str, float]) -> str:
    """
    Format a one-line summary string for printing during training.

    Args:
        phase   : "Train" or "Val"
        loss    : Average loss for the epoch.
        metrics : Dict from compute_metrics().

    Returns:
        Formatted string, e.g.:
        "  Train | Loss: 0.3421 | Acc: 0.8900 | P: 0.8500 | R: 0.9100 | F1: 0.8789"
    """
    return (
        f"  {phase:5s} | Loss: {loss:.4f} | "
        f"Acc: {metrics['accuracy']:.4f} | "
        f"P: {metrics['precision']:.4f} | "
        f"R: {metrics['recall']:.4f} | "
        f"F1: {metrics['f1']:.4f}"
    )

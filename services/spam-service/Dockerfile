# Base image with official PyTorch 2.6.0, CUDA 12.4, cuDNN 9 and TorchVision pre-installed
FROM pytorch/pytorch:2.6.0-cuda12.4-cudnn9-runtime

# NVIDIA container runtime environment configuration
ENV NVIDIA_VISIBLE_DEVICES=all \
    NVIDIA_DRIVER_CAPABILITIES=compute,utility \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# Install additional Python dependencies (scikit-learn, tqdm, pandas, Pillow)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-create standard container mount points for volumes
#   /dataset/spam     -> host spam images directory
#   /dataset/not_spam -> host clean/manga images directory
#   /output           -> host directory to receive best_model.pt and training_log.csv
#   /scan             -> host directory to scan for spam images
#   /models           -> host directory containing trained model checkpoint
RUN mkdir -p /dataset/spam /dataset/not_spam /output /scan /models

# Copy application source code and entrypoint
COPY dataset.py model.py utils.py train.py scan_folder.py entrypoint.sh ./
RUN chmod +x /app/entrypoint.sh

# Use entrypoint dispatcher to expose 'train' and 'scan' utilities
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["--help"]


#!/bin/bash
set -e

# Help display
show_help() {
    echo "=================================================================="
    echo "  Spam vs. Manga Binary Classifier (Docker Service)"
    echo "=================================================================="
    echo "Usage:"
    echo "  docker run --gpus all ... spam-service train [train-options]"
    echo "  docker run --gpus all ... spam-service scan [scan-options]"
    echo ""
    echo "Commands:"
    echo "  train       Train EfficientNet-B4 classifier (train.py)"
    echo "              Default dataset : /dataset (expects /dataset/spam & /dataset/not_spam)"
    echo "              Default output  : /output"
    echo ""
    echo "  scan        Scan folder for spam images (scan_folder.py)"
    echo "              Default folder  : /scan"
    echo "              Default model   : /models/best_model.pt (or /output/best_model.pt)"
    echo ""
    echo "  bash/sh     Open interactive shell inside the container"
    echo "=================================================================="
}

if [ "$1" = "train" ]; then
    shift
    DATA_ARG=()
    OUTPUT_ARG=()
    
    HAS_DATA=0
    HAS_OUTPUT=0
    for arg in "$@"; do
        case "$arg" in
            --data_dir*) HAS_DATA=1 ;;
            --output_dir*) HAS_OUTPUT=1 ;;
        esac
    done

    if [ $HAS_DATA -eq 0 ]; then
        DATA_ARG=(--data_dir /dataset)
    fi
    if [ $HAS_OUTPUT -eq 0 ]; then
        OUTPUT_ARG=(--output_dir /output)
    fi

    exec python train.py "${DATA_ARG[@]}" "${OUTPUT_ARG[@]}" "$@"

elif [ "$1" = "scan" ]; then
    shift
    FOLDER_ARG=()
    MODEL_ARG=()

    HAS_FOLDER=0
    HAS_MODEL=0
    for arg in "$@"; do
        case "$arg" in
            --folder*) HAS_FOLDER=1 ;;
            --model*) HAS_MODEL=1 ;;
        esac
    done

    if [ $HAS_FOLDER -eq 0 ]; then
        FOLDER_ARG=(--folder /scan)
    fi
    if [ $HAS_MODEL -eq 0 ]; then
        if [ -f "/models/best_model.pt" ]; then
            MODEL_ARG=(--model /models/best_model.pt)
        elif [ -f "/output/best_model.pt" ]; then
            MODEL_ARG=(--model /output/best_model.pt)
        fi
    fi

    exec python scan_folder.py "${FOLDER_ARG[@]}" "${MODEL_ARG[@]}" "$@"

elif [ "$1" = "--help" ] || [ "$1" = "-h" ] || [ -z "$1" ]; then
    show_help
    exit 0
else
    exec "$@"
fi


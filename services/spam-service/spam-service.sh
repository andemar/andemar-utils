#!/usr/bin/env bash
set -e

IMAGE_TAG="${IMAGE_TAG:-spam-service}"

show_help() {
    echo "=================================================================="
    echo "  Spam vs. Manga Binary Classifier CLI (Docker Wrapper)"
    echo "=================================================================="
    echo "Usage:"
    echo "  ./spam-service.sh build"
    echo "  ./spam-service.sh train --spam-dir <path> --not-spam-dir <path> [--output-dir <path>] [train-args]"
    echo "  ./spam-service.sh train --data-dir <path> [--output-dir <path>] [train-args]"
    echo "  ./spam-service.sh scan --folder <path> [--model <path>] [--move-spam] [--rename-spam] [--threshold <0.83>]"
    echo "=================================================================="
}

ACTION="$1"
shift || true

case "$ACTION" in
    build)
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        docker build -t "$IMAGE_TAG" "$SCRIPT_DIR"
        ;;

    train)
        SPAM_DIR=""
        NOT_SPAM_DIR=""
        DATA_DIR=""
        OUTPUT_DIR="./output"
        EXTRA_ARGS=()

        while [[ $# -gt 0 ]]; do
            case "$1" in
                --spam-dir) SPAM_DIR="$2"; shift 2 ;;
                --not-spam-dir) NOT_SPAM_DIR="$2"; shift 2 ;;
                --data-dir) DATA_DIR="$2"; shift 2 ;;
                --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
                *) EXTRA_ARGS+=("$1"); shift ;;
            esac
        done

        mkdir -p "$OUTPUT_DIR"
        ABS_OUTPUT="$(cd "$OUTPUT_DIR" && pwd)"
        MOUNTS=("-v" "${ABS_OUTPUT}:/output")

        if [[ -n "$SPAM_DIR" && -n "$NOT_SPAM_DIR" ]]; then
            ABS_SPAM="$(cd "$SPAM_DIR" && pwd)"
            ABS_NOT_SPAM="$(cd "$NOT_SPAM_DIR" && pwd)"
            MOUNTS+=("-v" "${ABS_SPAM}:/dataset/spam:ro" "-v" "${ABS_NOT_SPAM}:/dataset/not_spam:ro")
        elif [[ -n "$DATA_DIR" ]]; then
            ABS_DATA="$(cd "$DATA_DIR" && pwd)"
            MOUNTS+=("-v" "${ABS_DATA}:/dataset:ro")
        else
            echo "Error: Must specify either (--spam-dir and --not-spam-dir) OR --data-dir"
            exit 1
        fi

        TTY_ARG="-it"
        [ ! -t 0 ] && TTY_ARG="-i"
        echo "Running training container with NVIDIA GPU..."
        docker run --rm $TTY_ARG --gpus all --shm-size=4g "${MOUNTS[@]}" "$IMAGE_TAG" train "${EXTRA_ARGS[@]}"
        ;;

    scan)
        FOLDER=""
        MODEL="./output/best_model.pt"
        EXTRA_ARGS=()

        while [[ $# -gt 0 ]]; do
            case "$1" in
                --folder) FOLDER="$2"; shift 2 ;;
                --model) MODEL="$2"; shift 2 ;;
                --move-spam) EXTRA_ARGS+=("--move_spam"); shift ;;
                --rename-spam) EXTRA_ARGS+=("--rename_spam"); shift ;;
                *) EXTRA_ARGS+=("$1"); shift ;;
            esac
        done

        if [[ -z "$FOLDER" ]]; then
            echo "Error: --folder is required for scan"
            exit 1
        fi

        ABS_FOLDER="$(cd "$FOLDER" && pwd)"
        MOUNTS=("-v" "${ABS_FOLDER}:/scan")

        if [[ -f "$MODEL" ]]; then
            ABS_MODEL_DIR="$(cd "$(dirname "$MODEL")" && pwd)"
            MODEL_FILE="$(basename "$MODEL")"
            MOUNTS+=("-v" "${ABS_MODEL_DIR}:/models:ro")
            EXTRA_ARGS+=("--model" "/models/${MODEL_FILE}")
        elif [[ -d "$MODEL" ]]; then
            ABS_MODEL_DIR="$(cd "$MODEL" && pwd)"
            MOUNTS+=("-v" "${ABS_MODEL_DIR}:/models:ro")
            EXTRA_ARGS+=("--model" "/models/best_model.pt")
        fi

        TTY_ARG="-it"
        [ ! -t 0 ] && TTY_ARG="-i"
        echo "Running scan container with NVIDIA GPU..."
        docker run --rm $TTY_ARG --gpus all --shm-size=4g "${MOUNTS[@]}" "$IMAGE_TAG" scan "${EXTRA_ARGS[@]}"
        ;;

    *)
        show_help
        ;;
esac

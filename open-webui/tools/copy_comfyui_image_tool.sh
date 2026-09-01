#!/bin/bash
set -e

# ------------------------------------------------------------
# Deploy ComfyUI Image Tool to Open WebUI
# ------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env not found: $ENV_FILE"
    exit 1
fi

source "$ENV_FILE"

if [ -z "$OPENWEBUI_CUSTOMTOOLS" ]; then
    echo "Error: OPENWEBUI_CUSTOMTOOLS is not set in $ENV_FILE"
    exit 1
fi

SOURCE_DIR="$SCRIPT_DIR/comfyui_image"
DEST_DIR="$OPENWEBUI_CUSTOMTOOLS/comfyui_image"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: source directory not found: $SOURCE_DIR"
    exit 1
fi

mkdir -p "$DEST_DIR"

rsync -av \
    --exclude='__pycache__/' \
    --exclude='*.pyc' \
    "$SOURCE_DIR/" "$DEST_DIR/"

echo
echo "✓ ComfyUI Image Tool deployed"
echo "  Source:      $SOURCE_DIR"
echo "  Destination: $DEST_DIR"
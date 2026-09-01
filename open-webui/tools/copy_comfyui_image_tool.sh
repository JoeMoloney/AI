#!/bin/bash
# Tool copy script for Open-WebUI custom tools

# Read destination from .env file relative to current directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    exit 1
fi

# Read destination from .env file
DEST_DIR=$(grep "^OPENWEBUI_CUSTOMTOOLS=" "$ENV_FILE" | cut -d'=' -f2-)

# Replace ~ with home directory path if needed  
if [[ "$DEST_DIR" == "~/"* ]]; then
    DEST_DIR="${HOME}/${DEST_DIR:2}"
fi

# Copy the entire comfyui_image folder (excluding __pycache__ and README.md)
cp -r "$SCRIPT_DIR/comfyui_image/"* "$DEST_DIR/comfyui_image/"

echo "Tool files copied successfully to $DEST_DIR/comfyui_image/"
#!/bin/bash

# Setup script for tsim simulator
# This script adds tsim to your PATH for the current session

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Add tsim to PATH
export PATH="$SCRIPT_DIR/tsim-0.84/world/usr/local/bin:$PATH"

# Set the resource directory for tsim
export TSIM_RESOURCE_DIR="$SCRIPT_DIR/tsim-0.84/world/usr/local/share/tsim"

# Set TSIM_PATH for Java applications
export TSIM_PATH="$SCRIPT_DIR/tsim-0.84/world/usr/local/bin/tsim"

# Create symlink to system location if it doesn't exist or if it's pointing to wrong location
if [ ! -d "/usr/local/share/tsim" ] || [ ! -L "/usr/local/share/tsim/bitmaps" ] || [ "$(readlink /usr/local/share/tsim/bitmaps)" != "$TSIM_RESOURCE_DIR/bitmaps" ]; then
    echo "Creating/updating symlink for tsim resources..."
    sudo rm -rf /usr/local/share/tsim
    sudo mkdir -p /usr/local/share/tsim
    sudo ln -sf "$TSIM_RESOURCE_DIR"/* /usr/local/share/tsim/
    echo "Symlink created/updated successfully!"
fi

echo "Tsim setup complete!"
echo "Tsim binary location: $SCRIPT_DIR/tsim-0.84/world/usr/local/bin/tsim"
echo "Resource directory: $TSIM_RESOURCE_DIR"
echo ""
echo "You can now run tsim with:"
echo "  tsim [mapfile]"
echo ""
echo "Available maps:"
ls "$TSIM_RESOURCE_DIR/maps/" 2>/dev/null | sed 's/^/  /'
echo ""
echo "To make this permanent, add this line to your ~/.zshrc:"
echo "export PATH=\"$SCRIPT_DIR/tsim-0.84/world/usr/local/bin:\$PATH\""
echo "export TSIM_RESOURCE_DIR=\"$TSIM_RESOURCE_DIR\""


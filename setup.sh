#!/usr/bin/env sh
# setup.sh – auto-installs CMake on Mac/Linux then builds the C++ backend
set -e

echo "=== COMP208 Setup ==="

# ── Install CMake if missing ───────────────────────────────────
if ! command -v cmake > /dev/null 2>&1; then
    echo "CMake not found. Installing..."

    if [ "$(uname)" = "Darwin" ]; then
        if ! command -v brew > /dev/null 2>&1; then
            echo "Installing Homebrew..."
            /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        fi
        brew install cmake

    elif [ -f /etc/debian_version ]; then
        sudo apt-get update -qq
        sudo apt-get install -y cmake

    elif [ -f /etc/redhat-release ]; then
        sudo dnf install -y cmake || sudo yum install -y cmake

    elif [ -f /etc/arch-release ]; then
        sudo pacman -Sy --noconfirm cmake

    else
        echo "ERROR: Cannot auto-install CMake on this OS."
        echo "Please install CMake manually from https://cmake.org/download/"
        exit 1
    fi
else
    echo "CMake found: $(cmake --version | head -1)"
fi

# ── Build C++ backend ──────────────
echo ""
echo "=== Building C++ Backend ==="
cd Backend
mkdir -p build
cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
cd ../..
echo "C++ backend built successfully."

# ── Done ──────────────────────────────────────────
echo ""
echo "=== Setup complete. Run ./run_server.sh and ./run_client.sh ==="
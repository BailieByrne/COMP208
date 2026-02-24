#!/usr/bin/env bash
set -euo pipefail

# Paths (adjust if your project layout differs)
JAVAFX_PATH="Deps/javafx-sdk-17.0.18"
SRC_PATH="Backend/Server"
OUT_PATH="bin/classes"

mkdir -p "$OUT_PATH"

echo "Compiling Java files with JavaFX..."
if javac --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "$OUT_PATH" "$SRC_PATH/main.java"; then
  echo "Compilation successful!"
  echo "Copying FXML and CSS files..."
  cp "$SRC_PATH"/*.fxml "$OUT_PATH" 2>/dev/null || true
  cp "$SRC_PATH"/*.css "$OUT_PATH" 2>/dev/null || true
  echo
  echo "Running application..."
  java --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -Djava.util.logging.config.file=/dev/null -XX:+IgnoreUnrecognizedVMOptions -XX:ThreadStackSize=1024 -cp "$OUT_PATH" main
else
  echo "Compilation failed!"
  exit 1
fi

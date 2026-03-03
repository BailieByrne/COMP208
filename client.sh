#!/bin/bash

JAVAFX_PATH=Deps/javafx-sdk-17.0.18
SERVER_PATH=Server
CLIENT_PATH=client
OUT_PATH=bin/classes

mkdir -p "$OUT_PATH"

echo "Compiling Client..."
javac --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "$OUT_PATH" "$SERVER_PATH"/*.java "$CLIENT_PATH"/*.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Copying FXML and CSS files"
    cp "$CLIENT_PATH"/*.fxml "$OUT_PATH/" >/dev/null 2>&1
    cp "$CLIENT_PATH"/*.css "$OUT_PATH/" >/dev/null 2>&1
    echo "Starting client..."
    java --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "$OUT_PATH" client 2>/dev/null
else
    echo "Compilation failed!"
fi

read -p "Press any key to continue..."

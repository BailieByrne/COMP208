#!/bin/bash

JAVAFX_PATH=Deps/javafx-sdk-17.0.18
SERVER_PATH=Server
CLIENT_PATH=client
OUT_PATH=bin/classes

mkdir -p "$OUT_PATH"

echo "Compiling Client..."
JAVA_FILES=$(find "$SERVER_PATH" "$CLIENT_PATH" -name "*.java")
javac --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "$OUT_PATH" $JAVA_FILES 2>/dev/null

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Copying Assets folder"
    rm -rf "$OUT_PATH/Assets"
    cp -R "$CLIENT_PATH/Assets" "$OUT_PATH/"
    echo "Starting client..."
    java --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "$OUT_PATH" client 2>/dev/null
else
    echo "Compilation failed!"
fi

read -p "Press any key to continue..."

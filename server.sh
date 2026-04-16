#!/bin/bash

JAVAFX_PATH=Deps/javafx-sdk-17.0.18
SQLITE_LIB_DIR=Deps/sqlite
SERVER_PATH=Server
CLIENT_PATH=client
OUT_PATH=bin/classes

mkdir -p "$OUT_PATH"

echo "Compiling Server..."
JAVA_FILES=$(find "$SERVER_PATH" "$CLIENT_PATH" -name "*.java")
javac --module-path "$JAVAFX_PATH/lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "$OUT_PATH" $JAVA_FILES 2>/dev/null

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Starting server listening for connections..."
    java --enable-native-access=ALL-UNNAMED -cp "$OUT_PATH:$SQLITE_LIB_DIR/*" main 2>/dev/null
else
    echo "Compilation failed!"
fi

read -p "Press any key to continue..."

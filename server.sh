#!/usr/bin/env sh
# run_server.sh  –  works on Windows (Git Bash / WSL), macOS, Linux
set -e

echo "Compiling and starting server on port 5000..."

if [ -f "./gradlew" ]; then
    GRADLE="./gradlew"
elif [ -f "./gradlew.bat" ]; then
    GRADLE="./gradlew.bat"
else
    echo "ERROR: Gradle wrapper not found. Run 'gradle wrapper' first."
    exit 1
fi

$GRADLE clean compileJava -x test && \
$GRADLE runServer -x test --console=plain
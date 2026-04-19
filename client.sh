#!/usr/bin/env sh
# run_client.sh  –  works on Windows (Git Bash / WSL), macOS, Linux
set -e

echo "Compiling and starting client..."

# Pick the right wrapper executable
if [ -f "./gradlew" ]; then
    GRADLE="./gradlew"
elif [ -f "./gradlew.bat" ]; then
    GRADLE="./gradlew.bat"
else
    echo "ERROR: Gradle wrapper not found. Run 'gradle wrapper' first."
    exit 1
fi

$GRADLE clean compileJava -x test && \
$GRADLE runClient -x test --quiet --console=plain
@echo off
setlocal enabledelayedexpansion

echo Compiling and running Client with Gradle...
call .\gradlew.bat clean compileJava -x test

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Starting client...
    call .\gradlew.bat runClient -x test --quiet --console=plain
) else (
    echo Compilation failed!
)

pause

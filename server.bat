@echo off
setlocal enabledelayedexpansion

set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set SERVER_PATH=Server
set CLIENT_PATH=client
set OUT_PATH=bin\classes

if not exist "%OUT_PATH%" mkdir "%OUT_PATH%"

echo Compiling Server...
javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "%OUT_PATH%" "%SERVER_PATH%\*.java" "%CLIENT_PATH%\*.java" 2>nul

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Starting server listening for connections...
    java -cp "%OUT_PATH%" main 2>nul
) else (
    echo Compilation failed!
)

pause

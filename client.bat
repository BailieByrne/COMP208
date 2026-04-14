@echo off
setlocal enabledelayedexpansion

set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set SERVER_PATH=Server
set CLIENT_PATH=client
set OUT_PATH=bin\classes

if not exist "%OUT_PATH%" mkdir "%OUT_PATH%"

echo Compiling Client...
javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "%OUT_PATH%" "%SERVER_PATH%\*.java" "%CLIENT_PATH%\*.java" 2>nul

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Copying FXML and CSS files
    copy "%CLIENT_PATH%\*.fxml" "%OUT_PATH%" >nul 2>nul
    copy "%CLIENT_PATH%\*.css" "%OUT_PATH%" >nul 2>nul
    echo Starting client...
    java --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "%OUT_PATH%" client 2>nul
) else (
    echo Compilation failed!
)

pause

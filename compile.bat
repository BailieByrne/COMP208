@echo off
setlocal enabledelayedexpansion

set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set SRC_PATH=Backend\Server
set OUT_PATH=bin\classes

if not exist "%OUT_PATH%" mkdir "%OUT_PATH%"

echo Compiling Java files with JavaFX...
javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "%OUT_PATH%" "%SRC_PATH%\main.java"

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Copying FXML and CSS files...
    copy "%SRC_PATH%\*.fxml" "%OUT_PATH%"
    copy "%SRC_PATH%\*.css" "%OUT_PATH%"
    echo.
    echo Running application...
    java --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics --enable-native-access=javafx.graphics -Djava.util.logging.config.file=nul -XX:+IgnoreUnrecognizedVMOptions -XX:ThreadStackSize=1024 -cp "%OUT_PATH%" main 2>nul
) else (
    echo Compilation failed!
)

pause

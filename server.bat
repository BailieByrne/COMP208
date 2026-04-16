@echo off
setlocal enabledelayedexpansion


set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set SQLITE_LIB_DIR=Deps\sqlite
set SERVER_PATH=Server
set CLIENT_PATH=client
set OUT_PATH=bin\classes


if not exist "%OUT_PATH%" mkdir "%OUT_PATH%"


echo Compiling Server...
set SOURCES_FILE=%OUT_PATH%\sources.txt
if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"

for /r "%SERVER_PATH%" %%f in (*.java) do echo %%f>>"%SOURCES_FILE%"
for /r "%CLIENT_PATH%" %%f in (*.java) do echo %%f>>"%SOURCES_FILE%"

javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "%OUT_PATH%" @"%SOURCES_FILE%"


if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Starting server listening for connections...
    java --enable-native-access=ALL-UNNAMED --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "%OUT_PATH%;%SQLITE_LIB_DIR%\*" main
) else (
    echo Compilation failed!
)

pause

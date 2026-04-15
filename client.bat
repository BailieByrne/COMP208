@echo off
setlocal enabledelayedexpansion

set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set SERVER_PATH=Server
set CLIENT_PATH=client
set OUT_PATH=bin\classes

if not exist "%OUT_PATH%" mkdir "%OUT_PATH%"

echo Compiling Client...
set SOURCES_FILE=%OUT_PATH%\sources.txt
if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"

for /r "%SERVER_PATH%" %%f in (*.java) do echo %%f>>"%SOURCES_FILE%"
for /r "%CLIENT_PATH%" %%f in (*.java) do echo %%f>>"%SOURCES_FILE%"

javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -d "%OUT_PATH%" @"%SOURCES_FILE%"

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Copying Assets folder
    if exist "%OUT_PATH%\Assets" rmdir /s /q "%OUT_PATH%\Assets"
    xcopy "%CLIENT_PATH%\Assets" "%OUT_PATH%\Assets" /E /I /Y >nul
    echo Starting client...
    java --enable-native-access=javafx.graphics --sun-misc-unsafe-memory-access=allow --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "%OUT_PATH%" client
    if %errorlevel% neq 0 (
        echo Retrying without unsafe-memory flag...
        java --enable-native-access=javafx.graphics --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "%OUT_PATH%" client
    )
) else (
    echo Compilation failed!
)

pause

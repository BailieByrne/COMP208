@echo off
setlocal enabledelayedexpansion

set JAVAFX_PATH=Deps\javafx-sdk-17.0.18
set OUT_PATH=bin\classes

echo Compiling Client with Gradle...
call gradlew.bat runClient

if %errorlevel% neq 0 (
    echo Gradle failed, trying manual compile...
)

pause

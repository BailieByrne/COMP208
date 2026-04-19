@echo off
setlocal enabledelayedexpansion
echo === COMP208 Setup ===

REM ── Check for CMake ──────────────────────────────────────
cmake --version >nul 2>&1
if %errorlevel% equ 0 (
    echo CMake already installed.
    goto BUILD_CPP
)

echo CMake not found. Attempting to install...

REM ── Try winget first (windows native) ────────────────────────
winget --version >nul 2>&1
if %errorlevel% equ 0 (
    echo Installing CMake via winget...
    winget install --id Kitware.CMake -e --silent
    REM Refresh PATH so cmake is available in this session
    for /f "tokens=*" %%i in ('where cmake 2^>nul') do set CMAKE_PATH=%%i
    if not defined CMAKE_PATH (
        set "PATH=%PATH%;C:\Program Files\CMake\bin"
    )
    goto BUILD_CPP
)

REM ── Try Chocolatey ─────────────────────────────────────────────────
choco --version >nul 2>&1
if %errorlevel% equ 0 (
    echo Installing CMake via Chocolatey...
    choco install cmake --installargs 'ADD_CMAKE_TO_PATH=System' -y
    set "PATH=%PATH%;C:\Program Files\CMake\bin"
    goto BUILD_CPP
)

REM ── Try Scoop ──────────────
scoop --version >nul 2>&1
if %errorlevel% equ 0 (
    echo Installing CMake via Scoop...
    scoop install cmake
    goto BUILD_CPP
)

REM ── All auto-install methods failed ──────────────────────
echo.
echo ERROR: Could not auto-install CMake.
echo Please install it manually from https://cmake.org/download/
echo Make sure to tick "Add CMake to PATH" during install, then re-run this script.
pause
exit /b 1

:BUILD_CPP
REM ── Build C++ backend ───────────
echo === Building C++ Backend ===
cd Backend
if not exist build mkdir build
cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
cd ..\..
echo C++ backend built successfully.

echo.
echo === Setup complete. Run client.bat or server.bat to start. ===
pause
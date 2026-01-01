@echo off
setlocal

REM --- go to this script's folder (runelite plugin root) ---
cd /d "%~dp0"

REM --- stop any stuck gradle daemons ---
call gradlew.bat --stop >nul 2>&1

REM --- build then run the dev RuneLite client ---
echo Building plugin...
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Leaving window open.
  pause
  exit /b 1
)

echo.
echo Launching RuneLite dev client...
call gradlew.bat run

endlocal

@echo off
REM Run script for Jagex Account Creator (Windows)

set JAR_FILE=target\account-creator-1.0.0-jar-with-dependencies.jar

REM Check if JAR exists
if not exist "%JAR_FILE%" (
    echo ERROR: JAR file not found at %JAR_FILE%
    echo Please build the project first:
    echo   build.bat
    pause
    exit /b 1
)

REM Check if config exists
if not exist "config.toml" (
    echo WARNING: config.toml not found
    echo A default configuration will be created
)

echo ==========================================
echo Jagex Account Creator - Java Edition
echo ==========================================
echo.

REM Run the application
if "%~1"=="" (
    echo Using default config: config.toml
    java -jar "%JAR_FILE%"
) else (
    echo Using config: %~1
    java -jar "%JAR_FILE%" "%~1"
)

pause

@echo off
REM Build script for Jagex Account Creator (Windows)

echo ==========================================
echo Building Jagex Account Creator
echo ==========================================

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven: https://maven.apache.org/install.html
    pause
    exit /b 1
)

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or higher
    pause
    exit /b 1
)

REM Display Java version
echo Java version:
java -version

echo.
echo Building project...
call mvn clean package

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo Build successful!
    echo ==========================================
    echo.
    echo Executable JAR created at:
    echo   target\account-creator-1.0.0-jar-with-dependencies.jar
    echo.
    echo To run the application:
    echo   java -jar target\account-creator-1.0.0-jar-with-dependencies.jar
    echo.
    echo Or use the run script:
    echo   run.bat
    echo.
) else (
    echo.
    echo ==========================================
    echo Build failed!
    echo ==========================================
    pause
    exit /b 1
)

pause

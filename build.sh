#!/bin/bash

# Build script for Jagex Account Creator

echo "=========================================="
echo "Building Jagex Account Creator"
echo "=========================================="

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Display Java version
echo "Java version:"
java -version

echo ""
echo "Building project..."
mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "Build successful!"
    echo "=========================================="
    echo ""
    echo "Executable JAR created at:"
    echo "  target/account-creator-1.0.0-jar-with-dependencies.jar"
    echo ""
    echo "To run the application:"
    echo "  java -jar target/account-creator-1.0.0-jar-with-dependencies.jar"
    echo ""
    echo "Or use the run script:"
    echo "  ./run.sh"
    echo ""
else
    echo ""
    echo "=========================================="
    echo "Build failed!"
    echo "=========================================="
    exit 1
fi

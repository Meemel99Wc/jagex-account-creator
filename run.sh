#!/bin/bash

# Run script for Jagex Account Creator

JAR_FILE="target/account-creator-1.0.0-jar-with-dependencies.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    echo "Please build the project first:"
    echo "  ./build.sh"
    exit 1
fi

# Check if config exists
if [ ! -f "config.toml" ]; then
    echo "WARNING: config.toml not found"
    echo "A default configuration will be created"
fi

echo "=========================================="
echo "Jagex Account Creator - Java Edition"
echo "=========================================="
echo ""

# Run the application
if [ -z "$1" ]; then
    echo "Using default config: config.toml"
    java -jar "$JAR_FILE"
else
    echo "Using config: $1"
    java -jar "$JAR_FILE" "$1"
fi

#!/bin/bash
# Package Lambda deployment zip

cd "$(dirname "$0")"

# Clean previous build
rm -rf package lambda-deployment.zip

# Create package directory
mkdir -p package

# Install dependencies
pip install -r requirements.txt -t package/

# Copy ALL src/ contents to root of package
cp src/*.py package/
cp -r src/generators package/

# Create zip from package root
cd package
zip -r ../lambda-deployment.zip . -x "*.pyc" -x "__pycache__/*"

cd ..
echo "✅ Lambda package created: lambda-deployment.zip"
ls -lh lambda-deployment.zip
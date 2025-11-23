# feature-store/setup.py

from setuptools import setup, find_packages
from pathlib import Path

readme = Path(__file__).parent / "README.md"
long_description = readme.read_text() if readme.exists() else ""

setup(
    name="patternalarm-feature-store",  # ✅ Renamed
    version="0.1.0",
    author="Aurelien Courreges-Clercq",
    description="Feature engineering for PatternAlarm fraud detection",
    long_description=long_description,
    long_description_content_type="text/markdown",

    packages=find_packages(),

    install_requires=[
        "pydantic>=2.0.0",
        "pandas>=2.0.0",
    ],

    extras_require={
        "spark": ["pyspark>=3.5.0"],
        "dev": ["pytest", "black", "mypy"]
    },

    python_requires=">=3.9",

    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
    ]
)
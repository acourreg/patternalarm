# feature-store/setup.py
from setuptools import setup, find_packages

setup(
    name="patternalarm-features",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "pydantic>=2.0.0",
        "pandas>=2.0.0"
    ]
)

# Build
# python setup.py sdist bdist_wheel
# Creates: dist/patternalarm-features-0.1.0.tar.gz
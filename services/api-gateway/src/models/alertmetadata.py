from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class AlertMetadata(BaseModel):
    """Alert metadata structure"""
    windowSeconds: int
    baselineAvg: float
    patternsDetected: List[str]
    confidence: int
    modelVersion: str
    inferenceTimeMs: int

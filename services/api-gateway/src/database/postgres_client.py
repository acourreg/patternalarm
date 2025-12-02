"""
Database session configuration
Async PostgreSQL connection using SQLAlchemy 2.0
"""
import os
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base

# Database URL from environment
# Priority: DB_URL (full URL) > DB_PASSWORD (construct URL)
DB_URL = os.getenv("DB_URL")

if not DB_URL or DB_URL.startswith("jdbc:"):
    # Production mode: construct from separate env vars
    db_user = os.getenv("DB_USER", "dbadmin")
    db_password = os.getenv("DB_PASSWORD", "password")
    db_host = os.getenv("DB_HOST", "localhost")
    db_port = os.getenv("DB_PORT", "5432")
    db_name = os.getenv("DB_NAME", "patternalarm")
    DB_URL = f"postgresql://{db_user}:{db_password}@{db_host}:{db_port}/{db_name}"

# Convert to async driver
ASYNC_DB_URL = DB_URL.replace("postgresql://", "postgresql+asyncpg://")

# Engine
engine = create_async_engine(
    ASYNC_DB_URL,
    echo=False,  # Set to True for SQL logging
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10
)

# Session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False
)

# Base for models
Base = declarative_base()


# Dependency for FastAPI
async def get_db():
    """Dependency to get async DB session"""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()
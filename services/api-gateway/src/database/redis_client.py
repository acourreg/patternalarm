"""
Redis client for caching
Singleton pattern with async support
"""
import os
import json
from typing import Optional, Any
from redis import asyncio as aioredis
from redis.asyncio import Redis
import logging

logger = logging.getLogger(__name__)


class RedisClient:
    """Async Redis client singleton with caching helpers"""

    _instance: Optional[Redis] = None

    @classmethod
    async def get_client(cls) -> Optional[Redis]:
        """Get or create Redis client instance"""
        if cls._instance is None:
            # Priority: REDIS_URL > construct from REDIS_HOST/PORT
            redis_url = os.getenv("REDIS_URL")

            if not redis_url:
                # Construct from individual env vars
                redis_host = os.getenv("REDIS_HOST", "localhost")
                redis_port = os.getenv("REDIS_PORT", "6379")
                redis_password = os.getenv("REDIS_PASSWORD")

                # Build URL with or without password
                if redis_password:
                    redis_url = f"redis://:{redis_password}@{redis_host}:{redis_port}"
                else:
                    redis_url = f"redis://{redis_host}:{redis_port}"

            try:
                cls._instance = await aioredis.from_url(
                    redis_url,
                    encoding="utf-8",
                    decode_responses=True,
                    socket_connect_timeout=5,
                    socket_keepalive=True,
                    health_check_interval=30
                )
                # Test connection
                await cls._instance.ping()
                logger.info(f"✅ Redis connected: {redis_url.split('@')[-1] if '@' in redis_url else redis_url}")
            except Exception as e:
                logger.warning(f"⚠️ Redis connection failed: {e}. Cache disabled.")
                cls._instance = None

        return cls._instance

    @classmethod
    async def get_cached(cls, key: str) -> Optional[dict]:
        """
        Get cached value from Redis
        Returns parsed dict or None if cache miss/error
        """
        client = await cls.get_client()
        if not client:
            return None

        try:
            cached = await client.get(key)
            if cached:
                logger.info(f"🎯 Cache HIT: {key}")
                return json.loads(cached)
            logger.info(f"❌ Cache MISS: {key}")
            return None
        except Exception as e:
            logger.warning(f"⚠️ Cache get failed: {e}")
            return None

    @classmethod
    async def set_cached(cls, key: str, value: Any, ttl_seconds: int = 10):
        """
        Store value in Redis with TTL
        Accepts Pydantic models, dicts, or any JSON-serializable object
        """
        client = await cls.get_client()
        if not client:
            return

        try:
            # Handle Pydantic models
            if hasattr(value, 'model_dump'):
                value = value.model_dump()

            serialized = json.dumps(value, default=str)
            await client.setex(key, ttl_seconds, serialized)
            logger.info(f"💾 Cache SET: {key} (TTL: {ttl_seconds}s)")
        except Exception as e:
            logger.warning(f"⚠️ Cache set failed: {e}")

    @classmethod
    async def close(cls):
        """Close Redis connection"""
        if cls._instance:
            await cls._instance.close()
            cls._instance = None
            logger.info("Redis connection closed")


async def get_redis() -> Optional[Redis]:
    """Dependency to get Redis client (for health checks, etc.)"""
    return await RedisClient.get_client()
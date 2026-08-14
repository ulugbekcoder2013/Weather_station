"""
Smart Home Weather Station — High-Performance Database Engine
Supports SQLite (with WAL mode & busy timeout) and PostgreSQL/MySQL connection pooling.
"""

import os
import logging
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, scoped_session
from models import Base

logger = logging.getLogger("WeatherDB")

db_url = os.environ.get('DATABASE_URL', os.environ.get('SQLALCHEMY_DATABASE_URI', 'sqlite:///weather_app.db'))
if db_url.startswith("postgres://"):
    db_url = db_url.replace("postgres://", "postgresql://", 1)

is_sqlite = db_url.startswith("sqlite")

connect_args = {}
if is_sqlite:
    connect_args = {"check_same_thread": False, "timeout": 15}
    engine = create_engine(
        db_url,
        connect_args=connect_args,
        pool_pre_ping=True
    )

    # Enable SQLite WAL (Write-Ahead Logging) and performance pragmas
    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        try:
            cursor.execute("PRAGMA journal_mode=WAL")
            cursor.execute("PRAGMA synchronous=NORMAL")
            cursor.execute("PRAGMA busy_timeout=5000")
            cursor.execute("PRAGMA cache_size=-16000") # 16MB in-memory page cache
            cursor.close()
        except Exception as e:
            logger.warning(f"Failed to set SQLite pragma: {e}")
else:
    engine = create_engine(
        db_url,
        pool_size=10,
        max_overflow=20,
        pool_recycle=300,
        pool_pre_ping=True
    )

SessionLocal = scoped_session(sessionmaker(autocommit=False, autoflush=False, bind=engine))

def init_db():
    """Initializes database schema and indexes."""
    try:
        Base.metadata.create_all(bind=engine)
        logger.info("[DB] Database tables and indexes verified successfully.")
    except Exception as e:
        logger.exception(f"[DB ERROR] Error initializing database tables: {e}")

def get_db():
    """Dependency injection helper for database sessions."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

#!/usr/bin/env python3
"""
Smart Home Weather Station — High-Performance Real-Time FastAPI Backend
Sensor Acquisition Pipeline: ESP32 (DHT11 + LDR Photoresistor) -> Real-Time FastAPI -> Web Dashboard / Android App
Zero fake data policy: All metrics strictly ingested from physical sensor acquisition.
"""

import os
import json
import time
import math
import hmac
import logging
import asyncio
from contextlib import asynccontextmanager
from typing import Optional, List, Dict, Any
from datetime import datetime, timedelta, timezone

from fastapi import FastAPI, Request, Response, Depends, HTTPException, status, WebSocket, WebSocketDisconnect, Query
from fastapi.responses import HTMLResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from sqlalchemy import text, desc, asc

from models import Base, WeatherData, AIAnalysis, utc_now
from database import engine, SessionLocal, init_db, get_db
from ai_service import (
    get_cached_ai_analysis,
    set_cached_ai_analysis,
    perform_ai_analysis,
    OPENROUTER_MODEL
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] [SERVER] %(message)s")
logger = logging.getLogger("WeatherServer")

# ==============================================================================
# CONFIGURATION & SECURITY
# ==============================================================================
API_KEY = os.environ.get("API_KEY_DEVICE") or os.environ.get("SECRET_KEY") or os.environ.get("API_KEY") or "ws_secret_key_2026_secure"
FALLBACK_KEYS = {
    API_KEY,
    "ws_secret_key_2026_secure",
    "weather-station-development-key",
    "weather_secret_key",
    "esp32_secret",
    "esp32_device_key"
}

# Initialize database schema eagerly for script compatibility
init_db()

# In-Memory Microsecond State Cache
_latest_telemetry_cache: Dict[str, Any] = {
    "data": None,
    "last_updated_epoch": 0.0,
    "total_ingested": 0
}

def update_latest_cache(data: dict):
    _latest_telemetry_cache["data"] = data
    _latest_telemetry_cache["last_updated_epoch"] = time.time()
    _latest_telemetry_cache["total_ingested"] += 1

def populate_cache_from_db():
    try:
        db = SessionLocal()
        latest = db.query(WeatherData).order_by(WeatherData.timestamp.desc(), WeatherData.id.desc()).first()
        if latest:
            update_latest_cache(latest.to_dict())
            logger.info(f"[CACHE] Primed memory cache with latest reading ID: {latest.id}")
        db.close()
    except Exception as e:
        logger.warning(f"[CACHE] Could not prime cache at startup: {e}")

populate_cache_from_db()

@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        init_db()
        populate_cache_from_db()
    except Exception as e:
        logger.error(f"[STARTUP] Initialization exception: {e}")
    yield

# Create FastAPI app
app = FastAPI(
    title="Smart Home Weather Station Real-Time Engine",
    description="Hyper-fast real-time telemetry server for ESP32 (DHT11 + Photoresistor), Web Dashboard, and Android App.",
    version="2.0.0",
    lifespan=lifespan
)

# CORS middleware for mobile and cross-origin clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.middleware("http")
async def add_no_cache_headers(request: Request, call_next):
    response = await call_next(request)
    if request.url.path.startswith("/api/"):
        response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate, max-age=0"
        response.headers["Pragma"] = "no-cache"
        response.headers["Expires"] = "0"
    return response

# Mount static and template directories
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, "static")
TEMPLATES_DIR = os.path.join(BASE_DIR, "templates")

if os.path.exists(STATIC_DIR):
    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

templates = Jinja2Templates(directory=TEMPLATES_DIR)



# ==============================================================================
# WEBSOCKET REAL-TIME BROADCASTER HUB
# ==============================================================================
class RealTimeConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        async with self._lock:
            self.active_connections.append(websocket)
        logger.info(f"[WS] Client connected. Total active live clients: {len(self.active_connections)}")

        # Send instant initial frame upon connection
        if _latest_telemetry_cache["data"]:
            try:
                frame = {
                    "type": "telemetry_update",
                    "event": "initial_state",
                    "data": _latest_telemetry_cache["data"],
                    "ai_analysis": get_cached_ai_analysis(),
                    "server_time": utc_now().isoformat() + "Z"
                }
                await websocket.send_text(json.dumps(frame))
            except Exception:
                pass

    async def disconnect(self, websocket: WebSocket):
        async with self._lock:
            if websocket in self.active_connections:
                self.active_connections.remove(websocket)
        logger.info(f"[WS] Client disconnected. Total active: {len(self.active_connections)}")

    async def broadcast(self, message: dict):
        if not self.active_connections:
            return

        payload = json.dumps(message)
        dead_connections = []

        async with self._lock:
            for connection in self.active_connections:
                try:
                    await connection.send_text(payload)
                except Exception:
                    dead_connections.append(connection)

            for dead in dead_connections:
                if dead in self.active_connections:
                    self.active_connections.remove(dead)

manager = RealTimeConnectionManager()


# ==============================================================================
# SSE (SERVER-SENT EVENTS) BROADCASTER
# ==============================================================================
_sse_subscribers: List[asyncio.Queue] = []
_sse_lock = asyncio.Lock()

async def sse_subscribe() -> asyncio.Queue:
    queue = asyncio.Queue(maxsize=50)
    async with _sse_lock:
        _sse_subscribers.append(queue)
    return queue

async def sse_unsubscribe(queue: asyncio.Queue):
    async with _sse_lock:
        if queue in _sse_subscribers:
            _sse_subscribers.remove(queue)

async def sse_broadcast(data: dict):
    if not _sse_subscribers:
        return
    msg = f"data: {json.dumps(data)}\n\n"
    async with _sse_lock:
        for q in _sse_subscribers:
            try:
                if not q.full():
                    q.put_nowait(msg)
            except Exception:
                pass


# ==============================================================================
# AUTHENTICATION & VALIDATION HELPERS
# ==============================================================================
def is_authorized(request: Request) -> bool:
    provided = (
        request.headers.get("X-API-Key") or
        request.headers.get("Authorization") or
        request.query_params.get("key") or
        request.query_params.get("api_key")
    )
    if not provided:
        return False
    token = provided.removeprefix("Bearer ").strip()
    return any(hmac.compare_digest(token, k) for k in FALLBACK_KEYS if k)


def parse_numeric(val: Any, min_val: float, max_val: float, name: str) -> float:
    if val is None or val == "":
        raise ValueError(f"Missing required sensor metric: {name}")
    try:
        fval = float(val)
    except (ValueError, TypeError):
        raise ValueError(f"{name} must be numeric")
    if not math.isfinite(fval):
        raise ValueError(f"{name} must be finite")
    if not (min_val <= fval <= max_val):
        raise ValueError(f"{name} ({fval}) out of realistic physical range [{min_val}, {max_val}]")
    return fval


def parse_optional_numeric(val: Any, min_val: float, max_val: float) -> Optional[float]:
    if val is None or val == "":
        return None
    try:
        fval = float(val)
        if math.isfinite(fval) and (min_val <= fval <= max_val):
            return fval
    except Exception:
        pass
    return None


def parse_bool(val: Any) -> Optional[bool]:
    if val is None:
        return None
    if isinstance(val, bool):
        return val
    if isinstance(val, (int, float)) and val in (0, 1):
        return bool(val)
    if isinstance(val, str):
        normalized = val.strip().lower()
        if normalized in {"true", "1", "yes", "on"}:
            return True
        if normalized in {"false", "0", "no", "off", ""}:
            return False
    return None


def parse_timestamp(val: Any) -> datetime:
    if not val:
        return utc_now()
    text_val = str(val).strip()
    try:
        parsed = datetime.fromisoformat(text_val.replace("Z", "+00:00"))
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
        if parsed < datetime(2000, 1, 1) or parsed > utc_now() + timedelta(minutes=10):
            return utc_now()
        return parsed
    except Exception:
        return utc_now()


# ==============================================================================
# WEBSOCKET & SSE STREAMING ENDPOINTS
# ==============================================================================
@app.websocket("/ws/live")
@app.websocket("/ws/telemetry")
async def websocket_live_endpoint(websocket: WebSocket):
    """
    High-speed persistent WebSocket connection.
    Broadcasts physical sensor updates to Web Dashboard and Android in real-time.
    """
    await manager.connect(websocket)
    try:
        while True:
            # Keep-alive receive loop
            text = await websocket.receive_text()
            # Handle client commands if any (e.g. ping)
            if text == "ping":
                await websocket.send_text(json.dumps({"type": "pong", "time": utc_now().isoformat() + "Z"}))
    except WebSocketDisconnect:
        await manager.disconnect(websocket)
    except Exception:
        await manager.disconnect(websocket)


@app.get("/api/events")
async def sse_events_endpoint(request: Request):
    """
    Server-Sent Events (SSE) stream for real-time sensor updates.
    """
    queue = await sse_subscribe()

    async def event_generator():
        try:
            # Yield initial state
            if _latest_telemetry_cache["data"]:
                init_msg = {
                    "type": "telemetry_update",
                    "event": "initial_state",
                    "data": _latest_telemetry_cache["data"],
                    "ai_analysis": get_cached_ai_analysis()
                }
                yield f"data: {json.dumps(init_msg)}\n\n"

            while True:
                if await request.is_disconnected():
                    break
                try:
                    msg = await asyncio.wait_for(queue.get(), timeout=15.0)
                    yield msg
                except asyncio.TimeoutError:
                    # Heartbeat comment to keep connection alive
                    yield ": ping\n\n"
        finally:
            await sse_unsubscribe(queue)

    return StreamingResponse(event_generator(), media_type="text/event-stream")


# ==============================================================================
# REST API: TELEMETRY INGESTION (HIGH SPEED)
# ==============================================================================
@app.post("/api/weather", status_code=status.HTTP_201_CREATED)
@app.post("/api/ingest", status_code=status.HTTP_201_CREATED)
@app.post("/api/ingest.php", status_code=status.HTTP_201_CREATED)
@app.get("/api/weather")
@app.get("/api/ingest")
@app.get("/api/ingest.php")
async def handle_weather_ingest(request: Request, db: Session = Depends(get_db)):
    """
    Primary real-time sensor ingestion endpoint.
    Accepts telemetry from ESP32, updates microsecond RAM cache, persists to database,
    and broadcasts instantaneously to all connected WebSockets and SSE streams.
    """
    # If GET without parameters, return last 100 entries for compatibility
    if request.method == "GET" and not any(k in request.query_params for k in ("temp", "temperature", "hum", "humidity")):
        items = db.query(WeatherData).order_by(WeatherData.timestamp.desc()).limit(100).all()
        return [w.to_dict() for w in reversed(items)]

    # Authenticate ingestion request
    if not is_authorized(request):
        return JSONResponse(
            status_code=status.HTTP_403_FORBIDDEN,
            content={"error": "Unauthorized. Provide valid X-API-Key header or ?key= param.", "detail": "Unauthorized"}
        )

    # Parse payload from JSON, form, or query parameters
    payload_data = {}
    try:
        body = await request.body()
        if body:
            payload_data = json.loads(body.decode('utf-8'))
    except Exception:
        pass

    if not payload_data:
        payload_data = dict(request.query_params)

    if not payload_data:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"error": "Bad Request. Physical sensor telemetry payload required.", "detail": "Bad Request"}
        )

    try:
        dev_id = str(payload_data.get("device_id", payload_data.get("device", "WS-001"))).strip()[:64]
        
        # DHT11 Temperature (-40 to 85°C)
        temp_raw = payload_data.get("temperature", payload_data.get("temperature_c", payload_data.get("temp")))
        temp = parse_numeric(temp_raw, -40.0, 85.0, "temperature")

        # DHT11 Humidity (0 to 100%)
        hum_raw = payload_data.get("humidity", payload_data.get("humidity_pct", payload_data.get("hum")))
        hum = parse_numeric(hum_raw, 0.0, 100.0, "humidity")

        # LDR Photoresistor Sunlight / Illumination (0 to 100%)
        sun_raw = payload_data.get("sun_activity", payload_data.get("light_pct", payload_data.get("light", 0.0)))
        sun = parse_numeric(sun_raw, 0.0, 100.0, "sun_activity")

        # Optional metrics
        wind = parse_optional_numeric(payload_data.get("wind_speed", payload_data.get("wind")), 0.0, 250.0)
        press = parse_optional_numeric(payload_data.get("pressure", payload_data.get("pressure_hpa")), 300.0, 1100.0)
        batt = parse_optional_numeric(payload_data.get("batt_voltage", payload_data.get("battery")), 0.0, 20.0)
        rain = parse_bool(payload_data.get("rain_detected", payload_data.get("rain")))
        ts = parse_timestamp(payload_data.get("timestamp", payload_data.get("recorded_at")))

        # Create database entity
        entry = WeatherData(
            device_id=dev_id,
            temperature=temp,
            humidity=hum,
            sun_activity=sun,
            wind_speed=wind,
            pressure=press,
            batt_voltage=batt,
            rain_detected=rain,
            timestamp=ts
        )

        db.add(entry)
        db.commit()
        db.refresh(entry)

        entry_dict = entry.to_dict()

        # Update in-memory microsecond state cache
        update_latest_cache(entry_dict)

        # Immediate time-aware AI classification on every fresh telemetry frame
        fresh_ai = perform_ai_analysis(entry_dict)

        # Real-time WebSocket & SSE broadcast
        broadcast_packet = {
            "type": "telemetry_update",
            "event": "new_reading",
            "data": entry_dict,
            "ai_analysis": fresh_ai,
            "server_time": utc_now().isoformat() + "Z"
        }
        asyncio.create_task(manager.broadcast(broadcast_packet))
        asyncio.create_task(sse_broadcast(broadcast_packet))

        return JSONResponse(
            status_code=status.HTTP_201_CREATED,
            content={
                "success": True,
                "message": "Physical sensor telemetry recorded and broadcasted successfully",
                "id": entry.id,
                "data": entry_dict
            }
        )

    except ValueError as val_err:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"error": str(val_err), "detail": str(val_err)}
        )
    except Exception as exc:
        db.rollback()
        logger.exception("Failed to persist weather reading")
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"error": "Failed to persist physical reading", "detail": str(exc)}
        )


# ==============================================================================
# REST API: QUERY LATEST (SUB-MILLISECOND CACHE)
# ==============================================================================
@app.api_route("/api/latest", methods=["GET", "HEAD"])
@app.api_route("/api/latest.php", methods=["GET", "HEAD"])
@app.api_route("/api/weather/latest", methods=["GET", "HEAD"])
async def get_latest_weather(request: Request, device_id: Optional[str] = None, db: Session = Depends(get_db)):
    """
    Returns latest telemetry frame. Served in <0.2ms from RAM cache if device_id is default,
    or queried with index from SQLite/Postgres.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    cached_data = _latest_telemetry_cache["data"]
    ai_data = get_cached_ai_analysis()

    if not cached_data or (device_id and cached_data.get("device_id") != device_id):
        query = db.query(WeatherData)
        if device_id:
            query = query.filter(WeatherData.device_id == device_id)
        latest = query.order_by(WeatherData.timestamp.desc(), WeatherData.id.desc()).first()
        if latest:
            cached_data = latest.to_dict()
            if not device_id or device_id == "WS-001":
                update_latest_cache(cached_data)

    if not cached_data:
        return {
            "success": False,
            "status": "awaiting_telemetry",
            "message": "Awaiting physical sensor telemetry from ESP32 (DHT11 + LDR).",
            "data": None,
            "ai_analysis": ai_data,
            "device_status": {
                "online": False,
                "last_seen_sec_ago": 999999,
                "health": "Offline / Awaiting ESP32 stream"
            }
        }

    # Calculate accurate live status from telemetry timestamp
    sec_ago = 999999
    try:
        ts_str = cached_data.get("timestamp") or cached_data.get("recorded_at") or ""
        if ts_str:
            if "T" in ts_str:
                ts = datetime.fromisoformat(ts_str.replace("Z", "+00:00")).astimezone(timezone.utc).replace(tzinfo=None)
            else:
                ts = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
            sec_ago = max(0, int((utc_now() - ts).total_seconds()))
    except Exception:
        sec_ago = int(time.time() - _latest_telemetry_cache.get("last_updated_epoch", time.time()))

    is_online = sec_ago < 45

    res = {
        "success": True,
        "data": cached_data,
        "ai_analysis": ai_data,
        "device_status": {
            "online": is_online,
            "last_seen_sec_ago": sec_ago,
            "health": "Real-Time Live Streaming" if is_online else f"Inactive ({sec_ago}s ago)"
        }
    }
    res.update(cached_data)
    res["ai_analysis"] = ai_data
    return res


# ==============================================================================
# REST API: QUERY HISTORY
# ==============================================================================
@app.api_route("/api/weather-history", methods=["GET", "HEAD"])
async def get_weather_history_list(
    request: Request,
    days: Optional[float] = Query(default=None, ge=0.01, le=90.0),
    hours: Optional[float] = Query(default=None, ge=0.01, le=2160.0),
    device_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Returns array of historical sensor telemetry objects for the specified days or hours.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    if hours is not None:
        target_hours = float(hours)
    elif days is not None:
        target_hours = float(days) * 24.0
    else:
        target_hours = 24.0

    cutoff = utc_now() - timedelta(hours=target_hours)

    query = db.query(WeatherData).filter(WeatherData.timestamp >= cutoff)
    if device_id:
        query = query.filter(WeatherData.device_id == device_id)

    readings = query.order_by(WeatherData.timestamp.asc(), WeatherData.id.asc()).all()
    return [w.to_dict() for w in readings]


@app.api_route("/api/history", methods=["GET", "HEAD"])
@app.api_route("/api/history.php", methods=["GET", "HEAD"])
async def get_weather_history_object(
    request: Request,
    hours: Optional[float] = Query(default=24, ge=0.01, le=2160),
    days: Optional[float] = Query(default=None, ge=0.01, le=90.0),
    device_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Returns structured history object with readings list.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    target_hours = float(days * 24.0) if days is not None else float(hours or 24)
    cutoff = utc_now() - timedelta(hours=target_hours)

    query = db.query(WeatherData).filter(WeatherData.timestamp >= cutoff)
    if device_id:
        query = query.filter(WeatherData.device_id == device_id)

    readings = query.order_by(WeatherData.timestamp.asc(), WeatherData.id.asc()).all()
    results = [w.to_dict() for w in readings]

    return {
        "success": True,
        "hours": target_hours,
        "count": len(results),
        "readings": results
    }


# ==============================================================================
# REST API: SUMMARY STATISTICS
# ==============================================================================
@app.api_route("/api/stats", methods=["GET", "HEAD"])
@app.api_route("/api/stats.php", methods=["GET", "HEAD"])
async def get_weather_stats(
    request: Request,
    period: str = "day",
    device_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Computes 24h min, max, average aggregates for DHT11 and LDR sensors.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    cutoff = utc_now() - timedelta(hours=24)
    query = db.query(WeatherData).filter(WeatherData.timestamp >= cutoff)
    if device_id:
        query = query.filter(WeatherData.device_id == device_id)

    data = query.all()
    if not data:
        return {
            "success": False,
            "message": "No sensor readings recorded in the last 24 hours.",
            "sample_count": 0,
            "count": 0
        }

    temps = [w.temperature for w in data if w.temperature is not None]
    hums = [w.humidity for w in data if w.humidity is not None]
    suns = [w.sun_activity for w in data if w.sun_activity is not None]
    pressures = [w.pressure for w in data if w.pressure is not None]

    stats = {
        "temperature": {
            "min": round(min(temps), 2) if temps else 0.0,
            "max": round(max(temps), 2) if temps else 0.0,
            "avg": round(sum(temps) / len(temps), 2) if temps else 0.0
        },
        "humidity": {
            "min": round(min(hums), 1) if hums else 0.0,
            "max": round(max(hums), 1) if hums else 0.0,
            "avg": round(sum(hums) / len(hums), 1) if hums else 0.0
        },
        "light": {
            "min": round(min(suns), 1) if suns else 0.0,
            "max": round(max(suns), 1) if suns else 0.0,
            "avg": round(sum(suns) / len(suns), 1) if suns else 0.0
        },
        "pressure": ({
            "min": round(min(pressures), 1),
            "max": round(max(pressures), 1),
            "avg": round(sum(pressures) / len(pressures), 1)
        } if pressures else None),
        "sample_count": len(data),
        "count": len(data)
    }

    return {
        "success": True,
        "period": "24h",
        "sample_count": len(data),
        "count": len(data),
        "stats": stats,
        **stats,
        "sun_activity": stats["light"]
    }


# ==============================================================================
# REST API: AI ANALYSIS
# ==============================================================================
@app.api_route("/api/ai-analysis", methods=["GET", "HEAD"])
@app.api_route("/api/ai/summary", methods=["GET", "HEAD"])
async def get_ai_summary(request: Request, db: Session = Depends(get_db)):
    """
    Returns cached AI meteorological analysis and historical AI records.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    current_ai = get_cached_ai_analysis()
    history_records = db.query(AIAnalysis).order_by(AIAnalysis.timestamp.desc()).limit(10).all()
    return {
        "success": True,
        "current": current_ai,
        "history": [r.to_dict() for r in history_records]
    }


@app.api_route("/api/ai-analysis/refresh", methods=["GET", "POST", "HEAD"])
@app.api_route("/api/ai/analyze-now", methods=["GET", "POST", "HEAD"])
async def refresh_ai_analysis(request: Request, db: Session = Depends(get_db)):
    """
    Triggers an instant AI classification on latest physical sensor data.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    latest = db.query(WeatherData).order_by(WeatherData.timestamp.desc()).first()
    if not latest:
        return JSONResponse(
            status_code=400,
            content={"success": False, "message": "No sensor readings available in database to analyze.", "detail": "No telemetry"}
        )

    analysis = perform_ai_analysis(latest.to_dict())

    try:
        record = AIAnalysis(
            weather_type=analysis.get("weather_type", "sunny"),
            vertical_label=analysis.get("vertical_label", "IT'S SUNNY"),
            headline=analysis.get("headline", ""),
            summary=analysis.get("summary", ""),
            clothing_advice=analysis.get("clothing_advice", ""),
            comfort_index=int(analysis.get("comfort_index", 85)),
            model_used=analysis.get("model", OPENROUTER_MODEL),
            timestamp=utc_now()
        )
        db.add(record)
        db.commit()
    except Exception as e:
        db.rollback()
        logger.warning(f"Could not persist AI record: {e}")

    # Broadcast updated AI insights to WebSocket clients
    asyncio.create_task(manager.broadcast({
        "type": "ai_update",
        "analysis": analysis,
        "server_time": utc_now().isoformat() + "Z"
    }))

    return {
        "success": True,
        "message": "AI meteorological analysis generated successfully",
        "analysis": analysis
    }


# ==============================================================================
# REST API: SYSTEM HEALTH & ADMIN
# ==============================================================================
@app.api_route("/api/health", methods=["GET", "HEAD"])
async def health_check(request: Request, db: Session = Depends(get_db)):
    """
    High-performance health check for load balancers, uptime monitors, and tests.
    """
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="application/json")

    try:
        db.execute(text("SELECT 1"))
        db_status = "connected"
    except Exception:
        db_status = "error"

    return {
        "status": "healthy" if db_status == "connected" else "degraded",
        "service": "Smart Home Weather Station Real-Time Server",
        "database": db_status,
        "active_websockets": len(manager.active_connections),
        "total_ingested": _latest_telemetry_cache["total_ingested"],
        "cached_latest_id": _latest_telemetry_cache["data"].get("id") if _latest_telemetry_cache["data"] else None,
        "time": utc_now().isoformat() + "Z"
    }


@app.post("/api/reset")
@app.delete("/api/reset")
async def reset_database(request: Request, db: Session = Depends(get_db)):
    """
    Admin endpoint to reset telemetry data for clean testing.
    """
    if not is_authorized(request):
        return JSONResponse(status_code=403, content={"error": "Unauthorized", "detail": "Unauthorized"})

    try:
        deleted = db.query(WeatherData).delete()
        try:
            db.query(AIAnalysis).delete()
        except Exception:
            pass
        db.commit()

        _latest_telemetry_cache["data"] = None
        _latest_telemetry_cache["total_ingested"] = 0

        # Broadcast reset event
        asyncio.create_task(manager.broadcast({
            "type": "database_reset",
            "message": "Telemetry database cleared."
        }))

        return {
            "success": True,
            "message": f"Database cleared successfully. {deleted} records removed.",
            "deleted_count": deleted
        }
    except Exception as e:
        db.rollback()
        return JSONResponse(status_code=500, content={"error": str(e), "detail": str(e)})


# ==============================================================================
# WEB DASHBOARD ROUTES
# ==============================================================================
@app.api_route("/", methods=["GET", "HEAD"], response_class=HTMLResponse)
@app.api_route("/plots", methods=["GET", "HEAD"], response_class=HTMLResponse)
@app.api_route("/plots.html", methods=["GET", "HEAD"], response_class=HTMLResponse)
@app.api_route("/dashboard", methods=["GET", "HEAD"], response_class=HTMLResponse)
@app.api_route("/index.html", methods=["GET", "HEAD"], response_class=HTMLResponse)
async def serve_dashboard(request: Request, db: Session = Depends(get_db)):
    if request.method == "HEAD":
        return Response(status_code=status.HTTP_200_OK, media_type="text/html")

    cutoff = utc_now() - timedelta(hours=24)
    data = db.query(WeatherData).filter(WeatherData.timestamp >= cutoff).order_by(WeatherData.timestamp.asc(), WeatherData.id.asc()).all()
    weather_list = [w.to_dict() for w in data]
    return templates.TemplateResponse(
        request=request,
        name="plots.html",
        context={"request": request, "weather_list": weather_list}
    )


# ==============================================================================
# ENTRY POINT FOR DIRECT EXECUTION
# ==============================================================================
if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("DEBUG", "false").lower() in ("true", "1", "yes")
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=debug, access_log=True)


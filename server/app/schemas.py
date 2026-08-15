"""
Smart Home Weather Station — Pydantic Schemas
Fast, strict data validation for sensor telemetry, historical queries, and AI analysis.
"""

from typing import Optional, List, Any, Dict
from pydantic import BaseModel, Field, field_validator
import math

class TelemetryPayload(BaseModel):
    device_id: Optional[str] = Field(default="WS-001", max_length=64)
    temperature: float = Field(..., description="Temperature in Celsius (DHT11)")
    humidity: float = Field(..., description="Relative Humidity in % (DHT11)")
    sun_activity: Optional[float] = Field(default=None, description="Optional legacy parameter")
    light_pct: Optional[float] = Field(default=None, description="Optional legacy parameter")
    wind_speed: Optional[float] = Field(default=None, description="Wind speed in m/s")
    pressure: Optional[float] = Field(default=None, description="Barometric pressure in hPa")
    batt_voltage: Optional[float] = Field(default=None, description="Battery voltage in V")
    rain_detected: Optional[bool] = Field(default=None, description="Rain detection flag")
    timestamp: Optional[str] = Field(default=None, description="ISO-8601 recorded timestamp")
    recorded_at: Optional[str] = Field(default=None, description="Alternative timestamp string")

    @field_validator("temperature")
    @classmethod
    def validate_temp(cls, v: float) -> float:
        if not math.isfinite(v) or v < -40.0 or v > 85.0:
            raise ValueError("Temperature must be between -40.0°C and 85.0°C")
        return round(v, 2)

    @field_validator("humidity")
    @classmethod
    def validate_humidity(cls, v: float) -> float:
        if not math.isfinite(v) or v < 0.0 or v > 100.0:
            raise ValueError("Humidity must be between 0.0% and 100.0%")
        return round(v, 1)

class TelemetryResponse(BaseModel):
    success: bool = True
    message: str
    id: Optional[int] = None
    data: Optional[Dict[str, Any]] = None

class DeviceStatus(BaseModel):
    online: bool
    last_seen_sec_ago: int
    health: str

class LatestReadingResponse(BaseModel):
    success: bool
    status: Optional[str] = "live"
    data: Optional[Dict[str, Any]] = None
    ai_analysis: Optional[Dict[str, Any]] = None
    device_status: DeviceStatus

"""
Smart Home Weather Station — SQLAlchemy Data Models
High-performance database entities for physical sensor telemetry and AI analysis.
"""

from datetime import datetime, timezone, timedelta
from sqlalchemy import Column, Integer, Float, String, Boolean, DateTime, Text, Index
from sqlalchemy.orm import declarative_base

Base = declarative_base()

def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)

class WeatherData(Base):
    __tablename__ = 'weather_data'

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(64), nullable=False, default="WS-001", index=True)
    temperature = Column(Float, nullable=False) # DHT11 Temperature in °C
    humidity = Column(Float, nullable=False)    # DHT11 Relative Humidity in %
    sun_activity = Column(Float, nullable=True, default=None) # Optional legacy column
    wind_speed = Column(Float, nullable=True)
    pressure = Column(Float, nullable=True)
    batt_voltage = Column(Float, nullable=True)
    rain_detected = Column(Boolean, nullable=True)
    timestamp = Column(DateTime, default=utc_now, nullable=False, index=True)

    __table_args__ = (
        Index('ix_weather_data_dev_time', 'device_id', 'timestamp'),
    )

    def to_dict(self) -> dict:
        ts = self.timestamp or utc_now()
        local_ts = ts + timedelta(hours=5)
        time_iso = ts.strftime("%Y-%m-%dT%H:%M:%SZ")
        time_str = ts.strftime("%Y-%m-%d %H:%M:%S")

        hour = local_ts.hour
        if hour < 5:
            time_of_day = "Late Night"
            light_cond = "Night"
        elif hour < 8:
            time_of_day = "Sunrise / Dawn"
            light_cond = "Dawn"
        elif hour < 12:
            time_of_day = "Morning"
            light_cond = "Daylight"
        elif hour < 17:
            time_of_day = "Midday / Afternoon"
            light_cond = "Daylight"
        elif hour < 20:
            time_of_day = "Sunset / Dusk"
            light_cond = "Dusk"
        elif hour < 22:
            time_of_day = "Evening Twilight"
            light_cond = "Twilight"
        else:
            time_of_day = "Nighttime"
            light_cond = "Night"

        hum_val = round(self.humidity, 1) if self.humidity is not None else 0.0
        hum_cond = "Humid" if hum_val > 65 else ("Dry" if hum_val < 30 else "Optimal Comfort")
        temp_val = round(self.temperature, 2) if self.temperature is not None else 0.0

        return {
            "id": self.id,
            "device_id": self.device_id or "WS-001",
            "temperature": temp_val,
            "temperature_c": temp_val,
            "humidity": hum_val,
            "humidity_pct": hum_val,
            "sun_activity": None,
            "light_pct": None,
            "time_of_day": time_of_day,
            "wind_speed": round(self.wind_speed * 3.6, 2) if self.wind_speed is not None else None,
            "pressure": round(self.pressure, 1) if self.pressure is not None else None,
            "batt_voltage": round(self.batt_voltage, 2) if self.batt_voltage is not None else None,
            "rain_detected": bool(self.rain_detected) if self.rain_detected is not None else False,
            "sensor_source": "DHT11 (Temp/Hum)",
            "recorded_at": time_str,
            "timestamp": time_iso,
            "light_condition": light_cond,
            "condition_summary": f"{light_cond} & {hum_cond}"
        }


class AIAnalysis(Base):
    __tablename__ = 'ai_analysis'

    id = Column(Integer, primary_key=True, autoincrement=True)
    weather_type = Column(String(32), nullable=False, default="sunny")
    vertical_label = Column(String(64), nullable=False, default="IT'S SUNNY")
    headline = Column(String(128), nullable=True)
    summary = Column(Text, nullable=True)
    clothing_advice = Column(Text, nullable=True)
    comfort_index = Column(Integer, nullable=False, default=85)
    model_used = Column(String(64), nullable=True)
    time_context = Column(String(64), nullable=True)
    local_time = Column(String(32), nullable=True)
    timestamp = Column(DateTime, default=utc_now, nullable=False, index=True)

    def to_dict(self) -> dict:
        ts = self.timestamp or utc_now()
        local_ts = ts + timedelta(hours=5)
        time_str = ts.strftime("%Y-%m-%d %H:%M:%S")
        time_iso = ts.strftime("%Y-%m-%dT%H:%M:%SZ")
        return {
            "id": self.id,
            "weather_type": self.weather_type,
            "vertical_label": self.vertical_label,
            "headline": self.headline,
            "summary": self.summary,
            "clothing_advice": self.clothing_advice,
            "comfort_index": self.comfort_index,
            "model": self.model_used,
            "time_context": self.time_context,
            "local_time": self.local_time or local_ts.strftime("%Y-%m-%d %H:%M:%S"),
            "recorded_at": time_str,
            "timestamp": time_iso
        }

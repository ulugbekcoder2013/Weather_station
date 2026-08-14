"""
Smart Home Weather Station — SQLAlchemy Data Models
High-performance database entities for physical sensor telemetry and AI analysis.
"""

from datetime import datetime, timezone
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
    sun_activity = Column(Float, nullable=False, default=0.0) # LDR Photoresistor Light %
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
        time_iso = ts.strftime("%Y-%m-%dT%H:%M:%SZ")
        time_str = ts.strftime("%Y-%m-%d %H:%M:%S")

        light_val = round(self.sun_activity, 1)
        light_cond = "Bright Sunlight" if light_val > 70 else ("Dim / Low Light" if light_val < 25 else "Moderate Light")
        hum_val = round(self.humidity, 1)
        hum_cond = "Humid" if hum_val > 65 else ("Dry" if hum_val < 30 else "Optimal Comfort")

        return {
            "id": self.id,
            "device_id": self.device_id,
            "temperature": round(self.temperature, 2),
            "temperature_c": round(self.temperature, 2),
            "humidity": hum_val,
            "humidity_pct": hum_val,
            "sun_activity": light_val,
            "light_pct": light_val,
            "wind_speed": round(self.wind_speed * 3.6, 2) if self.wind_speed is not None else None,
            "pressure": round(self.pressure, 1) if self.pressure is not None else None,
            "batt_voltage": round(self.batt_voltage, 2) if self.batt_voltage is not None else None,
            "rain_detected": self.rain_detected,
            "sensor_source": "DHT11 (Temp/Hum) + LDR (Light)",
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
    timestamp = Column(DateTime, default=utc_now, nullable=False, index=True)

    def to_dict(self) -> dict:
        ts = self.timestamp or utc_now()
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
            "recorded_at": time_str,
            "timestamp": time_iso
        }

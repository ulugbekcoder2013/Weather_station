#!/usr/bin/env python3
"""
Aura Weather Station — AI Meteorological Intelligence Service
Powered by OpenRouter API (nvidia/nemotron-3-ultra-550b-a55b:free)

Periodically analyzes physical sensor telemetry from the database (temperature,
humidity, light intensity, barometric pressure, rain detection, time of day)
and generates hyper-professional meteorological classification, editorial vertical labels,
comfort indices, and clothing recommendations.
"""

import os
import json
import logging
import threading
import time
import ssl
import urllib.request
from datetime import datetime, timedelta, timezone

# Logging configuration
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] [AI] %(message)s')
logger = logging.getLogger("AuraAI")

# OpenRouter Configuration
OPENROUTER_API_KEY = os.environ.get(
    'OPENROUTER_API_KEY',
    ''
)
OPENROUTER_MODEL = os.environ.get(
    'OPENROUTER_MODEL',
    'nvidia/nemotron-3-ultra-550b-a55b:free'
)
OPENROUTER_FALLBACK_MODELS = [
    'nvidia/nemotron-3-ultra-550b-a55b:free',
    'nvidia/nemotron-3.5-lightning:free',
    'nvidia/nemotron-3-super-120b-a12b:free',
    'nvidia/nemotron-3-nano-30b-a3b:free',
    'google/gemma-4-26b-a4b-it:free',
    'liquid/lfm-2.5-2.6b:free'
]
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)

# Global in-memory cache for latest AI analysis
_latest_ai_cache = {
    "weather_type": "sunny",
    "vertical_label": "IT'S SUNNY",
    "headline": "Optimal Mediterranean Climate",
    "summary": "Clear radiant skies with abundant solar irradiance and balanced ambient thermal levels.",
    "clothing_advice": "Comfortable light attire and sunglasses recommended for outdoor activities.",
    "comfort_index": 92,
    "analyzed_at": utc_now().isoformat() + "Z",
    "model": OPENROUTER_MODEL,
    "status": "initialized"
}
_cache_lock = threading.Lock()

def get_cached_ai_analysis() -> dict:
    """Returns a copy of the latest cached AI analysis."""
    with _cache_lock:
        return dict(_latest_ai_cache)

def set_cached_ai_analysis(data: dict):
    """Updates the in-memory cache."""
    with _cache_lock:
        _latest_ai_cache.update(data)

def _get_time_context(reading_dict: dict) -> tuple:
    """
    Extracts local time in Uzbekistan (Asia/Tashkent UTC+5), hour, and descriptive astronomical time context.
    Raw database timestamps are in UTC and are converted accurately with +5 hours offset.
    """
    now_utc = utc_now()
    recorded_at = reading_dict.get('recorded_at') or reading_dict.get('timestamp')
    
    local_dt = now_utc + timedelta(hours=5)
    if recorded_at:
        try:
            ts_str = str(recorded_at).strip()
            if 'T' in ts_str:
                ts_clean = ts_str.replace('Z', '')
                parsed = datetime.fromisoformat(ts_clean)
                local_dt = parsed + timedelta(hours=5)
            elif ' ' in ts_str:
                parsed = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
                local_dt = parsed + timedelta(hours=5)
        except Exception:
            pass

    hour = local_dt.hour
    minute = local_dt.minute
    time_str = f"{hour:02d}:{minute:02d}"

    if hour < 5:
        context = "Late Night / Midnight"
    elif hour < 8:
        context = "Early Dawn / Sunrise"
    elif hour < 12:
        context = "Morning Daylight"
    elif hour < 17:
        context = "Midday / Afternoon"
    elif hour < 20:
        context = "Golden Hour / Sunset"
    elif hour < 22:
        context = "Evening Twilight"
    else:
        context = "Nocturnal Nighttime"

    return local_dt, hour, time_str, context

def _build_ai_prompt(reading_dict: dict) -> str:
    """Constructs a structured prompt for the LLM based on physical sensor data and time of day."""
    temp_c = reading_dict.get('temperature', 22.0)
    temp_f = round(temp_c * 9/5 + 32, 1)
    humidity = reading_dict.get('humidity', 50.0)
    pressure = reading_dict.get('pressure', 1013.25)
    wind_speed = reading_dict.get('wind_speed', 0.0)
    rain_detected = reading_dict.get('rain_detected', False)
    
    _, hour, time_str, time_context = _get_time_context(reading_dict)

    prompt = f"""You are an elite meteorological AI.
Analyze the following physical sensor telemetry from our smart home weather station:
- Temperature: {temp_c}°C ({temp_f}°F)
- Relative Humidity: {humidity}%
- Barometric Air Pressure: {pressure} hPa
- Wind Velocity: {wind_speed} km/h
- Rain Detection Sensor: {"RAIN DETECTED (Precipitation Active)" if rain_detected else "No Rain (Dry Surface)"}
- Current Local Time: {time_str} ({time_context})

CRITICAL INSTRUCTION:
Your analysis, headline, summary, and clothing recommendation MUST explicitly incorporate the current time of day ({time_context} at {time_str}) and sensor metrics.
- If it is Nighttime or Midnight ({time_str}), analyze nocturnal climate, nocturnal temperature comfort, and sleep/evening clothing.
- If it is Dawn / Morning, analyze morning air, sunrise transition, and morning attire.
- If it is Midday or Afternoon, analyze daytime atmosphere, thermal comfort, and outdoor activities.
- If it is Sunset / Dusk, analyze cooling dusk breezes and evening wear.

Valid 'weather_type' values MUST be one of:
- "sunny" (daytime clear sky)
- "sunset" (golden hour dusk, evening sky)
- "nighttime" (night hours, moonlit / nocturnal)
- "sunrise" (dawn, early morning transition)
- "rain" (active rain or high humidity precipitation)
- "thunderstorm" (violent weather, pressure drop + rain)
- "snow" (sub-zero temperatures below 2°C with moisture)
- "foggy" (dense moisture, mist, low visibility)

JSON Output Schema:
{{
  "weather_type": "<one of the valid weather types above>",
  "vertical_label": "<2-3 words uppercase punchy editorial phrase, e.g. 'CLEAR NIGHT', 'IT'S SUNNY', 'GOLDEN DUSK', 'TRANQUIL RAIN', 'CRISP WINTER'>",
  "headline": "<elegant 3-6 word weather headline incorporating time/atmosphere>",
  "summary": "<1-2 concise sentences of high-end editorial meteorological description reflecting time of day and sensor data>",
  "clothing_advice": "<actionable clothing and outdoor/indoor comfort suggestion for this specific time of day>",
  "comfort_index": <integer from 0 to 100 representing human biometeorological comfort>
}}"""
    return prompt

def perform_ai_analysis(reading_dict: dict) -> dict:
    """
    Sends sensor readings to OpenRouter model (nvidia/nemotron-3-ultra-550b-a55b:free)
    and parses the returned structured JSON response.
    """
    if not reading_dict:
        logger.warning("No telemetry provided for AI analysis.")
        return get_cached_ai_analysis()

    if not OPENROUTER_API_KEY or not OPENROUTER_API_KEY.strip():
        logger.info("No OPENROUTER_API_KEY configured; utilizing built-in local meteorological heuristic engine.")
        heuristic_res = _heuristic_fallback(reading_dict)
        set_cached_ai_analysis(heuristic_res)
        return heuristic_res

    prompt = _build_ai_prompt(reading_dict)
    
    headers = {
        "Authorization": f"Bearer {OPENROUTER_API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/ulugbekcoder2013/Weather_station",
        "X-Title": "Aura Weather Station AI Intelligence"
    }

    models_to_try = [OPENROUTER_MODEL] + [m for m in OPENROUTER_FALLBACK_MODELS if m != OPENROUTER_MODEL]

    for model_name in models_to_try:
        payload = {
            "model": model_name,
            "messages": [
                {
                    "role": "system",
                    "content": "You are a professional meteorological AI. You strictly reply in valid JSON format only, without markdown wrapping or preamble."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            "temperature": 0.2,
            "max_tokens": 2048
        }

        try:
            logger.info(f"Submitting sensor telemetry to OpenRouter model: {model_name}...")
            ctx = ssl.create_default_context()
            req = urllib.request.Request(
                OPENROUTER_URL,
                data=json.dumps(payload).encode('utf-8'),
                headers=headers
            )
            
            with urllib.request.urlopen(req, context=ctx, timeout=12) as resp:
                    body_str = resp.read().decode('utf-8').strip()
                    resp_data = json.loads(body_str)
                    choices = resp_data.get('choices', [])
                    if choices:
                        msg = choices[0].get('message', {})
                        raw_content = (msg.get('content') or msg.get('reasoning') or '').strip()
                        
                        # Robust JSON extraction from LLM response
                        first_brace = raw_content.find('{')
                        last_brace = raw_content.rfind('}')
                        if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
                            json_str = raw_content[first_brace:last_brace + 1]
                            parsed = json.loads(json_str)
                        elif raw_content:
                            parsed = json.loads(raw_content)
                        else:
                            raise ValueError("Empty message content returned by model.")

                        local_dt, _, time_str, time_context = _get_time_context(reading_dict)
                        parsed['time_str'] = time_str
                        parsed['time_context'] = time_context
                        parsed['local_time'] = local_dt.strftime("%Y-%m-%d %H:%M:%S")
                        parsed['analyzed_at'] = utc_now().isoformat() + "Z"
                        parsed['model'] = model_name
                        parsed['status'] = "success"

                        # Validate weather_type
                        valid_types = {"sunny", "sunset", "nighttime", "sunrise", "rain", "thunderstorm", "snow", "foggy"}
                        if parsed.get('weather_type') not in valid_types:
                            parsed['weather_type'] = _heuristic_weather_type(reading_dict)

                        set_cached_ai_analysis(parsed)
                        logger.info(f"AI Inference Successful! Weather: {parsed.get('weather_type')} | Label: {parsed.get('vertical_label')} | Model: {model_name}")
                        return parsed

        except Exception as ex:
            logger.warning(f"Model {model_name} failed: {ex}. Trying next model...")

    # Fallback to smart heuristic if all models unavailable
    logger.error("All OpenRouter models failed. Falling back to local heuristic analysis.")
    heuristic_res = _heuristic_fallback(reading_dict)
    set_cached_ai_analysis(heuristic_res)
    return heuristic_res

def _heuristic_weather_type(reading: dict) -> str:
    """Calculates weather condition type based on physical sensor rules and astronomical time logic."""
    is_rain = reading.get('rain_detected', False)
    temp = reading.get('temperature', 20.0)
    hum = reading.get('humidity', 50.0)

    _, hour, _, _ = _get_time_context(reading)

    if is_rain:
        if temp > 18 and hum > 85:
            return "thunderstorm"
        return "rain"
    if temp <= 1.5 and hum > 70:
        return "snow"
    if hum >= 90:
        return "foggy"

    # Astronomical time-of-day mapping
    if hour < 5 or hour >= 22:
        return "nighttime"
    if 5 <= hour < 8:
        return "sunrise"
    if 19 <= hour < 22:
        return "sunset"

    # Daytime (08:00 - 19:00)
    return "sunny"

def _heuristic_fallback(reading: dict) -> dict:
    """Generates hyper-refined, time-aware meteorological insights based on physical telemetry."""
    wtype = _heuristic_weather_type(reading)
    temp = reading.get('temperature', 22.0)
    hum = reading.get('humidity', 50.0)
    
    local_dt, _, time_str, time_context = _get_time_context(reading)

    if wtype == "nighttime":
        if temp >= 24.0:
            label = "WARM NIGHT"
            headline = f"Warm Midnight Climate ({time_str})"
            summary = f"At {time_str} ({time_context}), ambient nocturnal temperature is comfortably warm at {temp:.1f}°C with {hum:.1f}% humidity under clear night skies."
            advice = "Lightweight breathable nightwear recommended. Keep bedroom ventilated for optimal rest."
            comfort = 88
        elif temp < 16.0:
            label = "CRISP NIGHT"
            headline = f"Cool Night Atmosphere ({time_str})"
            summary = f"At {time_str} ({time_context}), nighttime temperature drops to a crisp {temp:.1f}°C with {hum:.1f}% relative humidity."
            advice = "Warm sleepwear and cozy layers recommended; light jacket if venturing outdoors."
            comfort = 82
        else:
            label = "CLEAR NIGHT"
            headline = f"Tranquil Nighttime Climate ({time_str})"
            summary = f"At {time_str} ({time_context}), stable nocturnal conditions record {temp:.1f}°C and {hum:.1f}% relative humidity."
            advice = "Comfortable night attire recommended for a restful evening."
            comfort = 90

    elif wtype == "sunrise":
        label = "MORNING DAWN"
        headline = f"Fresh Sunrise Awakening ({time_str})"
        summary = f"At {time_str} ({time_context}), early morning light is emerging with ambient {temp:.1f}°C and {hum:.1f}% humidity."
        advice = "Light morning layers; ideal conditions for early morning walks or fresh air."
        comfort = 88

    elif wtype == "sunset":
        label = "GOLDEN DUSK"
        headline = f"Serene Sunset Twilight ({time_str})"
        summary = f"At {time_str} ({time_context}), golden hour twilight brings mild {temp:.1f}°C temperatures and {hum:.1f}% humidity."
        advice = "Casual evening attire with a light cardigan or windbreaker for cooling dusk air."
        comfort = 87

    elif wtype == "sunny":
        if temp >= 28.0:
            label = "WARM SUN"
            headline = f"Bright Solar Radiance ({time_str})"
            summary = f"At {time_str} ({time_context}), daylight conditions warm ambient levels to {temp:.1f}°C with {hum:.1f}% humidity."
            advice = "Breathable summer cottons, sunglasses, and UV skin protection recommended."
            comfort = 84
        else:
            label = "IT'S SUNNY"
            headline = f"Optimal Daylight Climate ({time_str})"
            summary = f"At {time_str} ({time_context}), clear radiant daylight with balanced {temp:.1f}°C and {hum:.1f}% humidity."
            advice = "Comfortable lightweight daytime attire and sunglasses for outdoor activities."
            comfort = 92

    elif wtype == "rain":
        label = "IT'S RAINING"
        headline = f"Active Precipitation at {time_str}"
        summary = f"At {time_str} ({time_context}), rainfall detected with elevated humidity at {hum:.1f}% and ambient {temp:.1f}°C."
        advice = "Waterproof jacket, umbrella, and non-slip footwear strongly recommended."
        comfort = 62

    elif wtype == "thunderstorm":
        label = "THUNDERSTORM"
        headline = f"Atmospheric Storm Alert ({time_str})"
        summary = f"At {time_str} ({time_context}), heavy precipitation with storm moisture ({hum:.1f}% humidity) and ambient {temp:.1f}°C."
        advice = "Seek indoor shelter; avoid open exposed areas during active precipitation."
        comfort = 40

    elif wtype == "snow":
        label = "IT'S SNOWING"
        headline = f"Winter Frost Conditions ({time_str})"
        summary = f"At {time_str} ({time_context}), freezing temperatures ({temp:.1f}°C) with winter snow/frost."
        advice = "Heavy winter coat, insulated gloves, and warm footwear essential."
        comfort = 55

    else:  # foggy
        label = "MISTY FOG"
        headline = f"Dense Mist & Fog ({time_str})"
        summary = f"At {time_str} ({time_context}), saturated air ({hum:.1f}% humidity) creating dense misty fog."
        advice = "Moisture-resistant outer layer and caution in low visibility."
        comfort = 70

    return {
        "weather_type": wtype,
        "vertical_label": label,
        "headline": headline,
        "summary": summary,
        "clothing_advice": advice,
        "comfort_index": comfort,
        "time_str": time_str,
        "time_context": time_context,
        "local_time": local_dt.strftime("%Y-%m-%d %H:%M:%S"),
        "analyzed_at": utc_now().isoformat() + "Z",
        "model": "heuristic_time_aware",
        "status": "fallback"
    }

class AIBackgroundScheduler:
    """
    Background worker that runs periodically to analyze new database telemetry.
    """
    def __init__(self, session_factory, weather_data_cls, ai_analysis_cls, interval_sec: int = 600):
        self.session_factory = session_factory
        self.weather_data_cls = weather_data_cls
        self.ai_analysis_cls = ai_analysis_cls
        self.interval_sec = interval_sec
        self._stop_event = threading.Event()
        self._thread = None

    def start(self):
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run_loop, daemon=True, name="AuraAIScheduler")
        self._thread.start()
        logger.info(f"AI scheduler daemon started (Interval: {self.interval_sec}s).")

    def stop(self):
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=3)
        logger.info("AI scheduler daemon stopped.")

    def _run_loop(self):
        # Run first analysis shortly after boot (5s delay for DB init)
        time.sleep(5)
        self.analyze_now()

        while not self._stop_event.is_set():
            # Sleep in small slices to respond promptly to shutdown
            for _ in range(self.interval_sec):
                if self._stop_event.is_set():
                    break
                time.sleep(1)

            if not self._stop_event.is_set():
                self.analyze_now()

    def analyze_now(self):
        """Fetches the latest reading from DB and triggers AI inference."""
        if not self.session_factory:
            return
        db = self.session_factory()
        try:
            latest = db.query(self.weather_data_cls).order_by(self.weather_data_cls.timestamp.desc()).first()
            if latest:
                reading_dict = latest.to_dict()
                analysis = perform_ai_analysis(reading_dict)
                
                # Persist AI inference to database
                try:
                    record = self.ai_analysis_cls(
                        weather_type=analysis.get('weather_type', 'sunny'),
                        vertical_label=analysis.get('vertical_label', "IT'S SUNNY"),
                        headline=analysis.get('headline', ''),
                        summary=analysis.get('summary', ''),
                        clothing_advice=analysis.get('clothing_advice', ''),
                        comfort_index=int(analysis.get('comfort_index', 85)),
                        model_used=analysis.get('model', OPENROUTER_MODEL),
                        time_context=analysis.get('time_context', ''),
                        local_time=analysis.get('local_time', ''),
                        timestamp=utc_now()
                    )
                    db.add(record)
                    db.commit()
                    logger.info("AI analysis persisted to database table 'ai_analysis'.")
                except Exception as dbe:
                    db.rollback()
                    logger.warning(f"Could not persist AI record to DB: {dbe}")
            else:
                logger.info("No physical telemetry in database yet. Awaiting sensor stream.")
        except Exception as e:
            logger.error(f"Error during scheduled AI cycle: {e}")
        finally:
            try:
                db.close()
            except Exception:
                pass



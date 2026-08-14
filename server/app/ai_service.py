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

def _build_ai_prompt(reading_dict: dict) -> str:
    """Constructs a structured prompt for the LLM based on physical sensor data."""
    temp_c = reading_dict.get('temperature', 22.0)
    temp_f = round(temp_c * 9/5 + 32, 1)
    humidity = reading_dict.get('humidity', 50.0)
    sun_activity = reading_dict.get('sun_activity', 50.0)
    pressure = reading_dict.get('pressure', 1013.25)
    wind_speed = reading_dict.get('wind_speed', 0.0)
    rain_detected = reading_dict.get('rain_detected', False)
    recorded_at = reading_dict.get('recorded_at', utc_now().strftime("%Y-%m-%d %H:%M:%S"))

    # Extract current local hour
    current_hour = utc_now().hour
    try:
        if ' ' in str(recorded_at):
            time_part = str(recorded_at).split(' ')[1]
            current_hour = int(time_part.split(':')[0])
    except Exception:
        pass

    time_context = (
        "Early Dawn / Sunrise" if 5 <= current_hour < 8 else
        "Morning" if 8 <= current_hour < 12 else
        "Afternoon High Sun" if 12 <= current_hour < 17 else
        "Golden Hour / Sunset" if 17 <= current_hour < 20 else
        "Night Twilight / Moonlit"
    )

    prompt = f"""You are an elite meteorological AI.
Analyze the following physical sensor telemetry from our smart home weather station:
- Temperature: {temp_c}°C ({temp_f}°F)
- Relative Humidity: {humidity}%
- Solar Irradiance / Light Intensity (LDR Photoresistor): {sun_activity}%
- Barometric Air Pressure: {pressure} hPa
- Wind Velocity: {wind_speed} km/h
- Rain Detection Sensor: {"RAIN DETECTED (Precipitation Active)" if rain_detected else "No Rain (Dry Surface)"}
- Timestamp / Time of Day: {recorded_at} ({time_context})

Task: Determine the exact atmospheric condition and return a STRICT JSON object only.
Valid 'weather_type' values MUST be one of:
- "sunny" (clear sky, high sunlight)
- "sunset" (golden hour dusk, low sun, warm sky)
- "nighttime" (night hours, low light, starry moonlit)
- "sunrise" (dawn, rising morning sun)
- "rain" (active rain or high humidity precipitation)
- "thunderstorm" (violent weather, pressure drop + rain)
- "snow" (sub-zero temperatures below 2°C with moisture)
- "foggy" (dense moisture, mist, low visibility)

JSON Output Schema:
{{
  "weather_type": "<one of the valid weather types above>",
  "vertical_label": "<2-3 words uppercase punchy editorial phrase, e.g. 'IT'S SUNNY', 'GOLDEN DUSK', 'TRANQUIL RAIN', 'MOONLIT NIGHT', 'CRISP WINTER'>",
  "headline": "<elegant 3-6 word weather headline>",
  "summary": "<1-2 concise sentences of high-end editorial meteorological description>",
  "clothing_advice": "<actionable clothing and outdoor comfort suggestion>",
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
    """Calculates weather condition type based on physical sensor rules."""
    is_rain = reading.get('rain_detected', False)
    temp = reading.get('temperature', 20.0)
    sun = reading.get('sun_activity', 50.0)
    hum = reading.get('humidity', 50.0)

    current_hour = utc_now().hour

    if is_rain:
        if temp > 18 and hum > 85:
            return "thunderstorm"
        return "rain"
    if temp <= 1.5 and hum > 70:
        return "snow"
    if hum > 88 and sun < 30:
        return "foggy"
    if sun < 15 or current_hour < 6 or current_hour >= 21:
        return "nighttime"
    if (18 <= current_hour < 21) or (20 <= sun < 50 and current_hour >= 16):
        return "sunset"
    if (5 <= current_hour < 8) and sun < 50:
        return "sunrise"
    return "sunny"

def _heuristic_fallback(reading: dict) -> dict:
    wtype = _heuristic_weather_type(reading)
    temp = reading.get('temperature', 22.0)
    labels = {
        "sunny": ("IT'S SUNNY", "Optimal Daylight Climate", "Comfortable lightweight clothing recommended.", 90),
        "sunset": ("GOLDEN DUSK", "Serene Sunset Glow", "Pleasant evening climate; a light cardigan is ideal.", 85),
        "nighttime": ("IT'S CLEAR", "Tranquil Night Atmosphere", "Cool night temperatures; jacket recommended if outdoors.", 80),
        "sunrise": ("MORNING DAWN", "Fresh Dawn Atmosphere", "Cool morning breeze; light layers suggested.", 85),
        "rain": ("IT'S RAINING", "Precipitation in Region", "Waterproof jacket or umbrella strongly recommended.", 60),
        "thunderstorm": ("THUNDERSTORM", "Atmospheric Storm Warning", "Seek indoor shelter; high moisture and active rain.", 40),
        "snow": ("IT'S SNOWING", "Winter Frost Conditions", "Heavy winter coat, gloves, and insulated footwear needed.", 55),
        "foggy": ("MISTY FOG", "Dense Valley Mist", "Reduced visibility; moisture-resistant outer layer recommended.", 70)
    }
    label, headline, advice, comfort = labels.get(wtype, ("IT'S SUNNY", "Optimal Climate", "Comfortable attire.", 85))

    return {
        "weather_type": wtype,
        "vertical_label": label,
        "headline": headline,
        "summary": f"Ambient temperature of {temp:.1f}°C with relative humidity at {reading.get('humidity', 50)}%.",
        "clothing_advice": advice,
        "comfort_index": comfort,
        "analyzed_at": utc_now().isoformat() + "Z",
        "model": "heuristic_fallback",
        "status": "fallback"
    }

class AIBackgroundScheduler:
    """
    Background worker that runs periodically to analyze new database telemetry.
    """
    def __init__(self, app, db, weather_data_cls, ai_analysis_cls, interval_sec: int = 600):
        self.app = app
        self.db = db
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
        try:
            with self.app.app_context():
                latest = self.weather_data_cls.query.order_by(self.weather_data_cls.timestamp.desc()).first()
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
                            comfort_index=analysis.get('comfort_index', 85),
                            model_used=analysis.get('model', OPENROUTER_MODEL),
                            timestamp=utc_now()
                        )
                        self.db.session.add(record)
                        self.db.session.commit()
                        logger.info("AI analysis persisted to database table 'ai_analysis'.")
                    except Exception as dbe:
                        self.db.session.rollback()
                        logger.warning(f"Could not persist AI record to DB: {dbe}")
                else:
                    logger.info("No physical telemetry in database yet. Awaiting sensor stream.")
        except Exception as e:
            logger.error(f"Error during scheduled AI cycle: {e}")


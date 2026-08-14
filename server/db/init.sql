CREATE DATABASE IF NOT EXISTS weather_db;
USE weather_db;

CREATE TABLE IF NOT EXISTS weather_data (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL DEFAULT 'WS-001',
    temperature FLOAT NOT NULL,
    wind_speed FLOAT NULL,
    humidity FLOAT NOT NULL,
    pressure FLOAT NULL,
    sun_activity FLOAT NOT NULL DEFAULT 0.0,
    batt_voltage FLOAT NULL,
    rain_detected BOOLEAN NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_weather_timestamp (timestamp),
    INDEX idx_weather_device (device_id)
);

CREATE TABLE IF NOT EXISTS ai_analysis (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    weather_type VARCHAR(32) NOT NULL DEFAULT 'sunny',
    vertical_label VARCHAR(64) NOT NULL DEFAULT 'IT\'S SUNNY',
    headline VARCHAR(128) NULL,
    summary TEXT NULL,
    clothing_advice TEXT NULL,
    comfort_index INTEGER NOT NULL DEFAULT 85,
    model_used VARCHAR(64) NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_timestamp (timestamp)
);

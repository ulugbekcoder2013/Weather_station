#!/usr/bin/env python3
"""
Smart Home Weather Station — Real-Time Production Server Entry Point
Seamless ASGI & WSGI compatibility wrapper exposing the high-performance FastAPI app.
"""

import os
import sys

# Ensure server/app directory is in python path
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

from main import app, init_db

if __name__ == '__main__':
    import uvicorn
    port = int(os.environ.get('PORT', 5000))
    debug_mode = os.environ.get('DEBUG', 'false').lower() in ('true', '1', 't')
    uvicorn.run(app, host='0.0.0.0', port=port, log_level="info")

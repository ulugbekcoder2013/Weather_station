FROM python:3.11-slim

WORKDIR /app

# Copy requirements
COPY server/app/requirements.txt .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application files
COPY server/app/ .

ENV PORT=80
ENV SQLALCHEMY_DATABASE_URI=sqlite:///weather_app.db
ENV ENVIRONMENT=production
ENV WEB_CONCURRENCY=2

EXPOSE 80 5000 10000

CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-80} --workers ${WEB_CONCURRENCY:-2} --ws auto"]

FROM mcr.microsoft.com/playwright/python:v1.44.0-jammy

# Prevent Python from writing .pyc files and enable unbuffered logging
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Install project dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Create default downloads directory and ensure proper ownership
RUN mkdir -p /downloads && chown -R pwuser:pwuser /app /downloads

# Copy application code
COPY --chown=pwuser:pwuser . .

# Switch to unprivileged user
USER pwuser

# Expose FastAPI server port
EXPOSE 8000

# Run uvicorn with live reload for local development
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]

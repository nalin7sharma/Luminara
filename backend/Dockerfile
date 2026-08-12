# Luminara backend — production image.
#
# Three things dominate the size and are handled deliberately:
#   * PyTorch: the CPU-only wheel (~200 MB) instead of the CUDA build (~5 GB).
#     There is no GPU on any free tier, so the CUDA build is pure weight.
#   * Whisper `base`: baked in at build time. Downloading 139 MB on the first
#     request would make a judge's first lecture look broken.
#   * Chromium: needed to render the study-pack PDF. Without it the endpoint
#     still serves readable HTML and says so, so this stays optional.
#
# Listens on $PORT (Hugging Face Spaces uses 7860, Cloud Run 8080, Render 10000).

FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=7860 \
    HOME=/app \
    XDG_CACHE_HOME=/app/.cache

# ffmpeg comes from the imageio-ffmpeg wheel, so only chromium and the codec
# libraries OpenCV needs are installed here.
RUN apt-get update && apt-get install -y --no-install-recommends \
        chromium \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender1 \
        libgl1 \
        fonts-dejavu-core \
        fonts-noto-core \
        fonts-noto-cjk \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Devanagari and Bengali shaping for the Hindi/Bangla study pack. Without these
# the PDF renders boxes instead of text.
RUN apt-get update && apt-get install -y --no-install-recommends \
        fonts-noto-devanagari \
        fonts-beng \
    && rm -rf /var/lib/apt/lists/* || true

WORKDIR /app

# CPU-only torch first so the heavy layer caches independently of app code.
COPY requirements-prod.txt .
RUN pip install --no-cache-dir torch==2.4.1 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements-prod.txt

# Bake the Whisper weights into the image.
RUN python -c "import whisper; whisper.load_model('base', download_root='/app/.cache/whisper')" \
    && ls -la /app/.cache/whisper

COPY app ./app
COPY scripts ./scripts

# The demo lecture must exist for judges even on a fresh container. The assets
# are committed, but regenerate the whiteboard if it is somehow missing.
RUN test -f app/demo/assets/whiteboard.png || python scripts/make_demo_assets.py || true

RUN mkdir -p /app/data /app/data/uploads /app/data/exports

ENV CHROME_BIN=/usr/bin/chromium \
    WHISPER_MODEL=base \
    WHISPER_DEVICE=cpu \
    HOST=0.0.0.0

EXPOSE 7860

# One worker on purpose: Whisper holds the model in memory and the pipeline
# runs on a background thread. A second worker would double the RAM for no gain.
CMD ["sh", "-c", "python -m uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-7860} --workers 1 --timeout-keep-alive 75"]

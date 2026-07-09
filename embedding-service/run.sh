#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ -f ".env" ]; then
  set -a
  source .env
  set +a
fi

if [ -n "${CUDA_VISIBLE_DEVICES:-}" ]; then
  export CUDA_VISIBLE_DEVICES
fi

HOST="${HOST:-0.0.0.0}"
PORT="${PORT:-8008}"
WORKERS="${WORKERS:-1}"

exec "${PYTHON_BIN:-python}" -m uvicorn app:app --host "$HOST" --port "$PORT" --workers "$WORKERS"

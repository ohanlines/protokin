#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="${ENV_FILE:-${APP_DIR}/.env.prod}"
IMAGE_NAME="${IMAGE_NAME:-protokin:prod}"
CONTAINER_NAME="${CONTAINER_NAME:-protokin}"
HOST_PORT="${HOST_PORT:-80}"
APP_PORT="${APP_PORT:-8080}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${HOST_PORT}}"

if [[ ! -d "${APP_DIR}" ]]; then
  echo "Missing app directory: ${APP_DIR}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

cd "${APP_DIR}"

echo "Building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

echo "Running migration"
"${APP_DIR}/scripts/migrate-local.sh"

echo "Restarting container ${CONTAINER_NAME}"
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  --env-file "${ENV_FILE}" \
  -e PORT="${APP_PORT}" \
  -p "${HOST_PORT}:${APP_PORT}" \
  "${IMAGE_NAME}" >/dev/null

echo "Running smoke checks"
for i in {1..5}; do
  if curl -fsS "${BASE_URL}/health" >/dev/null; then
    break
  fi
  sleep 5
done

curl -fsS "${BASE_URL}/health" >/dev/null
curl -fsS "${BASE_URL}/ready" >/dev/null

echo "Deployment completed."

#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="${ENV_FILE:-${APP_DIR}/.env.prod}"
IMAGE_NAME="${IMAGE_NAME:-protokin:prod}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required"
  exit 1
fi

if ! docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
  echo "Docker image ${IMAGE_NAME} not found. Build first."
  exit 1
fi

echo "Running migrations with image ${IMAGE_NAME}"
docker run --rm \
  --env-file "${ENV_FILE}" \
  "${IMAGE_NAME}" \
  clojure -M:dev -e '(do (require (quote protokin.system.migrations)) (protokin.system.migrations/migrate!))'

echo "Migration completed."


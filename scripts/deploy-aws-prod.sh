#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-}"
APP_DIR="${APP_DIR:-/opt/protokin}"
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

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Fetching latest git refs..."
  git fetch --tags --prune origin

  if [[ -n "${TAG}" ]]; then
    if [[ ! "${TAG}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "Tag must match semantic format: vMAJOR.MINOR.PATCH"
      exit 1
    fi

    if ! git rev-parse "refs/tags/${TAG}" >/dev/null 2>&1; then
      echo "Tag ${TAG} not found"
      exit 1
    fi

    echo "Checking out tag ${TAG}"
    git checkout "${TAG}"
  else
    echo "Checking out latest main"
    git checkout main
    git pull --ff-only origin main
  fi
else
  echo "No git repository in ${APP_DIR}, skipping source update."
fi

echo "Building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

echo "Running migration"
"${APP_DIR}/scripts/migrate-aws-prod.sh"

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
for i in {1..30}; do
  if curl -fsS "${BASE_URL}/health" >/dev/null; then
    break
  fi
  sleep 2
done

curl -fsS "${BASE_URL}/health" >/dev/null
curl -fsS "${BASE_URL}/ready" >/dev/null

echo "Deployment completed."


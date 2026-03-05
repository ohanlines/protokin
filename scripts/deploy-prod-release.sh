#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-}"
CONFIG_PATH="${CONFIG_PATH:-configs/config.prod.edn}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

if [[ -z "${TAG}" ]]; then
  echo "Usage: $0 <tag>"
  echo "Example: $0 v0.1.0"
  exit 1
fi

if [[ ! "${TAG}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Tag must match semantic format: vMAJOR.MINOR.PATCH"
  exit 1
fi

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  if [[ "${CURRENT_BRANCH}" != "main" ]]; then
    echo "Deploy is only allowed from main branch. Current: ${CURRENT_BRANCH}"
    exit 1
  fi

  if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Working tree is dirty. Commit or stash before release deploy."
    exit 1
  fi

  if ! git rev-parse "refs/tags/${TAG}" >/dev/null 2>&1; then
    echo "Tag ${TAG} does not exist locally. Create tag first."
    exit 1
  fi

  echo "Checking out tag ${TAG}"
  git checkout "${TAG}"
else
  echo "Git repository not detected. Skipping branch/tag enforcement checks."
fi

echo "Running production migration with CONFIG_PATH=${CONFIG_PATH}"
CONFIG_PATH="${CONFIG_PATH}" ./scripts/migrate-prod.sh

echo "Restarting production service"
if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl restart protokin-prod
  sudo systemctl status --no-pager protokin-prod || true
else
  echo "systemctl not found. Start service manually with ./scripts/run-prod.sh"
fi

echo "Running smoke checks"
./scripts/smoke-check.sh "${BASE_URL}"

echo "Release deploy finished for ${TAG}"

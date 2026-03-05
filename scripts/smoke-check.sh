#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

echo "Smoke check against ${BASE_URL}"
echo "GET /health"
curl -fsS "${BASE_URL}/health" && echo

echo "GET /ready"
curl -fsS "${BASE_URL}/ready" && echo

echo "GET /metrics"
curl -fsS "${BASE_URL}/metrics" && echo

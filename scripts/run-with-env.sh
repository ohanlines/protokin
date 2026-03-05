#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.local}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.local.example to ${ENV_FILE} and fill values."
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

required_vars=(
  TELEGRAM_BOT_TOKEN
  DB_HOST
  DB_PORT
  DB_NAME
  DB_USER
  DB_PASSWORD
  ADMIN_TOKEN
)

for key in "${required_vars[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "Missing required env var: ${key}"
    exit 1
  fi
done

echo "Running bot using env vars from ${ENV_FILE}"
clj -M -m protokin.core


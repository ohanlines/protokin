#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="${ENV_FILE:-${APP_DIR}/.env.prod}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

cd "${APP_DIR}"

echo "Running migrations with env file ${ENV_FILE}"

# Load env file without shell expansion (safe for passwords with special chars)
while IFS= read -r line || [[ -n "${line}" ]]; do
  [[ -z "${line}" || "${line}" == \#* ]] && continue
  declare -x -- "${line}"
done < "${ENV_FILE}"

clj -M:dev -e "(require 'protokin.system.migrations) (protokin.system.migrations/migrate!)"

echo "Migration completed."

#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${CONFIG_PATH:-configs/config.prod.edn}"

echo "Running prod migrations with CONFIG_PATH=${CONFIG_PATH}"
CONFIG_PATH="${CONFIG_PATH}" clj -M:dev -e "(require 'protokin.system.migrations) (protokin.system.migrations/migrate!)"

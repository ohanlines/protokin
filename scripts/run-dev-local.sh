#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${CONFIG_PATH:-configs/config.dev.edn}"

echo "Running local dev bot with CONFIG_PATH=${CONFIG_PATH}"
CONFIG_PATH="${CONFIG_PATH}" clj -M -m protokin.core

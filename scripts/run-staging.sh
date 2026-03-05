#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${CONFIG_PATH:-configs/config.staging.edn}"

echo "Running staging bot with CONFIG_PATH=${CONFIG_PATH}"
CONFIG_PATH="${CONFIG_PATH}" clj -M -m protokin.core

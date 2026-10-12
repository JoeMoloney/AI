#!/bin/bash
set -euo pipefail

if ! docker network inspect selfhost_network >/dev/null 2>&1; then
  docker network create --driver bridge selfhost_network
fi

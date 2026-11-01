#!/bin/sh
set -eu

mode="${1:-initial}"
case "$mode" in
  initial|persist) ;;
  *) echo "Usage: sh scripts/offline-smoke.sh [initial|persist]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/.."

# Reuse the already-built local frontend image: it contains the pinned Node runtime.
# The application stack must already be running. Root can write the smoke-state volume.
docker compose run --rm --no-deps --pull never --user 0:0 \
  -e "SMOKE_MODE=$mode" \
  -v "$PWD/scripts/container-smoke.mjs:/work/container-smoke.mjs:ro" \
  -v "$PWD/demo-inputs:/samples:ro" \
  -v "xiaoying-material-studio_smoke-state:/state" \
  --entrypoint node frontend /work/container-smoke.mjs

#!/usr/bin/env bash
# Launch a Minecraft dev client with a Krios mod loaded.
# Usage: run-client.sh [neoforge|fabric] [mod]   (defaults: neoforge, industry)
set -euo pipefail

LOADER="${1:-neoforge}"
MOD="${2:-industry}"

case "$LOADER" in
    neoforge|fabric) ;;
    *) echo "Usage: run-client.sh [neoforge|fabric] [mod]"; exit 1 ;;
esac

DIR="$(cd "$(dirname "$0")/.." && pwd)/mods/$MOD"
if [ ! -f "$DIR/gradlew" ]; then
    echo "Unknown mod '$MOD': no gradlew in $DIR"
    exit 1
fi

cd "$DIR"
exec ./gradlew "$LOADER:runClient"

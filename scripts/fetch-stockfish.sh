#!/usr/bin/env bash
#
# Fetches the official Stockfish 18 Android ARMv8 dot-product binary
# and places it at app/src/main/jniLibs/arm64-v8a/libstockfish.so so
# that ./gradlew assembleDebug can package it into the APK.
#
# The binary is excluded from git (>100 MB GitHub limit). See
# app/src/main/jniLibs/README.md for source/version details and
# AGENT_NOTES.md §"Stockfish packaging decision" for the why.
#
# Idempotent: if the target already exists with the right size,
# this is a no-op.

set -euo pipefail

# Resolve repo root from the script's own location so this works
# regardless of where it's invoked from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DEST_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
DEST="$DEST_DIR/libstockfish.so"

URL="https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8-dotprod.tar"
TAR_MEMBER="stockfish/stockfish-android-armv8-dotprod"
EXPECTED_SIZE_BYTES=114115752  # observed 2026-05-15 from sf_18 release

if [[ -f "$DEST" ]]; then
    actual_size=$(wc -c < "$DEST" | tr -d ' ')
    if [[ "$actual_size" == "$EXPECTED_SIZE_BYTES" ]]; then
        echo "libstockfish.so already present at expected size ($actual_size bytes); nothing to do."
        exit 0
    fi
    echo "libstockfish.so present but size mismatch (have $actual_size, want $EXPECTED_SIZE_BYTES). Replacing."
fi

mkdir -p "$DEST_DIR"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Downloading Stockfish sf_18 ARMv8 dot-product (~111 MB)..."
curl -L -f -# -o "$TMP_DIR/sf.tar" "$URL"

echo "Extracting binary..."
tar -xf "$TMP_DIR/sf.tar" -C "$TMP_DIR" "$TAR_MEMBER"

echo "Verifying ELF magic..."
magic=$(head -c 4 "$TMP_DIR/$TAR_MEMBER" | xxd -p)
if [[ "$magic" != "7f454c46" ]]; then
    echo "ERROR: extracted file is not an ELF binary (magic=$magic). Aborting." >&2
    exit 1
fi

mv "$TMP_DIR/$TAR_MEMBER" "$DEST"
chmod 0644 "$DEST"

actual_size=$(wc -c < "$DEST" | tr -d ' ')
echo "Placed $DEST ($actual_size bytes)."
if [[ "$actual_size" != "$EXPECTED_SIZE_BYTES" ]]; then
    echo "WARNING: size differs from expected ($EXPECTED_SIZE_BYTES). The upstream release may have changed; update EXPECTED_SIZE_BYTES in this script and the table in app/src/main/jniLibs/README.md." >&2
fi

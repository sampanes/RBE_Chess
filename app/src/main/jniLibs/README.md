# jniLibs — Stockfish binary

This directory ships the Stockfish chess engine as a native binary that
Android extracts into the app's read-only-but-executable
`nativeLibraryDir`, so the app can `Runtime.exec()` it and talk UCI
over stdin/stdout. See `AGENT_NOTES.md` §"Stockfish packaging decision"
for why this pattern over JNI for M1.

> **The actual `.so` file is not in git.** It is 109 MB and exceeds
> GitHub's 100 MB hard limit. Run `scripts/fetch-stockfish.sh` from
> the repo root once after cloning (or any time the table below
> changes); `./gradlew assembleDebug` will fail until that script has
> placed `arm64-v8a/libstockfish.so`. The script is idempotent and
> verifies the ELF magic before placing the file.

## Current binary

| Field | Value |
|---|---|
| Engine | Stockfish |
| Release tag | `sf_18` |
| Build flavor | Android ARMv8 Dot Product |
| Tarball | `stockfish-android-armv8-dotprod.tar` |
| File on disk | `arm64-v8a/libstockfish.so` |
| Size | ~109 MB (large NNUE network is embedded) |
| ELF magic verified | yes (`7f 45 4c 46 02 01 01`) |
| Date placed | 2026-05-15 |

## Source

Primary build (current):
<https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8-dotprod.tar>

Fallback build (plain ARMv8, use if dot-product fails on a target device):
<https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8.tar>

Official pages:
- <https://stockfishchess.org/download/>
- <https://github.com/official-stockfish/Stockfish/releases/tag/sf_18>

## License

Stockfish is GPL v3 (see `Copying.txt` inside the upstream tarball).
We ship the upstream binary unmodified and invoke it as a separate
OS process — the standard "exec the engine" pattern used by every
mainstream chess GUI. We do **not** statically or dynamically link
against Stockfish source. If we ever switch to JNI / static linking
(see AGENT_NOTES "Falls back cleanly to JNI later" note), the
licensing implications need to be re-examined first.

## Why the file is named `libstockfish.so`

Android's package manager only extracts files matching `lib*.so` from
an APK's `lib/<abi>/` directory into the app's `nativeLibraryDir`.
Anywhere else in the APK, the file lives compressed inside the APK
and is not directly exec-able. The `.so` extension is a packaging
trick — the file is a regular ELF executable, not a shared library.

For the same reason, `build.gradle.kts` sets
`android.packaging.jniLibs.useLegacyPackaging = true`. This is the
opposite of the modern default and intentionally so — the modern
default (`false`) stores .so uncompressed *inside* the APK and the
linker mmap's it from there without ever writing a real file to
disk. `Runtime.exec()` then fails with ENOENT. With `true` AGP
injects `android:extractNativeLibs="true"` into the merged manifest
and Android extracts the binary into `nativeLibraryDir` as a real
file at install time. See AGENT_NOTES §"Stockfish packaging
decision" for the full background and verification command.

## How to update

1. Edit `scripts/fetch-stockfish.sh`: bump `URL`, `TAR_MEMBER`, and
   `EXPECTED_SIZE_BYTES` to match the new release.
2. Delete the local `arm64-v8a/libstockfish.so` and re-run the script.
   It will re-download, verify the ELF magic, and place the new file.
3. Update the table at the top of this README (tag, size, date).
4. Re-run the on-device UCI handshake test. If `bestmove` round-trips
   for a startpos query, the upgrade is good.

## Why no other ABIs

We target the Samsung S22 Ultra (arm64-v8a). Adding `armeabi-v7a` /
`x86_64` would roughly triple the APK size for no current value.
Revisit if the device list ever expands.

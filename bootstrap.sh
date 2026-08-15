#!/usr/bin/env bash
set -euo pipefail

MINDUSTRY_SHA="e8bf80a1d2d5e9cf339c2fbc2a82444bf6d779d7"
ARC_SHA="55553d17bef8bb32362c8038999a19427f508ef0"
ARC_GWT_BASE="2303ab81bb76a973db8885f3ba14b6515782a1a4"

KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${1:-$KIT_DIR/work}"

need(){ command -v "$1" >/dev/null 2>&1 || { echo "error: required command '$1' was not found" >&2; exit 2; }; }
need git; need python3; need tar; need java

if [[ -e "$WORK" ]] && [[ -n "$(find "$WORK" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  echo "error: destination is not empty: $WORK" >&2
  echo "Use a new directory so this script cannot damage an existing checkout." >&2
  exit 3
fi
mkdir -p "$WORK"

echo "[1/10] Cloning exact Mindustry revision..."
git clone --filter=blob:none https://github.com/Anuken/Mindustry.git "$WORK/Mindustry"
git -C "$WORK/Mindustry" checkout --detach "$MINDUSTRY_SHA"
git -C "$WORK/Mindustry" switch -c web-port

echo "[2/10] Cloning exact sibling Arc revision (Mindustry's own localArc mode will use it)..."
git clone --filter=blob:none https://github.com/Anuken/Arc.git "$WORK/Arc"
git -C "$WORK/Arc" checkout --detach "$ARC_SHA"
git -C "$WORK/Arc" switch -c web-port-base

echo "[3/10] Applying browser-safe current Arc patch..."
python3 "$KIT_DIR/scripts/patch_arc.py" "$WORK/Arc"

echo "[4/10] Recovering Anuken's last official Arc GWT backend as browser reference code..."
TMP="$WORK/.legacy-gwt"; mkdir -p "$TMP"
git -C "$WORK/Arc" archive "$ARC_GWT_BASE" backends/backend-gwt | tar -x -C "$TMP"

echo "[5/10] Installing TeaVM primary target + GWT fallback/reference target..."
python3 "$KIT_DIR/scripts/install_web_module.py" "$WORK/Mindustry" "$KIT_DIR/web" "$KIT_DIR/web-teavm"

echo "[6/10] Importing/normalizing the historical official Arc browser backend..."
python3 "$KIT_DIR/scripts/import_legacy_backend.py" "$TMP/backends/backend-gwt/src" "$WORK/Mindustry/web/src"

echo "[7/10] Generating TeaVM WebGL bridge from Anuken's official GWT GL20 implementation..."
python3 "$KIT_DIR/scripts/generate_teavm_gl.py" \
  "$WORK/Mindustry/web/src/arc/backend/gwt/GwtGL20.java" \
  "$WORK/Mindustry/web-teavm/src/main/java/mindustry/web/teavm/TeaVMGL20.java"
python3 "$KIT_DIR/scripts/check_gl_surface.py" \
  "$WORK/Arc/arc-core/src/arc/graphics/GL20.java" \
  "$WORK/Mindustry/web-teavm/src/main/java/mindustry/web/teavm/TeaVMGL20.java"

echo "[8/10] Applying deterministic current-Mindustry browser compatibility patch..."
python3 "$KIT_DIR/scripts/patch_mindustry.py" "$WORK/Mindustry"

echo "[9/10] Auditing source/runtime boundaries..."
python3 "$KIT_DIR/scripts/audit.py" "$WORK/Mindustry" "$WORK/Arc" --write-report "$WORK/Mindustry/WEB_PORT_REPORT.md"
rm -rf "$TMP"

echo "[10/10] Running the primary real compile gate (TeaVM Java -> browser JavaScript)..."
(
  cd "$WORK/Mindustry"
  ./gradlew web-teavm:distWeb --stacktrace
)

echo
echo "TeaVM browser distribution created at:"
echo "  $WORK/Mindustry/web-teavm/build/dist"
echo "Audit:"
echo "  $WORK/Mindustry/WEB_PORT_REPORT.md"
echo
echo "Serve the dist directory over HTTP; opening index.html directly via file:// is not a supported test."

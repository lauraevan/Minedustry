#!/usr/bin/env bash
# Build Anuken's jitpack-hosted dependencies from source into the local Maven repo.
#
# Why this exists: Mindustry pulls a few artifacts (notably com.github.Anuken:rhino)
# from jitpack.io. In sandboxed/CI environments where jitpack.io or the Sonatype
# snapshot repo are blocked by an egress policy (HTTP 403), Gradle cannot resolve
# them and :core:compileJava fails before any code is compiled. github.com is
# usually still reachable, so we can clone the exact pinned source and publish it to
# ~/.m2 (which build.gradle already lists first via mavenLocal()).
#
# In a normal network with jitpack access you do NOT need this; the standard
# bootstrap resolves these dependencies directly.
#
# Usage: scripts/build_local_deps.sh <work-dir>
set -euo pipefail

WORK="${1:?usage: build_local_deps.sh <work-dir>}"
mkdir -p "$WORK"

# Pin must match rhinoVersion in the pinned Mindustry build.gradle.
RHINO_SHA="32395f942976a6d1d21ec9877664554cd5762306"

if [[ ! -d "$WORK/rhino" ]]; then
  echo "[local-deps] cloning Anuken/rhino..."
  git clone --filter=blob:none https://github.com/Anuken/rhino.git "$WORK/rhino"
fi
git -C "$WORK/rhino" checkout --detach "$RHINO_SHA"

echo "[local-deps] publishing com.github.Anuken:rhino:$RHINO_SHA to mavenLocal..."
( cd "$WORK/rhino" && ./gradlew --no-daemon publishToMavenLocal -Pversion="$RHINO_SHA" )

echo "[local-deps] done. Gradle will now resolve rhino from ~/.m2 before hitting jitpack."

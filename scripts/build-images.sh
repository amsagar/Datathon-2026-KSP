#!/usr/bin/env bash
set -euo pipefail

# Build linux/amd64 OCI images for both apps, ready for Catalyst AppSail.
# Catalyst ONLY accepts amd64 images — on Apple Silicon this cross-builds via buildx/QEMU.
#
# Usage:
#   ./scripts/build-images.sh            # builds :latest
#   TAG=v1 ./scripts/build-images.sh     # builds :v1
#
# After building, deploy with the Catalyst CLI (see DEPLOY.md).

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="${TAG:-latest}"
PLATFORM="linux/amd64"

echo "==> Building backend image (ksp-agent-be:${TAG}) for ${PLATFORM}"
docker build --platform "${PLATFORM}" -t "ksp-agent-be:${TAG}" "${ROOT}/service"

echo "==> Building UI image (ksp-agent-ui:${TAG}) for ${PLATFORM}"
docker build --platform "${PLATFORM}" -t "ksp-agent-ui:${TAG}" "${ROOT}/client"

echo
echo "Built (linux/amd64):"
echo "  ksp-agent-be:${TAG}"
echo "  ksp-agent-ui:${TAG}"
echo
echo "Next: deploy with the Catalyst CLI — see DEPLOY.md"

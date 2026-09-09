#!/bin/sh
# Gate zero-MC-import (Q2 strict) : aucun import Minecraft/Forge hors bridge.
# Vert = rien trouvé. Exécuté par CI (.github/workflows/check.yml).
set -eu
cd "$(dirname "$0")/.."
hits=$(rg -n --no-heading "net\.minecraft|cpw\.mods\.|net\.minecraftforge" \
  --glob '!tools/**' --glob '!.git/**' --glob '!*.md' --glob '!java/build/**' . || true)
if [ -n "$hits" ]; then
  echo "FAIL zero-mc-import :"
  echo "$hits"
  exit 1
fi
echo "ok (zero-mc-import)"
python3 "$(dirname "$0")/check_goldens.py"

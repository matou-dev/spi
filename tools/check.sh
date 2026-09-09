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
# M1 skeleton gate : self-test Java 8, zero dep (IDs, Job seam, RNG adressé).
mkdir -p java/build
javac --release 8 -d java/build $(find java/src -name '*.java')
javac --release 8 -cp java/build -d java/build $(find java/test -name '*.java')
java -cp java/build fr.iamacat.spi.SkeletonCheck

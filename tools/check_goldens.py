#!/usr/bin/env python3
"""Gate goldens : chaque .matou a son .expected.json (arbre exact) ou
.expected.txt (CODE:LINE). Le parser est fail-fast : première erreur gagne.
"""
import glob
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PARSER = os.path.join(ROOT, "parser", "matou_parse.py")


def main():
    fails = []
    paths = sorted(glob.glob(os.path.join(ROOT, "goldens", "*.matou")))
    if not paths:
        print("FAIL goldens : aucun .matou")
        return 1
    for path in paths:
        name = os.path.basename(path)
        base = path[:-len(".matou")]
        jexp, texp = base + ".expected.json", base + ".expected.txt"
        r = subprocess.run([sys.executable, PARSER, path],
                           capture_output=True, text=True)
        if os.path.exists(jexp):
            with open(jexp, encoding="utf-8") as fh:
                want = fh.read().strip()
            good = r.returncode == 0 and r.stdout.strip() == want
            detail = ""
        elif os.path.exists(texp):
            with open(texp, encoding="utf-8") as fh:
                want = fh.read().strip()
            got = ":".join(r.stdout.strip().split(":")[:2])
            good = r.returncode != 0 and got == want
            detail = " (got %s, want %s)" % (got, want)
        else:
            good, detail = False, " (no expected file)"
        print(("ok golden : " if good else "FAIL golden : ") + name + detail)
        if not good:
            fails.append(path)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())

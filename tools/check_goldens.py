#!/usr/bin/env python3
"""Gate goldens : chaque .matou a son .expected.json (arbre, comparaison
structurelle) ou .expected.txt (CODE:LINE). Les goldens sont l'oracle partagé :
le parser Python de référence ET le port Java doivent les passer tous les deux
(fail-fast : première erreur gagne).
"""
import glob
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PY_PARSER = os.path.join(ROOT, "parser", "matou_parse.py")
JAVA_SRC = os.path.join(ROOT, "java", "src")
JAVA_BUILD = os.path.join(ROOT, "java", "build")
JAVA_MAIN = "fr.iamacat.spi.MatouParse"


def compile_java():
    srcs = []
    for dirpath, _, files in os.walk(JAVA_SRC):
        for f in files:
            if f.endswith(".java"):
                srcs.append(os.path.join(dirpath, f))
    if not srcs:
        return "no java sources"
    os.makedirs(JAVA_BUILD, exist_ok=True)
    r = subprocess.run(["javac", "--release", "8", "-d", JAVA_BUILD] + srcs,
                       capture_output=True, text=True)
    if r.returncode != 0:
        return r.stderr.strip() or "javac failed"
    return None


def run(cmd, path):
    r = subprocess.run(cmd + [path], capture_output=True, text=True)
    return r.returncode, r.stdout.strip()


def check_json(want_text, runners, path):
    want = json.loads(want_text)
    bad = []
    for name, cmd in runners:
        code, out = run(cmd, path)
        if code != 0:
            bad.append("%s exit=%d (%s)" % (name, code, out.split(":")[:2]))
            continue
        try:
            if json.loads(out) != want:
                bad.append("%s tree differs" % name)
        except ValueError as e:
            bad.append("%s bad json (%s)" % (name, e))
    return bad


def check_txt(want, runners, path):
    bad = []
    for name, cmd in runners:
        code, out = run(cmd, path)
        got = ":".join(out.split(":")[:2])
        if code == 0 or got != want:
            bad.append("%s got %s" % (name, got))
    return bad


def main():
    err = compile_java()
    if err is not None:
        print("FAIL goldens : java compile (%s)" % err)
        return 1
    runners = [("py", [sys.executable, PY_PARSER]),
               ("java", ["java", "-cp", JAVA_BUILD, JAVA_MAIN])]
    paths = sorted(glob.glob(os.path.join(ROOT, "goldens", "*.matou")))
    if not paths:
        print("FAIL goldens : aucun .matou")
        return 1
    fails = 0
    for path in paths:
        name = os.path.basename(path)
        base = path[:-len(".matou")]
        jexp, texp = base + ".expected.json", base + ".expected.txt"
        if os.path.exists(jexp):
            with open(jexp, encoding="utf-8") as fh:
                bad = check_json(fh.read(), runners, path)
        elif os.path.exists(texp):
            with open(texp, encoding="utf-8") as fh:
                bad = check_txt(fh.read().strip(), runners, path)
        else:
            bad = ["no expected file"]
        if bad:
            print("FAIL golden : %s (%s)" % (name, "; ".join(bad)))
            fails += 1
        else:
            print("ok golden : %s (py+java)" % name)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())

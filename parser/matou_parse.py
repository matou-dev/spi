#!/usr/bin/env python3
"""Reference parser for SYNTAX-V1 (spec: spec/SYNTAX-V1.md).

Single tokenizer + single recursive-descent-free state machine (line-oriented).
Fail-fast: first error wins, always `CODE:LINE`. No Minecraft imports.
"""
import json
import re
import sys

GENRES_V1 = ("block", "item", "mob", "feature")
GENRE_DECL = {g: g.capitalize() for g in GENRES_V1}  # block -> Block
RESERVED = {"syntax", "namespace", "from", "use", "genre", "field",
            "true", "false"}
TYPES = ("f32", "u32", "bool", "string")


class Err(Exception):
    def __init__(self, code, line, msg=""):
        self.code, self.line, self.msg = code, line, msg

    def __str__(self):
        s = "%s:%d" % (self.code, self.line)
        return s + (": %s" % self.msg if self.msg else "")


def strip_comment(line):
    out, quoted = [], False
    for ch in line:
        if ch == '"':
            quoted = not quoted
        if ch == "#" and not quoted:
            break
        out.append(ch)
    return "".join(out).rstrip()


def parse_file(path):
    with open(path, encoding="utf-8") as fh:
        raw = fh.read().splitlines()
    lines = [(i + 1, strip_comment(l)) for i, l in enumerate(raw)]
    lines = [(n, l) for n, l in lines if l.strip() != ""]
    if not lines:
        raise Err("E_MATOU_VERSION", 1, "empty file")
    pos = 0

    def cur():
        return lines[pos] if pos < len(lines) else (raw and len(raw) or 1, "")

    # --- syntax header: first significant line, exact ---
    n, l = lines[0]
    if not re.fullmatch(r"syntax 1", l):
        raise Err("E_MATOU_VERSION", n, l)
    pos = 1

    namespace, imports = None, set()
    genres, instances = {}, []  # genres: Decl -> {field: type}
    open_genre = None

    def close_instance_check(inst, end_line):
        seen = {fname for (_, fname, _) in inst["raw"]}
        missing = [f for f in genres[inst["decl"]].keys() if f not in seen]
        if missing:
            raise Err("E_MATOU_FIELD", end_line, "missing %s" % missing[0])

    pending = None  # instance shell being filled
    while pos < len(lines):
        n, l = lines[pos]
        if l.startswith(" ") or l.startswith("\t"):
            m = re.fullmatch(r"  ([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)", l)
            if m is None:
                raise Err("E_MATOU_INDENT", n, l)
            if pending is None:
                raise Err("E_MATOU_INDENT", n, l)
            fname, fraw = m.group(1), m.group(2).strip()
            gfields = genres[pending["decl"]]
            if fname not in gfields:
                raise Err("E_MATOU_FIELD", n, fname)
            if fname in pending["fields"]:
                raise Err("E_MATOU_FIELD", n, "duplicate " + fname)
            pending["raw"].append((n, fname, fraw))
            pos += 1
            continue
        if pending is not None:
            close_instance_check(pending, n)
            instances.append(pending)
            pending = None
        if l.startswith("namespace "):
            m = re.fullmatch(r"namespace ([A-Za-z_][A-Za-z0-9_.]*)", l)
            if m is None:
                raise Err("E_MATOU_HEADER", n, l)
            if namespace is not None:
                raise Err("E_MATOU_HEADER", n, "duplicate namespace")
            namespace = m.group(1)
            open_genre = None
        elif l.startswith("from "):
            m = re.fullmatch(r"from ([A-Za-z_][A-Za-z0-9_.]*) use (.+)", l)
            if m is None:
                raise Err("E_MATOU_HEADER", n, l)
            imports.add(m.group(1))
            open_genre = None
        elif l.startswith("genre "):
            m = re.fullmatch(r"genre ([A-Za-z_][A-Za-z0-9_]*) : Data", l)
            if m is None:
                raise Err("E_MATOU_GENRE", n, l)
            decl = m.group(1)
            if decl not in GENRE_DECL.values():
                raise Err("E_MATOU_GENRE", n, decl)
            if decl in genres:
                raise Err("E_MATOU_GENRE", n, "duplicate " + decl)
            genres[decl] = {}
            open_genre = decl
        elif l.startswith("field "):
            m = re.fullmatch(r"field ([A-Za-z_][A-Za-z0-9_]*) : (\S+)", l)
            if m is None or open_genre is None:
                raise Err("E_MATOU_FIELD", n, l)
            fname, ftyp = m.group(1), m.group(2)
            if fname in RESERVED:
                raise Err("E_MATOU_RESERVED", n, fname)
            if not (ftyp in TYPES or (ftyp.endswith("_ref")
                    and ftyp[:-4] in GENRES_V1)):
                raise Err("E_MATOU_TYPE", n, ftyp)
            if fname in genres[open_genre]:
                raise Err("E_MATOU_FIELD", n, "duplicate " + fname)
            genres[open_genre][fname] = ftyp
        else:
            m = re.fullmatch(r"([A-Za-z_][A-Za-z0-9_]*) ([A-Za-z_][A-Za-z0-9_]*)", l)
            if m is None:
                raise Err("E_MATOU_GENRE", n, l)
            gword, iname = m.group(1), m.group(2)
            if gword in RESERVED:
                raise Err("E_MATOU_HEADER", n, l)
            decl = next((d for d, low in
                         ((d, d.lower()) for d in GENRE_DECL.values())
                         if low == gword), None)
            if decl is None or decl not in genres:
                raise Err("E_MATOU_GENRE", n, gword)
            if namespace is None:
                raise Err("E_MATOU_HEADER", n, "instance before namespace")
            if iname in RESERVED:
                raise Err("E_MATOU_RESERVED", n, iname)
            if any(i["decl"] == decl and i["name"] == iname
                   for i in instances):
                raise Err("E_MATOU_FIELD", n, "duplicate " + iname)
            pending = {"decl": decl, "name": iname, "fields": {}, "raw": []}
            open_genre = None
        pos += 1
    if pending is not None:
        close_instance_check(pending, lines[-1][0])
        instances.append(pending)

    if namespace is None:
        raise Err("E_MATOU_HEADER", lines[-1][0], "missing namespace")

    # --- pass 2: values + refs (forward refs allowed) ---
    known = {(namespace, i["decl"].lower(), i["name"]) for i in instances}
    out_instances = []
    for inst in instances:
        fields = {}
        for (ln, fname, fraw) in inst["raw"]:
            ftyp = genres[inst["decl"]][fname]
            fields[fname] = parse_value(fraw, ftyp, ln, namespace,
                                        imports, known)
        inst["fields"] = fields
        del inst["raw"]
        out_instances.append(inst)
    return {"syntax": 1, "namespace": namespace,
            "imports": sorted(imports),
            "genres": genres, "instances": out_instances}


def parse_value(raw, typ, line, local_ns, imports, known):
    if typ == "f32":
        try:
            return float(raw)
        except ValueError:
            raise Err("E_MATOU_TYPE", line, raw)
    if typ == "u32":
        if re.fullmatch(r"\d+", raw):
            return int(raw)
        raise Err("E_MATOU_TYPE", line, raw)
    if typ == "bool":
        if raw in ("true", "false"):
            return raw == "true"
        raise Err("E_MATOU_TYPE", line, raw)
    if typ == "string":
        m = re.fullmatch(r'"(.*)"', raw)
        if m is None:
            raise Err("E_MATOU_TYPE", line, raw)
        return m.group(1)
    # xxx_ref
    m = re.fullmatch(r"([A-Za-z_][A-Za-z0-9_.]*):([A-Za-z_][A-Za-z0-9_]*)",
                     raw)
    if m is None:
        raise Err("E_MATOU_TYPE", line, raw)
    ns, name = m.group(1), m.group(2)
    if ns != local_ns and ns not in imports:
        raise Err("E_MATOU_UNKNOWN_REF", line, raw)
    want_genre = typ[:-4]
    if ns == local_ns and (ns, want_genre, name) not in known:
        raise Err("E_MATOU_UNKNOWN_REF", line, raw)
    return raw


def main(path):
    try:
        tree = parse_file(path)
    except Err as e:
        print(str(e))
        return 1
    print(json.dumps(tree, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))

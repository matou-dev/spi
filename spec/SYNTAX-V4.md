# SYNTAX-V4 — vein genre delta over V3

**Status: FROZEN after gate.** `SYNTAX-V1.md`, `SYNTAX-V2.md` and
`SYNTAX-V3.md` are untouched; this file is the complete delta.
Rationale: after point placements (`feature`) and multi-block volumes
(`structure`), the next worldgen primitive is clustered ore: a seeded
cluster of one registered block, decided purely and placed by the
bridge. It is the second consumer of the V2 foundation types (`vec3`,
`i32`) and the first consumer of the registration path
(`decisions/REGISTRATION.md` in the hub).

## 1. Unchanged from V3

File layout, comments, headers, indentation, types, reserved words, fail-fast
`CODE:LINE`, forward references — all per V1/V2/V3. No new error codes.

## 2. Header

First significant line: `syntax 4`. The reference parser accepts `syntax 1`,
`2`, `3` and `4` and reports the version in the tree
(`"syntax": 1 | 2 | 3 | 4`). Anything else is `E_MATOU_VERSION` at line 1.

## 3. New genre (v4-only): vein

A vein is decided purely and placed by the bridge as one block kind in
seeded clusters. Fields, all required:

- `block : block_ref` — the block the job may place (a content ref; the
  operator maps it to a landable block, same split as `structure`
  palettes).
- `count : u32` — clusters to decide per tick (mirrors `feature.count`).
- `size : vec3` — cluster extents in cells. Syntactically any integers;
  non-positive extents are refused by the deciding job with a named
  error, never defaulted (the parser stays syntactic — same split as
  `structure.size`, which the `StructurePlaceJob` refuses at decide
  time, not at parse time).
- `seed : i32` — deterministic salt for the cluster stream (a V2 `i32`,
  signed, any value). Zero new types.

Version gating (existing code paths, no new codes):

- `genre Vein` or a `vein` instance in a `syntax 1|2|3` file is
  `E_MATOU_GENRE` at that line.
- A `vein_ref` field type in a `syntax 1|2|3` file is `E_MATOU_TYPE` at
  the field line.

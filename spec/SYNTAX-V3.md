# SYNTAX-V3 — structure genre delta over V2

**Status: FROZEN after gate.** `SYNTAX-V1.md` and `SYNTAX-V2.md` are untouched;
this file is the complete delta. Rationale: after point placements
(`feature`), the next worldgen primitive is multi-block placement, and it is
the first real consumer of the V2 foundation types (`vec3`, `list<T>`).

## 1. Unchanged from V2

File layout, comments, headers, indentation, types, reserved words, fail-fast
`CODE:LINE`, forward references — all per V1/V2. No new error codes.

## 2. Header

First significant line: `syntax 3`. The reference parser accepts `syntax 1`,
`2` and `3` and reports the version in the tree (`"syntax": 1 | 2 | 3`).
Anything else is `E_MATOU_VERSION` at line 1.

## 3. New genre (v3-only): structure

A structure is decided purely and placed by the bridge. Fields, all required:

- `anchor : vec3` — origin offset in cells (signed, any value).
- `size : vec3` — extents in cells. Syntactically any integers; non-positive
  extents are refused by the deciding job with a named error, never defaulted
  (the parser stays syntactic — same split as `feature.count`, which the
  `OwnedVeinJob` refuses at decide time, not at parse time).
- `palette : list<block_ref>` — blocks the job may place. Empty is accepted
  by the parser (uniform list rule) and refused by the job.
- `parts : list<structure_ref>` — sub-structures placed with this one, order
  preserved; empty = leaf. Forward references allowed (pass 2, as in V1).
- `count : u32` — occurrences to decide per tick (mirrors `feature.count`).

Version gating (existing code paths, no new codes):

- `genre Structure` or a `structure` instance in a `syntax 1|2` file is
  `E_MATOU_GENRE` at that line.
- A `structure_ref` field type in a `syntax 1|2` file is `E_MATOU_TYPE` at
  the field line.

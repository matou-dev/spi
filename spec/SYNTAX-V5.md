# SYNTAX-V5 — weakspot genre delta over V4

**Status: FROZEN after gate.** `SYNTAX-V1.md`, `SYNTAX-V2.md`,
`SYNTAX-V3.md` and `SYNTAX-V4.md` are untouched; this file is the
complete delta.
Rationale: after worldgen primitives (`feature`, `structure`, `vein`),
the next content table is combat: per-bone damage multipliers decided
purely and applied by the bridge hook. It is a V1-types-only genre
(`string` instance names, one `f32` field) — zero new types, zero new
error codes; the version gate exists so older files never silently
gain combat semantics.

## 1. Unchanged from V4

File layout, comments, headers, indentation, types, reserved words, fail-fast
`CODE:LINE`, forward references — all per V1/V2/V3/V4. No new error codes.

## 2. Header

First significant line: `syntax 5`. The reference parser accepts `syntax 1`,
`2`, `3`, `4` and `5` and reports the version in the tree
(`"syntax": 1 | 2 | 3 | 4 | 5`). Anything else is `E_MATOU_VERSION` at line 1.

## 3. New genre (v5-only): weakspot

A weakspot names one bone of the content beast and the damage
multiplier a struck hit on that bone pays. The instance name IS the
bone (same rule as `block my_ore` and `mob my_beast` — no separate
bone field, never a shadow name). Fields, all required:

- `mult : f32` — damage multiplier for this bone (e.g. `2.0` on the
  head). Syntactically any float; a missing, non-positive or
  non-finite multiplier, an empty table, or a duplicate bone is
  refused by the deciding table with a named error, never defaulted
  (the parser stays syntactic — same split as `vein.size`, which the
  `VeinPlaceJob` refuses at decide time, not at parse time).

The mob reach attribute stays a `Mob` field (`reach : f32`, V1 scalar —
no genre needed): the weakspot table and the reach attribute join by
file co-location (one owned file funds one mob plus its weakspots),
same join as the loot/spawn tables.

Version gating (existing code paths, no new codes):

- `genre Weakspot` or a `weakspot` instance in a `syntax 1|2|3|4` file is
  `E_MATOU_GENRE` at that line.
- A `weakspot_ref` field type in a `syntax 1|2|3|4` file is `E_MATOU_TYPE`
  at the field line.

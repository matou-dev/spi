# SYNTAX-V6 — per-mob weakspot join delta over V5

**Status: FROZEN after gate.** `SYNTAX-V1.md`, `SYNTAX-V2.md`,
`SYNTAX-V3.md`, `SYNTAX-V4.md` and `SYNTAX-V5.md` are untouched; this
file is the complete delta.
Rationale: single-mob co-location (one owned file funds one mob plus
its weakspots) cannot name which mob a weakspot funds once a file
carries several mobs. Per-mob combat needs a weakspot-to-mob join key,
so the weakspot gains a required `mob : mob_ref` field and the join
rule becomes per-mob: each weakspot funds one `(mob, bone)` pair. It
is a V1-types-only field (`mob_ref` is valid since V1) — zero new
genres, zero new types, zero new error codes; the version gate exists
so older files never silently gain join semantics.

## 1. Unchanged from V5

File layout, comments, headers, indentation, types, reserved words,
fail-fast `CODE:LINE`, forward references — all per V1/V2/V3/V4/V5.
No new error codes.

## 2. Header

First significant line: `syntax 6`. The reference parser accepts
`syntax 1`, `2`, `3`, `4`, `5` and `6` and reports the version in the
tree (`"syntax": 1 | 2 | 3 | 4 | 5 | 6`). Anything else is
`E_MATOU_VERSION` at line 1.

## 3. Weakspot join key (v6-only): `mob : mob_ref`

A v6 weakspot still names one bone (the instance name IS the bone) and
still carries its damage multiplier (`mult : f32`, refused at decide
time when missing, non-positive or non-finite — the parser stays
syntactic, same split as `vein.size`), and gains one required field:

- `mob : mob_ref` — the content mob this bone funds (qualified
  `ns:name`, local namespace, forward refs allowed like all refs).
  The deciding table joins per-mob: each weakspot funds one
  `(mob, bone)` pair, and the mob `reach : f32` attribute stays a
  `Mob` field (one reach per mob, no genre needed). Instance-name
  uniqueness follows the join: a v6 `weakspot` name may repeat across
  mobs (every beast has a `head`), so the parser keys v6 weakspot
  duplicates by `(mob, bone)` — same bone on different mobs parses,
  the same `(mob, bone)` twice is refused by the deciding table with
  a named error, never a quiet pick.

Version gating (existing code paths, no new codes):

- A `syntax 6` weakspot WITHOUT `mob` is refused by the deciding
  table with a named error, never defaulted (same split as
  `vein.size`: the parser accepts the genre shape, the table decides
  the join).
- `syntax 1|2|3|4|5` files keep the V5 co-location single-mob path;
  a weakspot CARRYING `mob` in such a file is refused by the deciding
  table with a named error (the parser stays syntactic — a
  `mob : mob_ref` field declaration is V1-valid syntax, so the
  refusal lives at decide time, never at parse time).
- No new genre, no new type, no new code (`mob_ref` is valid since
  V1; `genre Weakspot` and `weakspot` instances stay v5-gated as
  before).

Loot and spawn tables keep their co-location single-mob joins (their
`:multi` refusals are untouched by this delta).

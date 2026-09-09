# SYNTAX-V2 — foundation types delta over V1

**Status: FROZEN after gate.** `SYNTAX-V1.md` is untouched; this file is the
complete delta. Rationale: V1 has no signed integers, no coordinates and no
collections — every future genre (`structure`, `biome`, ...) needs them, so
the base types land first with zero business semantics.

## 1. Unchanged from V1

File layout, comments, headers, indentation, the closed 4 genres
(`block`, `item`, `mob`, `feature`), reserved words, fail-fast `CODE:LINE`,
forward references — all per `SYNTAX-V1.md`. No new error codes.

## 2. Header

First significant line: `syntax 2`. The reference parser accepts `syntax 1`
and `syntax 2` and reports the version in the tree (`"syntax": 1 | 2`).
Anything else is `E_MATOU_VERSION` at line 1 (fail-fast, as in V1).

## 3. New scalar types (v2-only)

- `i32`: signed 32-bit integer, `^-?\d+$`. JSON number.
- `vec3`: three signed integers `x,y,z`. Each comma-separated element is
  trimmed, then must match `^-?\d+$`; surrounding spaces around elements are
  allowed, floats and wrong arity are not. JSON `[x, y, z]` (numbers).
  Anything else = `E_MATOU_TYPE` at the value line.

## 4. Generic type (v2-only)

- `list<T>` where `T` is one of `f32 u32 i32 bool string vec3 <genre>_ref`
  (genres = the closed V1 set). Never nested: `list<list<...>>` is
  `E_MATOU_TYPE` at the field line.
- Value: `[e1, e2, ...]`, `[]` allowed (empty list). Elements are trimmed;
  the split is on top-level commas only (a comma inside `"..."` never
  splits). Each element follows the scalar rule for `T`, including the V1
  reference visibility rules; the first bad element is `E_MATOU_TYPE` (or
  `E_MATOU_UNKNOWN_REF` for references) at the value line. JSON array.
- Unknown type names — including these three in a `syntax 1` file — are
  `E_MATOU_TYPE` at the field line (existing code path, no new codes).

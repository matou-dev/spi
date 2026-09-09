# AUTHORING.md — writing a matou test mod (content or client proof)

One page for the author of the *next* test mod. Normative contracts live
in the code (`fr.iamacat.spi`, `fr.iamacat.bridge`) and the frozen specs
(`spec/SYNTAX-V1.md`, `V2`, `V3`); this file only maps goal to interfaces
so the first cut is the right one.

## 1. Which interfaces

| Goal | Implement | Never |
|---|---|---|
| Content proof (worldgen, structures) | `ContentPack` (+ `ConfigurablePack` when operators pass `k=v` args), one `MatouJob<List<String>>` per feature | Touch the world, read files on the tick path, keep mutable statics |
| Client proof (overlay, minimap) | One `MatouJob<List<String>>` reading `Snapshot` states the bridge sealed | Draw, touch vanilla buffers, read the live world |
| Bridge-side landing | Call `ForgeContent.decideAll/applyAll`, parse with `Cell`, load with `Packs.loadConfigured` | Re-split cell strings by hand |

Load a pack with `Packs.loadConfigured(className, args)`: it
instantiates (public no-arg constructor required) and configures
(`ConfigurablePack`) in one named step. A non-configurable pack with
non-empty args is refused — args never vanish silently. `jobs()` order is
the merge contract: first = owned backend, rest = late additives
(`ForgeContent.merge` keeps owned-first, dedups, never replaces).

## 2. Reading the snapshot

Use the typed accessors; do not re-implement null/type/shape guards per
field (that 50-line chain used to be copied into every job):

- `snap.require(id)` — unknown id refused, never null-surfaced.
- `snap.stringOf(id)` / `snap.longOf(id)` / `snap.mapOf(id)` — typed,
  refused loudly on mismatch.
- Counts (feature occurrences, scatter amounts): `Counts.positive(raw,
  id, "<YOUR_CODE>")` — one rule (missing / type / range), your code
  prefix so errors stay yours, e.g. `E_EXAMPLE_COUNT`.

## 3. Cells

Decide `List<String>`, build them with `Cell`:

- `Cell.of(x, z).render()` → `"x,z"` plane cells (wire y/block).
- `Cell.of(x, y, z, block).render()` → `"x,y,z:ns:block"` volume cells.
- Parse with `Cell.parse` / `parsePlane` / `parseVolume`;
  `posKey()` is the key position-keyed merges dedup on.
- Randomness is addressed: `MatouRng.forAddress(id.namespace, id.name,
  tick)` — same address, same stream, every JVM. Never `new Random()`.

## 4. Refusal convention

- Null argument → `NullPointerException` with a named code
  (`E_<MOD>_<WHAT>:null ...`).
- Bad value → `IllegalArgumentException` with a named code.
- Generic read failures (missing state, wrong primitive type, bad cell
  shape) reuse the shared codes (`E_MATOU_SNAPSHOT:*`,
  `E_MATOU_CELL:*`, `E_MATOU_ID:*`, `E_MATOU_RNG*`); only *domain* rules
  (radius bounds, glyph shape, count ranges, alias coverage) mint
  `E_<MOD>_*` codes. New codes cost nothing; silent defaults are the
  only forbidden move.
- Errors some other layer matches on (bridge `E_BRIDGE_*`,
  `E_FORGE_*`) are recoded at the seam, never renamed in place.

## 5. Packs with several jobs (the `ExamplePack` pattern)

Keep one id table (`ExampleIds`: every `namespace:name` once, counts and
grid beside them — job classes delegate, never recopy literals), expose
`job(MatouId)` beside `jobs()` so callers stop indexing positionally,
and add a job by extending the registry (one `put` + one states entry),
not by adding a factory overload. `fromFiles` overloads stay thin
delegates over a single wiring core.

## 6. Tests (no JUnit on these gates)

`check(cond, what)` + `expectRefused` / `expectNullRefused` taking
`Runnable`: lambdas are legal (`--release 8`), so refusal batteries go
in `Object[][]` tables (`{() -> ..., "what"}`) with one runner per
refusal kind instead of one anonymous class per case. Lock behaviour,
not messages: assert exception *types* plus decided values, shapes and
immutability. Compile against the `../spi` sibling checkout
(`MATOU_SPI_SRC` overrides the path); content proofs also run the
py/java parity (`check_content.py`).

## 7. Known limits (deliberate, single consumer each)

- One structure root per `ExamplePack`; wire a second tree only when a
  second consumer exists (per-tree alias coverage stays strict).
- Minimap radius is capped, not paged; glyphs are single chars; cell
  maps are string-keyed. Grow any of these when a mod outgrows it, not
  before.
- `.matou` files have no includes; fixtures stay explicit per file.

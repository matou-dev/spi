# Changelog — matou-dev/spi

Notable changes to this repo. Binaries ship inside the
`matou-dev/bridge-*` versioned server drops; store listings stay DRAFT
(see hub `NAMES.md`). Full notes per tag:
https://github.com/matou-dev/spi/releases.

## [Unreleased]

## [1.2.0] - 2026-09-09

Source release: https://github.com/matou-dev/spi/releases/tag/v1.2.0

- Authoring surface (test-mod scaling audit): shared pure `Cell` value
  type (`fr.iamacat.spi`, plane + volume parse/render/`posKey`, exact
  bridge accept-set, `E_MATOU_CELL` codes); `ForgeCells` delegates to it
  and recodes to identical `E_BRIDGE_CELL` messages (locked in
  `BridgeCheck`), `BlockCell` constructor now public. `Snapshot` gains
  `require`/`stringOf`/`longOf`/`mapOf` (`E_MATOU_SNAPSHOT` codes, `get`
  unchanged); shared `Counts.positive(raw, id, code)` count rule.
  `Packs.loadConfigured` instantiates + configures in one named step
  (stray args on a non-configurable pack refused), `PackSpec`
  constructor now public. New `AUTHORING.md` maps goal to interfaces
  (decision table, refusal convention, multi-job pack pattern, test
  conventions). Javadoc `@see` links (`MatouJob` to RNG/snapshot
  accessors, `ContentPack` to cells/sink). Additive only: no signature
  changed, no error code renamed.
## [1.1.0] - 2026-09-09

Source release: https://github.com/matou-dev/spi/releases/tag/v1.1.0

- Shared apply seam `fr.iamacat.bridge` (v1.1.0): `SpiBridge`, `CellSink`,
  `ForgeCells`, `ForgeSnapshot`, `ForgeContent`, `Packs` moved up from
  `bridge-1710` at identical FQNs (zero MC, only `matou-spi` deps);
  self-test `BridgeCheck` moves with them. `bridge-*` repos consume the
  seam from here and carry only their Forge side.
- `ContentPack` doc now names both decided cell shapes (`"x,z"` plane
  cells and `"x,y,z:ns:block"` V3 volume cells carrying their own y and
  block); the `"x,z"`-only wording went false when example1 wired its
  structure job.
- SYNTAX-V3 structure genre (`spec/SYNTAX-V3.md`, frozen; V1/V2 untouched):
  `anchor`/`size` (vec3), `palette` (list\<block_ref\>), `parts`
  (list\<structure_ref\>, empty = leaf), `count` (u32). Semantic validation
  (non-positive size, empty palette) belongs to deciding jobs, as with
  `feature.count`. Both parsers accept `syntax 1|2|3` with version-gated
  genres (`Structure`/`structure_ref` in older files stay refused loudly).
- Goldens: 18 shared py+java (`valid_v3_structure`, `err_v3_structure_field`,
  `err_v2_structure`, `err_v2_structref`; `err_version` now probes `syntax 4`).
- SYNTAX-V2 foundation types (`spec/SYNTAX-V2.md`, frozen; V1 untouched):
  `i32`, `vec3`, `list<T>` (v2-only, no new genre, no new error codes).
  Both parsers accept `syntax 1|2` and report it in the tree; anything else
  stays `E_MATOU_VERSION` (`err_version` golden now probes `syntax 3`).
- Goldens: 14 shared py+java (`valid_v2_types`, `err_v2_vec3`,
  `err_v2_list_elem`, `err_v2_list_ref`, `err_v1_vec3` version-gating lock).
- `MatouParse.java` refactored table-driven (precompiled patterns, lookup
  tables, fused int branches); 521 lines — the 450 alert was reviewed,
  residual growth is closed-set type branches, future genres add none.

- CI: runner pinned (`ubuntu-24.04`), JDK 21 via `setup-java` (temurin),
  actions pinned by SHA with Dependabot.
- Docs: README rewritten in English (R3 hygiene).

## [1.0.0] - 2026-09-09

Source release: https://github.com/matou-dev/spi/releases/tag/v1.0.0

- Pure zero-Minecraft contract: `MatouId` (`ns:name`), addressed `MatouRng`,
  immutable `Snapshot`, pure `Job` seam (decide on snapshot + apply on tick).
- `ContentPack` + optional `ConfigurablePack` contracts (public no-arg
  constructor) for content the bridge discovers by class name.
- Reference parser (`parser/matou_parse.py` + Java port) with 9 shared
  goldens; Java divergence fails the gate by name.

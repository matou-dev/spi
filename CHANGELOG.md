# Changelog — matou-dev/spi

Notable changes to this repo. Binaries ship inside the
`matou-dev/bridge-1710` versioned server drop; store listings stay DRAFT
(see hub `NAMES.md`). Full notes per tag:
https://github.com/matou-dev/spi/releases.

## [Unreleased]

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

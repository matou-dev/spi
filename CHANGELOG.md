# Changelog — matou-dev/spi

Notable changes to this repo. Binaries ship inside the
`matou-dev/bridge-1710` versioned server drop; store listings stay DRAFT
(see hub `NAMES.md`). Full notes per tag:
https://github.com/matou-dev/spi/releases.

## [Unreleased]

- Docs: README rewritten in English (R3 hygiene).

## [1.0.0] - 2026-09-09

Source release: https://github.com/matou-dev/spi/releases/tag/v1.0.0

- Pure zero-Minecraft contract: `MatouId` (`ns:name`), addressed `MatouRng`,
  immutable `Snapshot`, pure `Job` seam (decide on snapshot + apply on tick).
- `ContentPack` + optional `ConfigurablePack` contracts (public no-arg
  constructor) for content the bridge discovers by class name.
- Reference parser (`parser/matou_parse.py` + Java port) with 9 shared
  goldens; Java divergence fails the gate by name.

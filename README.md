# matou-spi — core SPI (zero Minecraft)

Pure contract of the dislocated scratch: types, `ns:name` IDs, multi-slot
registries, execution seam (pure `Job` on snapshot + `apply` on tick),
addressed RNG.
**No `net.minecraft` / `cpw.mods` / `net.minecraftforge` import here —
the `check` gate breaks otherwise.** Only `bridge-*` translates to a given MC.

M1 skeleton: `MatouId`, `MatouRng`, `Snapshot` + `MatouJob`
(`java/src/fr.iamacat.spi`), self-test `java/test`, gate `tools/check.sh`.
Shared apply seam since v1.1.0: `fr.iamacat.bridge` (`SpiBridge`,
`CellSink`, `ForgeCells`, `ForgeSnapshot`, `ForgeContent`, `Packs` —
pure, zero MC), consumed by every `bridge-*`.

See `NAMES.md` (identifier SSOT) and the org: https://github.com/matou-dev.

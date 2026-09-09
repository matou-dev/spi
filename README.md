# matou-spi — core SPI (zéro Minecraft)

Contrat pur du scratch disloqué : types, IDs `ns:name`, registries multi-slot,
seam d'exécution (`Job` pur sur snapshot + `apply` sur tick), RNG adressé.
**Aucun import `net.minecraft` / `cpw.mods` / `net.minecraftforge` ici —
le gate `check` casse sinon.** Seul `bridge-*` traduit vers un MC donné.

Voir `NAMES.md` (SSOT identifiants) et l'org : https://github.com/matou-dev.

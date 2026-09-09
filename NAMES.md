# NAMES.md — SSOT des identifiants (décisions Q-N1..Q-N3)

Règle : le slug GitHub n'est que de l'hébergement. Maven / modid / namespace
SPI / slugs plateformes sont gelés ici et ne suivent jamais un rename de repo.
Un slug global pris se détecte avant création (`curl
https://api.modrinth.com/v2/project/<slug>` → 404 = libre + recherche
CurseForge). Statut PROPOSED = pas encore réservé sur la plateforme.

| Repo GitHub | Maven | Modid FML | Namespace SPI | Modrinth | CurseForge | Statut |
|---|---|---|---|---|---|---|
| `matou-dev/spi` | `fr.iamacat:matou-spi` | — (lib, pas de mod) | `matou:` | `matou-spi` | — (lib, plus tard) | PROPOSED |
| `matou-dev/bridge-1710` | `fr.iamacat:matou-bridge-1710` | `matoubridge` | — (traducteur, pas de contenu) | — (jamais publié seul) | — (jamais publié seul) | PROPOSED |
| `matou-dev/example1` | — (mod, pas publié maven) | `example1` | `example1:` | `matou-example1` | `matou-example1` | PROPOSED |
| `matou-dev/minimap` | — (mod, pas publié maven) | `matouminimap` | `minimap:` | `matou-minimap` | `matou-minimap` | PROPOSED |

Notes :
- `matoubridge` diffère volontairement de l'ancien `matouengine` (matou-engine)
  pour cohabiter dans une même instance pendant la migration.
- `matouminimap` : `minimap` seul est pris partout, d'où le préfixe.
- Historique `quentin452/*` (MatouMap, Cat-Culling, matoulib-core...) : gelé tel
  quel, jamais renommé (JitPack/saves).

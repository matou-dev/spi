# SYNTAX-V1 — data language gelée (décision Q-S1)

**Statut : FROZEN.** Tout changement = v2 + amendement ici, jamais d'édition
silencieuse. Leçon CatzEngineNext (CATZ_SYNTAX_V2, P61-P65) : une grammaire
unique schema-driven, pas de hand-written par fichier.

## 1. Fichier

- Extension `.matou`, UTF-8. Première ligne obligatoire : `syntax 1`.
  Parser refuse tout autre numéro (pas de devinette de version).
- Commentaire : `#` jusqu'à fin de ligne. Espaces indifférents sauf
  indentation des instances (2 espaces, fixe).
- Erreurs : catalogue `E_MATOU_*` avec `file:line` toujours. Bare idents
  (référence non qualifiée) refusés — toute ref est `namespace:name`.

## 2. Headers

```matou
syntax 1
namespace example1.common

from matou.blocks use block.stone
```

- `namespace <dotted>` : un par fichier, préfixe des définitions locales.
- `from <ns> use <a.b>, <c.d>` : imports explicites, pas de glob.
- Référence : `<namespace>:<name>` (Fqid). Inconnue = `E_MATOU_UNKNOWN_REF`.

## 3. Genres v1 (fermé — 4, voir §5)

```matou
genre Block : Data
field hardness : f32
field opaque : bool

block my_ore
  hardness = 3.0
  opaque = true
```

- Déclaration `genre <Name> : Data` + `field <name> : <type>`.
- Types : `f32`, `u32`, `bool`, `string`, `<ns>:<genre>_ref`.
- Instance : `<genre-lower> <name>` + champs `name = value`, un par ligne.
- Champ manquant ou inconnu = `E_MATOU_FIELD`. Type faux = `E_MATOU_TYPE`.

Genres v1 : `block`, `item`, `mob`, `feature`. Tout autre genre =
`E_MATOU_GENRE` (pas de fallback, pas de skip silencieux).

## 4. Réservés

`syntax namespace from use genre field true false` — usage comme nom =
`E_MATOU_RESERVED`.

## 5. Versionnement

Genres versionnés : ajouter un genre ou un champ = nouvelle spec v2, jamais
d'extension ad hoc du parser. Le parser v1 refuse les fichiers `syntax 2`
avec `E_MATOU_VERSION` nommé (pas de demi-parse).

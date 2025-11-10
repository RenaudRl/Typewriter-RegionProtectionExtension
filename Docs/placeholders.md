# Placeholders TypeWriter Regions

Les placeholders ci-dessous sont exposés via l'expansion PlaceholderAPI `typewriter`. Ils permettent
de récupérer toutes les informations pertinentes sur les régions actives autour d'un joueur, en se
calant sur les conventions existantes dans WorldGuard tout en ajoutant des métriques plus poussées.

## Placeholders de base

| Placeholder | Description |
| --- | --- |
| `%typewriter_regions:count%` | Nombre de régions actives à la position du joueur. |
| `%typewriter_regions:list%` | Identifiants des régions actives, séparés par une virgule. |
| `%typewriter_regions:list_names%` | Noms affichés des régions actives. |
| `%typewriter_regions:stack%` | Pile formatée `P<prio> Nom`, utile en debug/action bar. |
| `%typewriter_regions:has:<id1,id2>%` | `true` si le joueur se trouve dans au moins une des régions listées. |

## Région dominante (priorité la plus haute)

Tous les placeholders ci-dessous se basent sur la région ayant la priorité la plus élevée à la
position du joueur (équivalent à l'ancien comportement WorldGuard).

| Placeholder | Description |
| --- | --- |
| `%typewriter_regions:primary%` | Nom affiché de la région dominante. |
| `%typewriter_regions:primary:id%` | Identifiant brut de la région. |
| `%typewriter_regions:primary:priority%` | Priorité numérique (plus grand = plus dominant). |
| `%typewriter_regions:primary:position%` | Rang dans la pile (1 pour la dominante). |
| `%typewriter_regions:primary:overlap_count%` | Nombre total de régions actives autour du joueur. |
| `%typewriter_regions:primary:parent%` | Identifiant du parent direct (vide si aucun). |
| `%typewriter_regions:primary:parent_name%` | Nom affiché du parent (si existant). |
| `%typewriter_regions:primary:owners%` | Liste des owners (UUID ou pseudos). |
| `%typewriter_regions:primary:members%` | Liste des members. |
| `%typewriter_regions:primary:groups%` | Groupes associés. |
| `%typewriter_regions:primary:depth%` | Profondeur dans l'arbre (0 = racine). |
| `%typewriter_regions:primary:is_global%` | `true` si la région provient d'un artefact global. |
| `%typewriter_regions:primary:path%` | Chemin hiérarchique depuis la racine (`Monde -> Quartier -> Parc`). |
| `%typewriter_regions:primary:flags_count%` | Nombre de flags effectifs appliqués. |
| `%typewriter_regions:primary:flags_local_count%` | Flags définis localement dans la région. |
| `%typewriter_regions:primary:flags_inherited_count%` | Flags hérités d'un parent. |
| `%typewriter_regions:primary:flags_override_count%` | Flags qui redéfinissent une valeur parent. |
| `%typewriter_regions:primary:flags_blocked_count%` | Flags ignorés car bloqués par la politique d'héritage. |
| `%typewriter_regions:primary:flags_list%` | Liste formatée `id=valeur` des flags effectifs. |

### Flags ciblés

Le segment `flag` reproduit la sémantique WorldGuard et ajoute des sorties enrichies.

| Placeholder | Description |
| --- | --- |
| `%typewriter_regions:primary:flag:<id>%` | Valeur formatée du flag `<id>` (ou vide si absent). |
| `%typewriter_regions:primary:flag:<id>:state%` | `local`, `inherited`, `blocked` ou `unset`. |
| `%typewriter_regions:primary:flag:<id>:source%` | ID de la région qui fournit la valeur effective. |
| `%typewriter_regions:primary:flag:<id>:source_name%` | Nom affiché de la région source. |
| `%typewriter_regions:primary:flag:<id>:history%` | Chaîne `parent -> enfant` retraçant les contributions. |
| `%typewriter_regions:primary:flag:<id>:parent%` | Valeur du parent immédiat si disponible. |
| `%typewriter_regions:primary:flag:<id>:parent_source%` | ID de la région parent ayant fourni la valeur. |
| `%typewriter_regions:primary:flag:<id>:parent_source_name%` | Nom affiché du parent fournisseur. |

## Accès aux autres régions de la pile

| Placeholder | Description |
| --- | --- |
| `%typewriter_regions:active:<index>%` | Nom affiché de la région à la position `<index>` (1 = dominante). |
| `%typewriter_regions:active:<index>:priority%` | Priorité de la région ciblée. |
| `%typewriter_regions:active:<index>:flag:<id>%` | Même API que pour `primary`, mais appliquée à la région ciblée. |

## Consultation par identifiant

Ces placeholders ne nécessitent pas la présence d'un joueur dans la région : ils interrogent le
cache du `RegionRepository` directement.

| Placeholder | Description |
| --- | --- |
| `%typewriter_regions:by_id:<id>:name%` | Nom affiché de la région `<id>`. |
| `%typewriter_regions:by_id:<id>:priority%` | Priorité enregistrée. |
| `%typewriter_regions:by_id:<id>:parent%` | Parent immédiat. |
| `%typewriter_regions:by_id:<id>:flags_list%` | Liste `id=valeur` des flags effectifs sur cette région. |
| `%typewriter_regions:by_id:<id>:flag:<flag>%` | Valeur d'un flag spécifique, même hors contexte joueur. |

## Notes d'utilisation

- Tous les placeholders retournent une chaîne vide lorsqu'aucune donnée n'est disponible.
- Les valeurs de flag sont sérialisées en conservant les couleurs (`§`), compatibles avec DeluxeMenus,
  Scoreboards et l'écosystème PlaceholderAPI.
- Les identifiants de flag respectent la nomenclature WorldGuard/ExtraFlags (`keep-inventory`,
  `teleport-on-entry`, etc.).
- Les segments `state`, `source`, `history` et `parent_*` facilitent le debug des héritages complexes
  directement depuis PlaceholderAPI.

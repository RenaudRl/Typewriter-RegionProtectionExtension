- entry.artifact.RegionArtifactEntry et entry.region.RegionDefinitionEntry modélisent désormais régions, presets et métadonnées UI, complétés par entry.artifact.GlobalRegionArtifactEntry pour les mondes entiers.
L’extension s’appuiera sur les `ManifestEntry` et `ArtifactEntry` pour stocker régions et presets, sur le DSL de commandes `/typewriter` pour administrer les zones, sur les nodes interactifs pour guider les sélections en jeu, et sur la DI Koin pour gérer dépôts et services, garantissant une intégration native côté panel et runtime TypeWriter.
Objectif : livrer une solution prête pour Paper 1.21.8+ qui supprime toute dépendance à WorldGuard, tout en restant 100 % alignée avec l’écosystème BornToCraft et le panel TypeWriter.
## Implémentation novembre 2025
- entry.artifact.RegionArtifactEntry et entry.region.RegionManifestEntry modélisent désormais régions, presets et métadonnées UI.
- service.storage.RegionRepository construit le graphe d'héritage, sérialise les overrides JSON et expose l'API consommée par les actions/commandes.
- flags.RegionFlagRegistry répertorie les flags WorldGuard/ExtraFlags et fournit un moteur déclaratif utilisé par service.runtime.ProtectionRuntimeService.
lags.RegionFlagRegistry répertorie les flags WorldGuard/ExtraFlags et fournit un moteur déclaratif utilisé par service.runtime.ProtectionRuntimeService.
- selection.SelectionService + selection.SelectionSession s'appuient sur NodesComponent pour les outils in-game, avec import WorldEdit et commit direct via le dépôt.
- command.ProtectionCommand ajoute /typewriter protection … (list, select, point, import-we, apply, cancel, info) pour piloter la roadmap depuis le jeu/panel.
- service.runtime.FlagInspectionService supporte désormais l'affichage multi-bossbar (priorité, hiérarchie complète, compteur d'héritage) pour refléter les superpositions de régions.
- placeholders/ProtectionPlaceholders expose l'intégralité des tokens WorldGuard (`%typewriter_regions:*%`) avec extensions debug (état d'héritage, source du flag, pile complète).

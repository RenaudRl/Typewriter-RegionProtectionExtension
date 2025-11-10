
### Feuille de route dtaille  systme de flags

1. **Cartographie fonctionnelle**
   - Dresser la liste exhaustive des flags supports (hrits de BTC-Protection, WorldGuard, ExtraFlags) avec leur type de valeur, porte (rgion, joueur, entit) et conditions d'activation.
   - Identifier les dpendances externes ncessaires (MMOItems, MythicMobs, ItemsAdder, etc.) et documenter les points de croisement.

2. **Infrastructure de rsolution**
   - tendre `RegionFlagRegistry` pour exposer un catalogue typ incluant priorit, hritage et compatibilit Folia.
   - Introduire un service `FlagEvaluationService` responsable de :
     - Charger et mettre en cache les bindings effectifs (hritage parent/enfant, priorits).
     - Exposer des API asynchrones (`suspend`) pour interroger l'tat d'un flag (boolen, numrique, vecteur, enum).
     - Publier des vnements internes (Koin) lors des modifications afin que les listeners se resynchronisent.

3. **Gestion des contextes dexcution**
   - Crer un objet `FlagContext` standardisant les donnes ncessaires (joueur, entit source/cible, position d'entre/sortie, action) afin d'viter la duplication dans chaque listener.
   - Mettre en place un pool de contextes rutilisables pour limiter les allocations lors des vnements frquents (mouvement, dgts).

4. **Implmentation des listeners Paper/Folia**
   - **Blocage construction/dconstruction** : `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerBucketEmptyEvent`, `PlayerBucketFillEvent`, `StructureGrowEvent`.
   - **Interaction monde** : `PlayerInteractEvent`, `PlayerInteractEntityEvent`, `PlayerCommandPreprocessEvent`, `PlayerTeleportEvent`, `VehicleEnterEvent`, `VehicleExitEvent`.
   - **Combat & entits** : `EntityDamageByEntityEvent`, `EntitySpawnEvent`, `CreatureSpawnEvent`, `EntityTargetEvent`, `EntityPotionEffectEvent`.
   - **Mcaniques spciales** : triggers TNT/pistons (`BlockPistonExtendEvent`, `BlockExplodeEvent`), redstone (`BlockRedstoneEvent`), portails (`PlayerPortalEvent`).
   - **QoL/Gameplay** : faim (`FoodLevelChangeEvent`), vol (`PlayerToggleFlightEvent`), chorus, perles, tridents, interactions montures.
   - Chaque listener :
     - Rcupre la rgion dominante via `RegionRepository.regionsAt` (priorit dcroissante).
     - Construit un `FlagContext` et dlgue  `FlagEvaluationService`.
     - Applique laction (annulation, modification de dgts, message custom, dclenchement de son/commande) en respectant les priorits.

5. **Hooks & intgrations**
   - Publier des vnements Typewriter (`RegionFlagAllowEvent`, `RegionFlagDenyEvent`) pour permettre aux autres extensions de sinscrire sur le flux de dcision.
     - SuperiorSkyblock pour harmoniser les flags rgionaux avec les iles.

6. **Outils de debug**
   - Commande `/tw protection flags <region>` affichant ltat de rsolution (hritages, overrides, valeurs effectives).
   - Mode inspection temps rel (`/tw protection inspect`) enrichi de ltat des flags applicables au joueur, avec dlai de mise  jour (bossbar/actionbar).

7. **Validation & QA**
   - Tests unitaires cibls sur `FlagEvaluationService` (rsolution hritage, cascade de priorits, conversions de type).
   - Suites dintgration simulant les principaux listeners avec `MockBukkit` ou un harness interne.
   - Scnarios manuels :
     - Matrice de tests par type de flag (build, combat, interaction, entits) sur plusieurs combinaisons de rgions imbriques.
     - Vrification des messages utilisateur (Adventure) et logs debug (SLF4J).

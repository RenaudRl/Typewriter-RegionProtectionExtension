# ProtectionExtension quick reference

## `/typewriter protection` commands
- `list`: display loaded regions with their priority and manifest identifier.
- `select <manifest>`: start a selection session for the chosen manifest (points are previewed through `NodesComponent`).
- `point <1|2>`: record a position at the player's current location.
- `import-we`: import the player's WorldEdit selection when available.
- `apply`: persist the current shape directly into the `RegionRepository` (create or update).
- `cancel`: close the active selection session.
- `info <region>`: summarise priority, members, and effective flags.
- `flags <region>`: inspect inherited values, local overrides, and the final result for every flag.
- `teleport <region>`: teleport to the approximate centre of the requested region.
- `inspect`: toggle the ActionBar/BossBar overlay that periodically lists the active flags.

## Editing workflow
1. Create manifests/artifacts from the panel using the `protection_region` tag.
2. In-game, run `/tw protection select <manifest>` then define the points manually or import from WorldEdit.
3. `/tw protection apply` persists the shape, priority, and flags through the JSON overrides (`RegionArtifactSnapshot`).
4. `/tw protection flags <region>` immediately validates inheritance to double-check the configuration.
5. Flag actions (`RegionFlagActionEntry`) call `RegionRepository.applyFlag`, cascading according to `FlagScope`.
6. `ProtectionListener` enforces the rules in-game (building, block breaking, PvP, entry/exit).

## Key services
- `RegionRepository`: region cache and inheritance resolution.
- `RegionFlagRegistry`: definitions and handlers (building, PvP, teleportation, etc.).
- `ProtectionRuntimeService`: central API for listeners and tests.
- `SelectionService`: player sessions, interactive nodes, WorldEdit import.
- `FlagInspectionService`: refreshes the `/tw protection inspect` ActionBar/BossBar overlays.

## Examples

```text
/tw protection flags spawn_centre
/tw protection inspect
```

The ActionBar overlay lists the leading flags with the inherited (`↑`) and override (`↻`) indicators.
The BossBar mirrors the legend while showing counts for active, inherited, and overridden flags.
The refresh delay and colours are configured through a `protection_settings` entry.

## Messages
The `messages` section of the `protection_settings` entry centralises every MiniMessage string sent to players. Defaults are in
English (`Action denied`, `Entry denied`, `Flag inspection enabled/disabled`, and so on) but can be overridden freely. Available
placeholders are summarised below:

| Field | Placeholders | Details |
| --- | --- | --- |
| `deniedAction` | `{reason}`, `{reason_line}` | `reason_line` automatically adds `: <reason>` when a reason is provided. |
| `deniedEntry` | `{region}`, `{flag}` | Used when entry is blocked (teleports or movement). |
| `inspectionActionBarPrefix` | `{priority}`, `{region}` | Prefix prepended to the `/tw protection inspect` ActionBar overlay. |
| `inspectionActionBarDetail` | `{flag}`, `{value}`, `{inherited}`, `{overrides}` | Inherited/override indicators are appended only when required. |
| `inspectionActionBarAdditionalRegions` | `{count}` | Number of additional overlapping regions. |
| `inspectionBossBarTitle` | `{index}`, `{total}`, `{priority}`, `{region}`, `{flags}`, `{summary}` | BossBar title for each active region. |
| `inspectionBossBarSummaryBase` | `{active}`, `{local}` | Base BossBar summary (active flag counters). |
| `inspectionBossBarSummaryInherited` | `{count}` | Added when at least one flag is inherited. |
| `inspectionBossBarSummaryOverrides` | `{count}` | Added when a flag overrides its parent value. |
| `inspectionBossBarDetail` | `{flag}`, `{value}`, `{inherited}`, `{overrides}` | Formatted flag details within the BossBar. |

Other fields (`inspectionToggleOn`, `inspectionToggleOff`, `inspectionNoRegionActionBar`, `inspectionBossBarDetailPrefix`, etc.)
do not expose placeholders but remain fully customisable. All strings are parsed via MiniMessage—syntax errors fall back to the
raw template with a warning in the logs.

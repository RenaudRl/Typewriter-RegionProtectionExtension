# Protection Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Target](https://img.shields.io/badge/Target-Paper-blue)
![Typewriter](https://img.shields.io/badge/Typewriter-0.9.0--beta--177-purple)

**Protection Extension** is a region management system for **TypeWriter**, built for **BTC Studio** infrastructure. Regions are cuboids or polygon prisms stored in artifacts; flags on a region cancel or change what players do inside it.

> **Deprecated:** this is the final compatibility release of the public Protection extension.
> Typewriter's native region system is the replacement for new deployments; this extension is
> retained for existing servers and will receive no new features.

---

## 🚀 Key Features

- **Cuboid and polygon regions**: draw them in game with a hotbar toolbar, or convert a WorldEdit selection.
- **World-wide regions**: a global region artifact covers whole worlds.
- **Flags, priority and parents**: flag evaluation by priority, with owners, members and a parent region.
- **Region events and filters**: enter and exit events, an audience filter, a group and a fact.
- **Inspection and migration**: show the flags that apply where you stand; import regions from WorldGuard.

---

## 🧩 Available Entries

| Type | Entries |
|---|---|
| Manifest | `protection_settings`, `region_definition` |
| Artifact | `protection_region_artifact`, `global_protection_region_artifact` |
| Event | `on_enter_protection_region`, `on_exit_protection_region` |
| Audience | `protection_region_audience`, `protection_region_group` |
| Fact | `in_protection_region_fact` |

## ⌨️ Commands and Permissions

`/typewriter protection <subcommand>` (permission `typewriter.protection`):
`list`, `edit`, `remove`, `import-we`, `apply`, `cancel`, `info`, `flags`, `teleport`, `inspect`, `wand`.

Admin subcommands (permission `typewriter.protection.admin`):
`setflag`, `clearflag`, `addowner`, `removeowner`, `addmember`, `removemember`, `reload`,
`migrate-from-worldguard`, `delete`.

`typewriter.protection.bypass` lets a player ignore the flags of every region.

---

## 📋 Requirements

- **Typewriter** `0.9.0-beta-177` (with PacketEvents, which Typewriter already requires)
- **Paper** (no Folia support is promised)
- Optional: WorldEdit or FastAsyncWorldEdit (`import-we`), MythicMobs (`mob-spawning` flag on Mythic mobs), BlueMap (regions on the map)

## ⚙️ Configuration

Everything is configured from the Typewriter web panel through the entries above.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/Typewriter-RegionProtectionExtension.git
cd Typewriter-RegionProtectionExtension

# Build the project
./gradlew clean build
```

Output: `build/libs/ProtectionExtension-<version>.jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

## 📖 Documentation

Full documentation (flag list, getting started): [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/protection/).

---

## 📜 Licence

**GNU General Public License v3.0 or later** — [LICENSE](LICENSE) — with a
**linking exception** for the Typewriter engine — [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md).

| | |
|---|---|
| You may | Run it anywhere, **including on a monetised server**. Study it, modify it, use it as a base, and redistribute it — **even for a fee**. GPLv3 §4 explicitly allows charging for a copy. |
| You must | Publish the complete corresponding source of your version under GPLv3, preserve the copyright notices, and **state that you modified it and when** (§5(a)). |
| You may not | Ship a closed-source or proprietary version, relicense under stricter terms, or strip the attribution and present this work as your own — §8 terminates your rights automatically. |
| Marks | **"Born To Craft"** and **"BTC Studio"** are **not** covered by the GPL. Fork it freely, sell your fork if you like — but **rebrand it**. |

> Reselling this code is legally allowed and practically pointless: whoever buys a
> copy from you receives, under the GPL, the right to redistribute it for free.
> That is the protection — not a clause forbidding sale, which the GPL does not
> permit us to add.

### About Typewriter

This is a **third-party extension**. It uses the public extension API of the
[Typewriter](https://github.com/gabber235/Typewriter) engine by gabber235 and
contains none of its source. Born To Craft Studio is not affiliated with or
endorsed by the Typewriter project.

The engine itself is **not** free software — its licence forbids redistributing
it. **Get it from the Typewriter project, and never redistribute it**, including
inside a fork of this repository.

Full attribution, the statement of modifications required by §5(a), and the
trademark reservation are in **[NOTICE.md](NOTICE.md)**. Read it before
redistributing.

© 2026 Born To Craft Studio.

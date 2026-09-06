# Protection Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20BTC--CORE-blue)

**Protection Extension** is a region management system for **TypeWriter**, engineered for **BTC Studio** infrastructure. It provides WorldGuard-grade protection features, fully optimized for Paper and Folia environments.

> **Deprecated:** this is the final compatibility release of the public Protection extension.
> Typewriter's native region system is the replacement for new deployments; this extension is
> retained for existing servers and will receive no new features.

---

## 🚀 Key Features

### 🛡️ Region Management
- **Full-Featured Engine**: Create and manage regions with a robust selection toolset.
- **Flag Presets**: Pre-configured flags for common protection scenarios.

### ⚡ Performance Optimized
- **Paper/Folia Safe**: Runtime enforcement designed for modern async architectures.
- **Lightweight Replacement**: Replaces the heavy WorldGuard + ExtraFlags dependency stack.

---

## ⚙️ Configuration

Protection Extension configuration is managed via TypeWriter's manifest system.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/ProtectionExtension.git
cd ProtectionExtension

# Build the project
./gradlew clean build
```

### Artifact Locations:
- `build/libs/Protection-[Version].jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

---

## 📜 License
Licensed under the **MIT License**.

## Documentation

Full documentation available at [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/protection/).

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

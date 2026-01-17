# Protection Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20BTC--CORE-blue)

**Protection Extension** is a region management system for **TypeWriter**, engineered for **BTC Studio** infrastructure. It provides WorldGuard-grade protection features, fully optimized for Paper and Folia environments.

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

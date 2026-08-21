# ReplayMod-WDG

ReplayMod-WDG is the Wargames Development Minecraft **1.7.10-only** ReplayMod fork.

This branch deliberately does not retain ReplayMod's historical multi-Minecraft-version build graph. There are no 1.8+, Fabric, Loom, `setCoreVersion`, or ReplayMod preprocessor projects in normal development.

## Target

- Minecraft: 1.7.10
- Forge: 10.13.4.1614
- MCP mappings: stable_12
- Java: 8 (x86_64 Java 8 for runtime development on Apple Silicon)
- ForgeGradle: anatawa12 ForgeGradle 1.2, pinned to 1.2-1.1.1

## Setup

Recommended setup:

```bash
./setup-1.7.10.sh
```

On Apple Silicon Macs, this selects an **x86_64 Java 8** JDK for Minecraft 1.7.10/LWJGL 2 development. Use that same JDK for IntelliJ's generated `Minecraft Client` configuration.

## Build

```bash
./gradlew clean build
```

See `SOURCE-PROVENANCE.md` for the source-normalisation provenance used to create this native 1.7.10 tree.

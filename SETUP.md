# ReplayMod-WDG 1.7.10 development setup

ReplayMod-WDG is intentionally a normal, single-project Forge 1.7.10 codebase.

## Requirements

- Git
- Java 8 JDK
- On Apple Silicon Macs: an **x86_64** Java 8 JDK for Minecraft/LWJGL 2 runtime testing (Temurin 8 is known to work)
- Internet access for the initial Gradle/Maven dependency resolution
- IntelliJ IDEA if using the generated IDEA project

## macOS setup

The recommended setup path is:

```bash
cd "$HOME/Documents/GitHub/ReplayMod-WDG"
./setup-1.7.10.sh
```

On Apple Silicon, the setup helper deliberately selects an **x86_64 Java 8** JDK. Minecraft 1.7.10 uses LWJGL 2 natives that cannot be loaded by an ARM64 Java process. In IntelliJ, use the same x86_64 Java 8 JDK for the generated `Minecraft Client` run configuration.

## Build

```bash
./gradlew clean build
```

## Video rendering / FFmpeg

Video export requires **FFmpeg** to be installed on the machine running ReplayMod. ReplayMod-WDG searches the inherited `PATH` and common platform locations. On macOS it also checks Homebrew locations including `/opt/homebrew/bin/ffmpeg` (Apple Silicon) and `/usr/local/bin/ffmpeg` (Intel). Windows discovery includes common WinGet, Scoop and Chocolatey locations plus `.minecraft/ffmpeg/bin/ffmpeg.exe`.

You do not need to hard-code an export command when FFmpeg is installed in one of those locations. If FFmpeg is installed somewhere unusual, add it to the environment `PATH` used by the Java/Minecraft process or configure the export command explicitly.

There is no ReplayMod preprocessor bootstrap, no multi-version Gradle graph, and no jGui Git submodule.

Both pinned jGui jars in `libs/` are source-controlled: the `-dev` jar is MCP-named for deobfuscated IDEA/`runClient` launches, while the production jar is SRG-named and embedded into the distributable ReplayMod-WDG jar.

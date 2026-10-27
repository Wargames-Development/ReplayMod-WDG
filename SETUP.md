# ReplayMod-WDG 1.7.10 development setup

ReplayMod-WDG is intentionally a normal, single-project Forge 1.7.10 codebase.

## Requirements

- Git
- Java 8 JDK
- On Apple Silicon Macs: an **x86_64** Java 8 JDK for Minecraft/LWJGL 2 runtime testing (Temurin 8 is known to work)
- Internet access for the initial Gradle/Maven dependency resolution
- IntelliJ IDEA if using the generated IDEA project

## Windows setup

From PowerShell in the repository folder:

```powershell
.\setup-1.7.10.ps1
```

The helper uses a Java 8 JDK from `JAVA_HOME` or common installation folders, generates the Forge development workspace and IDEA project, and validates a clean release build. It only changes the Java environment while it runs. To select a JDK explicitly:

```powershell
.\setup-1.7.10.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot'
```

If PowerShell blocks scripts, run the helper with a process-only policy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-1.7.10.ps1
```

## macOS setup

The recommended setup path is:

```bash
cd "$HOME/Documents/GitHub/ReplayMod-WDG"
./setup-1.7.10.sh
```

On Apple Silicon, the setup helper deliberately selects an **x86_64 Java 8** JDK. Minecraft 1.7.10 uses LWJGL 2 natives that cannot be loaded by an ARM64 Java process. In IntelliJ, use the same x86_64 Java 8 JDK for the generated `Minecraft Client` run configuration.

## IntelliJ IDEA

1. Run the setup helper for your platform before importing the project. Alternatively, with `JAVA_HOME` pointing to Java 8, run `./gradlew setupDecompWorkspace idea` (`.\gradlew.bat setupDecompWorkspace idea` on Windows).
2. Open the repository folder or `build.gradle` in IntelliJ and import it as a Gradle project.
3. In **File > Project Structure > Project**, select a **Java 8 JDK** as the Project SDK and set the language level to **8**. Add the installed JDK if it is not listed.
4. In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, use the **Gradle wrapper**, set **Gradle JVM** to the same **Java 8 JDK**, and set **Build and run using** and **Run tests using** to **Gradle**. Reload the Gradle project.
5. Select the shared **Build ReplayMod** run configuration and run it. The distributable JAR is written to `build/libs/`. You can also run **Tasks > build > build** from IntelliJ's Gradle tool window.

Use the full Gradle `build` task to produce the mod JAR: it performs source token replacement, generates the Mixin refmap, reobfuscates Minecraft names, and packages the bundled dependencies. IntelliJ's Java compiler alone does not perform these steps.

For a development launch, run **Tasks > forgegradle > runClient** from the Gradle tool window. The setup helper also generates `ReplayMod-WDG.ipr` with Forge's native `Minecraft Client`/`Minecraft Server` configurations for the file-based IDEA workflow; use Java 8 for those configurations too.

This repository pins Gradle 5.4.1 for ForgeGradle 1.2. Do not upgrade the wrapper or select Java 17/21/25 as the Gradle JVM. IntelliJ itself can run on its bundled runtime.

## Build

```bash
./gradlew clean build
```

## Build numbering

`version.txt` holds the base version (`2.5.2-WDG`). `modBuildNumber=72` in
`gradle.properties` sets the first packaging number for a checkout without a
local counter. Its first release is `2.5.2-WDG_X72`, then `_X73`, `_X74`, and so on.
The last successful packaging number is stored in
`.gradle/replaymod-build-number.txt`, which Git ignores. Builds never rewrite
`gradle.properties`, so IDE syncs and packaging do not create tracked changes.

Running `build` (including IntelliJ's **Build ReplayMod** configuration),
`assemble`, `shadowJar`, `jar`, `reobf`, or the default Gradle build selects the
next number when its task graph includes JAR packaging. It is persisted once
when JAR packaging succeeds, including reusing an up-to-date JAR from a failed
attempt; combining tasks in one
invocation does not increment it multiple times. Failed invocations and
`--dry-run` do not persist a new number. IDE imports (including imports with no
task names), `help`, `idea`, workspace setup, `compileJava`, and test-only runs
do not advance the counter. `clean` leaves the counter intact. Overlapping
packaging runs are rejected to prevent two builds reserving the same number.

The complete version is used in the JAR filename, manifest, mod annotation,
`mcmod.info`, Minecraft's Mods list, crash reports, and newly recorded replay
metadata. Compare the `_X` number against the newest JAR for the same base version
to identify an older installation. Each checkout has its own local sequence.
When intentionally setting a new starting number, edit `modBuildNumber` and
remove `.gradle/replaymod-build-number.txt` while no packaging build is running.
Copy that ignored counter separately if another checkout should continue the
same sequence; Git commits do not advance or reset it.

## Video rendering / FFmpeg

Video export requires **FFmpeg** to be installed on the machine running ReplayMod. ReplayMod-WDG searches the inherited `PATH` and common platform locations. On macOS it also checks Homebrew locations including `/opt/homebrew/bin/ffmpeg` (Apple Silicon) and `/usr/local/bin/ffmpeg` (Intel). Windows discovery includes common WinGet, Scoop and Chocolatey locations plus `.minecraft/ffmpeg/bin/ffmpeg.exe`.

You do not need to hard-code an export command when FFmpeg is installed in one of those locations. If FFmpeg is installed somewhere unusual, add it to the environment `PATH` used by the Java/Minecraft process or configure the export command explicitly.

There is no ReplayMod preprocessor bootstrap, no multi-version Gradle graph, and no jGui Git submodule.

Both pinned jGui jars in `libs/` are source-controlled: the `-dev` jar is MCP-named for deobfuscated IDEA/`runClient` launches, while the production jar is SRG-named and embedded into the distributable ReplayMod-WDG jar.

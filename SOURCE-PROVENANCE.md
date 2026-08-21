# ReplayMod-WDG 1.7.10 source provenance

This branch is intentionally a **single-version Minecraft 1.7.10 fork**.

## Upstream anchor

The WDG Git history remains anchored at ReplayMod's official 2.5.2 release commit:

`ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885` — `Release 2.5.2`

## One-time flat-source normalisation reference

ReplayMod 2.5.2's upstream repository stored a multi-version canonical source tree and generated old-version source through a preprocessor graph. WDG does not retain that graph.

For the one-time conversion, The P003 conversion imports the already-flattened Minecraft 1.7.10 ReplayMod-derived source from:

- repository: `Arownic/LegacyReplay`
- commit: `997671b57fb516b1e03821d248104b0ab7678f15`
- commit purpose: `Remove Minecraft version specific files and comments`

Residual ReplayMod `//#...` markers and inactive `//$$ ...` lines are removed as comments only; they are not re-evaluated through the historical multi-version preprocessor.

The conversion removes that reference fork's MapWriter-specific layer before installation. WDG-specific mod compatibility is added separately after the clean 1.7.10 baseline is proven.

## Build target

- Minecraft 1.7.10
- Forge 10.13.4.1614
- MCP `stable_12`
- Java 8
- anatawa12 ForgeGradle `1.2-1.1.1`
- ReplayStudio `com.github.ReplayMod:ReplayStudio:a1f82a7`

No Fabric/Loom or post-1.7.10 Minecraft subprojects are part of this branch.


## 1.7.10 mixin normalization

The flattening snapshot retained one modern descriptor literal in `Mixin_PreserveDepthDuringHandRendering`. P003b replaces that inactive-era `RenderSystem.clear` descriptor with the Minecraft 1.7.10 direct `org.lwjgl.opengl.GL11.glClear(I)V` target before compilation. No post-1.7.10 rendering API remains in the native source tree.

## jGui dependency

The original ReplayMod 2.5.2 commit referenced jGui submodule `6641836321f4583e6f56d8ceb59da452213396e7`.
The native flat baseline vendors a generated Minecraft 1.7.10 jGui JAR from the official ReplayMod/jGui repository at `a4cf26044dfb4f13abd61644fa15126c89dfcb91`. This is a deliberate standalone-build normalization and is kept explicitly documented rather than hidden behind a submodule.

# Biblioteca for NeoForge on Minecraft 1.21.11

Biblioteca 1.0.3 supports Minecraft **1.21.11**, pinned to **NeoForge 21.11.48** from the
[official MDK](https://github.com/NeoForgeMDKs/MDK-1.21.11-ModDevGradle/blob/main/gradle.properties).
It requires Java 21 and NeoForge; Fabric API is not needed. The complete and no-render JARs share
gameplay, networking and world data. Multiplayer requires Biblioteca on both ends; a server may
use no-render while clients use the complete variant for periodic visuals.

The complete and no-render release artifacts are:

- `biblioteca-1.0.3+1.21.11-neoforge.jar`
- `biblioteca-1.0.3+1.21.11-neoforge-norender.jar`

The Minecraft dependency is exact. The NeoForge range is `[21.11.48,21.12)`; the explicitly
tested baseline is 21.11.48.

## Build and verification

The shared root build was run on JDK 25; this target compiles and executes its checks with the
installed Java 21 toolchain. ModDevGradle remains pinned to 2.0.148.

From the repository root:

```powershell
.\tools\neoforge-pilot.ps1 prepare -MinecraftVersion 1.21.11
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 1.21.11
```

The equivalent build command is:

```powershell
.\gradlew.bat -p platforms/neoforge-1.21.11 build
```

The version comes from `mod.version` in `stonecutter.properties.toml`. For development builds,
`-ModVersion` or Gradle's `-PbibliotecaVersion` can override it. Build validates
both packaged variants before copying them into `build/distributions/`. `assemble` only compiles
and packages. `status -MinecraftVersion 1.21.11` reads the local preparation/verification record.

## Implementation boundaries

`exportNeoForgeSources` supplies the existing 1.21.11 Stonecutter selection. The target retains
the shared NeoForge entrypoints, payload registration and client handler. Java target and Mixin
compatibility level are 21; validation also checks the class-file versions. NeoForge artifacts
use its named runtime and omit Fabric metadata, access wideners and Fabric-only adapters.

NeoForge recompiles the game, which changes the synthetic Snow Golem callback's name.
`mixin-targets.json` maps the Fabric selector `method_63010` to the verified native
`lambda$performRangedAttack$1` during source export. Only that selector changes; the aiming logic
remains shared. The export asserts the replacement, required injections remain enabled, and
an additional native-callback regression checks both seam directions, ballistic lift and the
ordinary-world result. Do not edit generated files under `build/neoforge-shared/1.21.11/`.

## Verified scope

Both packaged release variants passed four actual FML runs:

| Variant | Side | Strict target classes |
| --- | --- | ---: |
| Complete | Dedicated server | 56 |
| Complete | Client | 79 |
| No render | Dedicated server | 56 |
| No render | Client | 66 |

Checks include entrypoints, statistics, commands, join/state payloads, client scheduling and
disconnect cleanup, configuration persistence, saved-data codecs, boundary movement, pathing,
three sky modes, light recipients, the Snow Golem callback and native GPU draw records.

A real local server additionally created and reopened a flat world with a 1 GB heap and 49
forced chunks. Three 8×8 shafts cover ordinary, negative and chunk-border coordinates; digging,
re-roofing, reopening, saved blocks, all four dimension settings and shutdown cleanup passed.

The real Java 21 client loaded a copy of that world. Server/client sky light matched at 15,
then 0 after filling a shaft, then 15 after reopening it. The client rendered ordinary terrain
and the upper periodic bedrock layer, and a teleport to Y=325 wrapped to Y=-59 in the 384-row
world. A separate client run opened the native More tab and the dimension settings screen.
All five saved screenshots were visually reviewed. These graphical checks use NeoForge's
native renderer; shader packs and replacement renderers are outside this target's verified scope.

Reproduce the save test with an already accepted EULA file:

```powershell
python tools/verify_neoforge_save.py --runtime-config platforms/neoforge-1.21.11/build/validation/runtime.json --jar build/distributions/biblioteca-1.0.3+1.21.11-neoforge.jar --new-world --hybrid-dig --label fresh-hybrid-world --eula-file path/to/eula.txt --java path/to/java21 --javac path/to/javac21
```

`tools/verify_neoforge_client.py` accepts that runtime configuration and JAR, the saved test
world via `--world-template`, cached assets via `--assets-dir`, and `--asset-index 29`.
Use `--ui-only` without a world template to check the creation/settings pages. Pass Java 21
executables with `--java` and `--javac`. It copies templates into isolated run directories.

Evidence stays under the ignored `build/neoforge-1.21.11/` directory:

- `validation/results.json`: exact JAR hashes and four FML results.
- `save-reload/fresh-hybrid-world/results.json`: real write/stop/reopen runs.
- `client-validation/world/results.json`: live light values and three world screenshots.
- `client-validation/ui/results.json`: the two native UI screenshots.

The existing NeoForge 26.3 Java 25 target also passed all four packaged regressions after the
shared tooling changes. Each configured target is rebuilt and checked for the 1.0.3 release.

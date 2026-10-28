# Biblioteca for NeoForge 26.1.x

Biblioteca 1.0.2 now has separate targets for Minecraft 26.1, 26.1.1 and 26.1.2.
Each target uses the exact version's Stonecutter-preprocessed gameplay/renderer code and the
shared NeoForge adapters. Save/quit, bounded sky-light rebuilding and live light synchronization
are included on all three targets.

| Minecraft | Tested NeoForge | Official MDK |
| --- | --- | --- |
| 26.1 | 26.1.0.19-beta | [26.1](https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/blob/main/gradle.properties) |
| 26.1.1 | 26.1.1.15-beta | [26.1.1](https://github.com/NeoForgeMDKs/MDK-26.1.1-ModDevGradle/blob/main/gradle.properties) |
| 26.1.2 | 26.1.2.122 | [26.1.2](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle/blob/main/gradle.properties) |

The first two pins are the official beta builds for those Minecraft releases. Compatibility is
not widened across the three Minecraft versions. Game runtime requires Java 25 and NeoForge;
Fabric API is not required. Development additionally uses Python 3.11+, Gradle 9.8.0 and
ModDevGradle 2.0.148.

## Build and verify

From the repository root:

```powershell
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.1
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.1.1
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.1.2
```

`status -MinecraftVersion <version>` reads that target's checkpoint. The equivalent Gradle command
is `gradlew.bat -p platforms/neoforge-<version> build` (use `./gradlew` on Linux/macOS). The build
automatically invokes the existing version node's `exportNeoForgeSources` task, compiles the
shared code against the pinned NeoForge game, and validates both packaged variants before
collecting them in `build/distributions`. Reports are isolated by Minecraft version.

Use `assemble` for compilation/package-only work. Shared build logic remains in
`platforms/neoforge-common.gradle`; shared loader entrypoints and metadata are in `platforms/neoforge/`.
Edit the root sources/version directives, not the generated `build/neoforge-shared/<version>` tree.

## Deliverables

For each Minecraft version, the build produces:

- `biblioteca-1.0.2+<version>-neoforge.jar`: complete gameplay, networking, audio, UI and periodic visuals.
- `biblioteca-1.0.2+<version>-neoforge-norender.jar`: the same nonvisual functionality, with periodic visual Mixins removed.

The files are in `build/distributions/` and in the target's `build/libs/`. Use exactly the JAR
for your Minecraft version, and install one variant per instance. Both variants use mod ID
`biblioteca` and work on dedicated servers; use the complete variant for client visuals.

## Validation

All three targets passed all four actual FML loader runs, using the packaged JAR and strict
Mixin injection counting with side filtering:

| Variant | Distribution | Strict target classes per Minecraft version |
| --- | --- | ---: |
| Complete | Dedicated server | 58 |
| Complete | Client | 80 |
| No render | Dedicated server | 58 |
| No render | Client | 67 |

Each version selects 67 common Mixins, 30 complete-client Mixins or 11 no-render client Mixins.
Checks exercise entrypoints, statistics, commands, login/state packets, codecs, client scheduling
and disconnect, config persistence, save-data formats, movement/path surfaces, sky-light modes,
interior light-packet recipients and restoration, dimension models, native GPU draw records
and terrain ordering/resource lifetime. Matching Fabric version nodes also passed their client
and server regressions and the six Fabric artifact audits.

Real localhost-server tests additionally passed separately on 26.1, 26.1.1 and 26.1.2 using new
flat worlds, 49 forced chunks and a 1 GB heap. Each run excavated three full-height 8x8 shafts
(including chunk borders and negative coordinates), replaced and removed roofs, saved, stopped,
reopened with the block and all four settings intact, checked the shafts again, and stopped.

To reproduce for one target, using an already accepted EULA file:

```powershell
python tools/verify_neoforge_save.py --runtime-config platforms/neoforge-26.1/build/validation/runtime.json --jar build/distributions/biblioteca-1.0.2+26.1-neoforge.jar --new-world --hybrid-dig --eula-file path/to/eula.txt
```

Replace both occurrences of `26.1` for another target. Test worlds and logs stay under
`build/neoforge-<version>/save-reload/`; the test-only probe is never shipped.

Evidence per target:

- `build/neoforge-<version>/state.json` and `verify.log`
- `build/neoforge-<version>/validation/results.json`, with exact JAR SHA-256 values
- `build/neoforge-<version>/validation/{full,norender}/{server,client}/loader.log`
- `build/neoforge-<version>/save-reload/fresh-hybrid-world/results.json` and its server logs

These tests cover real game classes, native light/network paths and actual server save/reopen.
Graphical client play, remote multiplayer peers and third-party mod packs have not been tested
on these new targets. See the [shared verification guide](../../README.md#verification) for coverage.

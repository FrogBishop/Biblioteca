# Biblioteca for NeoForge 26.2

This target builds Biblioteca 1.0.1 for **Minecraft 26.2**, pinned to **NeoForge 26.2.0.93**
(FML 11.0.16). The pin comes from the [official 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle/blob/main/gradle.properties).
Development uses Java 25, Gradle 9.8.0, ModDevGradle 2.0.148 and Python 3.11 or newer.
Game installations require Java 25 and NeoForge; Fabric API is not required.

## Build

Run from the repository root:

```powershell
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.2
.\tools\neoforge-pilot.ps1 status -MinecraftVersion 26.2
```

The equivalent Gradle command is `gradlew.bat -p platforms/neoforge-26.2 build` on Windows,
or `./gradlew -p platforms/neoforge-26.2 build` on Linux/macOS. A successful build validates
both packaged variants on both distributions before copying them to `build/distributions`.
Use `assemble` to compile/package without running the regressions.

The shared source tree stays at Minecraft 26.3. `prepareSharedSources` invokes the existing
Stonecutter 26.2 node's `exportNeoForgeSources` task, which compiles that Fabric node and exports
its exact preprocessed/selected sources and resources into `build/neoforge-shared/26.2`.
Fabric loader adapters and metadata are excluded from the export. The NeoForge build then uses
the shared adapters in `platforms/neoforge/` and `platforms/neoforge-common.gradle`.

The build was verified with the exported directory absent: it regenerates the sources without
manual preparation. Source/resource/config changes invalidate that export, and `--offline` is
forwarded to the nested version build. Edit root sources and version directives, not the export.

## Artifacts

- `build/distributions/biblioteca-1.0.1+26.2-neoforge.jar`: complete gameplay, networking, UI,
  audio and periodic visuals; works on clients and dedicated servers.
- `build/distributions/biblioteca-1.0.1+26.2-neoforge-norender.jar`: the same gameplay,
  networking, prediction, audio and UI, with periodic visual Mixins removed.

Install one variant per instance. These JARs require Minecraft **26.2**; use the separate
[26.3 target](../neoforge-26.3/README.md) for 26.3. Metadata permits NeoForge 26.2.0.93 and later
26.2 builds, while the specifically tested baseline is 26.2.0.93.

The target carries forward the 1.0.1 save/quit and bulk sky-light queue fixes. Its renderer uses
26.2's GPU draw groups and shared Uber buffers. Its dimension configuration uses that version's
bounded scrollable UI. The exact 26.2 source selection contains 66 common Mixins, 30 complete
client Mixins and 11 no-render client Mixins.

## Validation

All four packaged FML checks pass with strict required injections and inactive-side class filtering:

| Variant | Distribution | Target classes |
| --- | --- | ---: |
| Complete | Dedicated server | 57 |
| Complete | Client | 79 |
| No render | Dedicated server | 57 |
| No render | Client | 66 |

The checks cover loader lifecycle/statistics, the complete command event, login/state transport,
the registered clientbound codec and main-thread receiver, disconnect clearing, config persistence,
SavedData round trips, native entity/movement/path surfaces, sky-light modes and propagation,
dimension selection models, native GPU draw records, terrain sorting and resource lifetime.
26.3-only rendering and game API checks are not compiled against the 26.2 classpath.

`build/neoforge-26.2/state.json`, `verify.log`, and `validation/results.json` record the result
and exact JAR hashes; per-variant/per-side logs are under `validation/`. Reports are separate
from 26.3's `build/neoforge-pilot` directory.
Fabric 26.2 also passed its 57 server / 79 client target checks, shared gameplay regressions
and both variant artifact audits after exporting the current sources.

A real-server save/reload cycle also passed with a freshly generated flat 26.2 world, all four
dimension settings enabled, 49 forced chunks and a 1 GB heap. The world and placed block were
saved, the server closed all three levels, the same world reopened with its data intact, and
the server closed again. Native shutdown took 702 ms and 103 ms in the recorded run. Reproduce
with an already accepted EULA file:

```powershell
python tools/verify_neoforge_save.py --runtime-config platforms/neoforge-26.2/build/validation/runtime.json --jar build/distributions/biblioteca-1.0.1+26.2-neoforge.jar --new-world --eula-file path/to/eula.txt
```

The test creates an isolated localhost server under `build/neoforge-26.2/save-reload/`. A copied
world can be supplied with `--world-template` instead of `--new-world`; use a world from the
matching Minecraft version with Overworld looping and hybrid sky enabled. No player instance
is modified by these commands. The test-only probe is not shipped in either JAR.

Graphical client play, real multiplayer peers and third-party mod packs remain outside this
automated verification. The shared-build extraction was also regressed on NeoForge 26.3, whose
two JARs retain the exact previously verified hashes.

# Biblioteca for NeoForge 26.3

This independent target builds Biblioteca 1.0.1 for Minecraft 26.3, tested on NeoForge
26.3.0.98 and 26.3.0.99. It shares gameplay, persistence, commands, UI and rendering with the
Fabric target. The shared source version is 26.3; edit the repository's root sources rather
than Stonecutter-generated copies for older Minecraft releases.

Build/package/validation logic is shared in `platforms/neoforge-common.gradle`, and loader
adapters/metadata live in `platforms/neoforge/`. The independent [26.2 target](../neoforge-26.2/README.md)
consumes Stonecutter-preprocessed sources; this active 26.3 target consumes the root sources directly.

Requirements: JDK 25 and Python 3.11 or newer. The repository supplies Gradle 9.8.0 and pins
ModDevGradle 2.0.148. Normal game installations need NeoForge and Java 25; Python is only
used by the development regression checks. Fabric API is not a NeoForge dependency.

## Build and verify

From the repository root on Windows:

```powershell
.\tools\neoforge-pilot.ps1 verify
.\tools\neoforge-pilot.ps1 verify -NeoForgeVersion 26.3.0.99
.\tools\neoforge-pilot.ps1 status
```

The underlying cross-platform command is:

```sh
./gradlew -p platforms/neoforge-26.3 build --console=plain --max-workers=1
```

Use `gradlew.bat` on Windows. The `build`/`check` tasks run `verifyNeoForge`: both packaged
variants are loaded through the actual FML transformer on dedicated-server and client
distributions, with inactive-side class filtering and strict Mixin injection counts.
Only successful verification allows `collectArtifacts` to copy the JARs to `build/distributions`.
`assemble` builds the JARs in this target's `build/libs` without running regressions.

If Python is not on PATH, pass `-PpythonExecutable=/path/to/python` to Gradle. The verifier
uses the same Java 25 toolchain selected for compilation. For individual diagnostics:

```sh
./gradlew -p platforms/neoforge-26.3 assemble writeValidationConfig
python tools/verify_neoforge.py --variants full --sides client
```

The Windows runner records `running` before starting and `verified` or `failed` afterward in
`build/neoforge-pilot/state.json`. `verified` requires all four results and matching JAR hashes.
An interrupted run is never considered successful. The older `prepare` action remains available
to prepare only the patched game; its `prepared` state does not mean that mod validation ran.

## Deliverables

- `build/distributions/biblioteca-1.0.1+26.3-neoforge.jar`: complete gameplay and periodic visuals;
  works on both clients and dedicated servers.
- `build/distributions/biblioteca-1.0.1+26.3-neoforge-norender.jar`: the same gameplay,
  networking, prediction, audio and configuration UI, with periodic visual Mixins removed.

Install one variant per instance; both have the mod ID `biblioteca`. Use the complete variant
for periodic visuals. Minecraft compatibility is restricted to 26.3. NeoForge 26.3.0.98 is the
tested baseline; the declared NeoForge range allows later 26.3 builds, not 26.4.

1.0.1 fixes the save/quit hang in 1.0.0. Dimension attributes now remain active until native
chunk saving and level shutdown finish. Bulk sky-light rebuilding invalidates only the batch
boundary, avoiding an increase-queue explosion when many previously lit chunks reload together.

## Platform implementation

`NeoForgeBiblioteca` registers the common initialization, custom statistic at `RegisterEvent`,
commands, login synchronization, and versioned clientbound payload codec. The separate client
entrypoint registers the main-thread payload handler, disconnect cleanup and NeoForge config
directory. Common code has no Fabric API dependency. The saved world format and packet content
are shared; cross-loader multiplayer compatibility has not been validated.

`gradle/26.3-sources.json` controls the 69 common Mixins, 30 complete-client Mixins and 11
no-render client Mixins for both loaders. The access transformer grants the same terrain draw
group access as the Fabric access widener. Two injection selectors account for NeoForge's
additional explosion and block-entity extraction overloads without relaxing required injections.

## Validation evidence and limits

`build/neoforge-pilot/validation/results.json` records each variant/side, the exact JAR SHA-256,
target count and completed regression groups. Individual `loader.log` files and compiler logs
are beside it. Checks cover actual entrypoints and registry lifecycle, login packets, registered
codecs and client handlers, main-thread scheduling, disconnect cleanup, config persistence,
SavedData migration, dimension commands and UI, collision/movement/pathfinding, mob targeting,
three sky modes, terrain sorting, transparency, weather and render resource lifetimes.

The tests use the real game and NeoForge classes with isolated world/GPU fixtures. They do not
open a game window, connect real network peers or replace manual visual and third-party mod-pack
testing. The existing Fabric 26.3 regressions are run separately with
`tools/verify_matrix.py` and `tools/verify_artifacts.py`.

The save/reload regression additionally starts a real server on localhost, using a copy of a
supplied world with Overworld looping and hybrid sky enabled. It requires an existing accepted
EULA file and leaves the supplied world template unchanged:

```powershell
python tools/verify_neoforge_save.py --jar build/distributions/biblioteca-1.0.1+26.3-neoforge.jar --world-template path/to/test-world --eula-file path/to/eula.txt
```

It loads 49 forced chunks with a 1 GB heap, saves and closes, reopens the same copy to check a
persisted block and dimension attributes, then closes again. The test observer checks that
attributes remain available through native chunk flush and unload, and are cleared afterward.
Results and logs are under `build/neoforge-pilot/save-hang/`; the probe mod is never shipped.

The existing GitHub Actions pilot workflow still prepares only the NeoForge environment.
This change's complete build and validation have been run locally; no new remote artifact upload
was configured. [The recovery checkpoint](../../NEOFORGE_PILOT.md) records the verified scope.

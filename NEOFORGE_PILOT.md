# NeoForge migration / recovery checkpoint

Status: **NeoForge 26.2 and 26.3 targets complete; 1.0.1 builds pass packaged dual-side checks and real save/stop/reopen validation**.

## 26.2 expansion completed on 2026-10-26

The next target is Minecraft 26.2, pinned to the official MDK's NeoForge 26.2.0.93 (FML 11.0.16).
Dependency preparation, both artifacts and all four FML regressions have succeeded. Shared
sources come from the existing Stonecutter 26.2 node through `exportNeoForgeSources`; each
independent NeoForge build invokes that export as needed. Common loader adapters and build
logic now live under `platforms/neoforge/` and `platforms/neoforge-common.gradle`.

Reports are isolated under `build/neoforge-26.2/`;
26.3 retains `build/neoforge-pilot/`. Run `tools/neoforge-pilot.ps1 verify -MinecraftVersion 26.2`.
The complete 26.2 JAR passes 57 server / 79 client target classes; no-render passes 57 / 66.
The build also succeeds with its exported source directory absent. Real save/stop/reopen of a
new flat 26.2 world passed with all four attributes, 49 forced chunks and `-Xmx1G`; the two native
shutdowns took 702 ms and 103 ms. Evidence is in `build/neoforge-26.2/save-reload/fresh-world/`.
NeoForge 26.3's four regressions passed after extracting the common build, and both of its JAR
hashes are unchanged. [The 26.2 target guide](platforms/neoforge-26.2/README.md) documents commands.
Fabric 26.2 was also rebuilt and passed 57 server / 79 client targets, gameplay checks and
complete/no-render artifact audits. Its build log is `build/neoforge-26.2/fabric-regression.log`.

## Save/quit regression reported on 2026-10-26

The user's real 26.3.0.99 client used the exact delivered complete JAR (SHA-256
`cadb5f1764546190b63c3cdccce4f86d8e66c4f462320ee75de755d47674453a`). The log reaches
`Saving worlds` at 20:04:09, then two lighting workers throw `OutOfMemoryError: Java heap space`
while expanding the sky-light increase queue. There is no live game process left to capture.
Evidence and an isolated copy of the roughly 16 MB test world are in
`build/neoforge-pilot/save-hang/`. The original save has not been changed. The affected instance
now has the verified 1.0.1 complete JAR; its previous installed JAR is backed up as
`save-hang/installed-1.0.0-backup.jar`. `save-hang/installation.json` records the exact paths/hash.

Two related causes were fixed:

1. `MinecraftServerMixin` cleared attributes at the HEAD of `stopServer`, switching loaded
   periodic light engines to vanilla before their chunk/light tasks drained. Cleanup now runs
   at TAIL, after native saving, level closing and storage shutdown. A test-only observer on the
   real server reproduces the early-clear failure with the original 1.0.0 JAR.
2. Sky-light batch rebuilding queued a decrease for every previously lit cell, then refilled
   its sources before processing those decreases. This produced millions of redundant increase
   entries on a bulk saved-chunk reload. The batch now clears its interior directly and only
   queues outward invalidation at the batch boundary; sources/borders are then reseeded normally.
   A 3x3, 384-row regression checks bounded queue growth and all three sky modes.

Real-world verification on 26.3.0.99 uses a read-only template copied into isolated run directories,
49 forced chunks, and `-Xmx1G`. The fixed JAR saved and closed all three levels in 139 ms, reopened
the same world with the placed block and dimension settings intact, then closed in 125 ms.
Both processes exited successfully. The first shutdown-only repair exposed a second OOM on
reopen; the queue fix was applied and the complete save/reopen cycle rerun successfully.

Evidence: `save-hang/before-fix-probe/results.json`, `save-hang/after-queue-fix/results.json`,
their run logs, and `save-hang/validation-26.3.0.99.json`. The same final JAR hashes also pass the
four default 26.3.0.98 validations in `validation/results.json`. Fabric 26.3 was rebuilt and its
client/server regressions, new queue bound and artifact audits passed.

The original checks missed real server shutdown. `tools/verify_neoforge_save.py` and its separate
test-only probe now cover the full native save/drain/unload/cleanup/reopen sequence; those probe
classes are not included in either deliverable.

- Verified on 2026-10-26 with Minecraft 26.3, NeoForge 26.3.0.98, ModDevGradle 2.0.148,
  Gradle 9.8.0 and Java 25.
- Scope: the Biblioteca feature set plus the 1.0.1 save/reload fix on NeoForge Minecraft 26.2 and 26.3.
  Earlier NeoForge Minecraft targets have not been added.
- Existing Fabric 26.3 was rebuilt and passed its client/server regressions and artifact audit.
- These are local workspace changes and local deliverables. No release or remote upload was made.
- Read `git status --short --branch` before resuming. Full logs are under `build/neoforge-pilot/`.

## Original 26.3 pilot stages

- [x] Confirm and pin the official 26.3 toolchain.
- [x] Resolve and prepare patched Minecraft/NeoForge artifacts locally.
- [x] Add the independent NeoForge target and small platform boundary, sharing gameplay code.
- [x] Compile/package the complete and no-render JARs with NeoForge metadata and access rules.
- [x] Load both JARs through actual FML on client and dedicated-server distributions.
- [x] Run strict Mixin checks, loader-specific network/lifecycle checks and gameplay regressions.
- [x] Rebuild and verify the existing Fabric 26.3 target after shared changes.
- [x] Record deliverables, validation scope and remaining manual testing limits.

## Deliverables

Files are in `build/distributions/` and in the corresponding target's `build/libs/` directory.

| Variant | File | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Complete (26.2) | `biblioteca-1.0.1+26.2-neoforge.jar` | 252851 | `a0b8c9706d15c535dc55cb8bd4d7418f58f5fac2362014231c7baccb8202a134` |
| No render (26.2) | `biblioteca-1.0.1+26.2-neoforge-norender.jar` | 220009 | `687fabe057dae2d2d7d00df28e3c896c028f15ddc2dce52c12f6c1a2aa16aea1` |
| Complete | `biblioteca-1.0.1+26.3-neoforge.jar` | 263022 | `4ae1f3996bf4ae3b6d412d96f1ee869c46f8368e2a48a01d2a0a09dd472ccfe2` |
| No render | `biblioteca-1.0.1+26.3-neoforge-norender.jar` | 231648 | `20141dc2b97bc5020f3afb6e3413486325d56d46c9f81a5f213a6a37d3826440` |

The complete JAR works on clients and servers. The no-render JAR retains gameplay, networking,
prediction, audio and configuration UI, with periodic visual Mixins removed. Both use mod ID
`biblioteca`; install one variant per instance for that exact Minecraft version. NeoForge baselines
are 26.2.0.93 and 26.3.0.98 (26.3.0.99 is also verified). Each dependency range stays in its own MC family.

## Repeatable local validation

From the repository root with Java 25 and Python 3.11+:

```powershell
.\tools\neoforge-pilot.ps1 verify
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.2
.\tools\neoforge-pilot.ps1 verify -NeoForgeVersion 26.3.0.99
.\tools\neoforge-pilot.ps1 status
```

`verify` runs `gradlew.bat -p platforms/neoforge-26.3 build`. The Gradle `check` task invokes
`verifyNeoForge`, which loads the actual packaged JARs and runs the regressions. Distribution
collection depends on successful verification. Use `assemble` for a compilation/package-only run.

Each target's `state.json` records phase `neoforge_validation`,
status `verified` and exit code 0. The runner validates all four results and their JAR hashes
before recording success. A `running` record after an interruption is not success.

| Target | Variant | Distribution | Strict target classes | Result |
| --- | --- | --- | ---: | --- |
| NeoForge 26.2.0.93 | Complete | Dedicated server | 57 | PASS |
| NeoForge 26.2.0.93 | Complete | Client | 79 | PASS |
| NeoForge 26.2.0.93 | No render | Dedicated server | 57 | PASS |
| NeoForge 26.2.0.93 | No render | Client | 66 | PASS |
| NeoForge 26.3.0.98 and 26.3.0.99 | Complete | Dedicated server | 60 | PASS |
| NeoForge 26.3.0.98 and 26.3.0.99 | Complete | Client | 82 | PASS |
| NeoForge 26.3.0.98 and 26.3.0.99 | No render | Dedicated server | 60 | PASS |
| NeoForge 26.3.0.98 and 26.3.0.99 | No render | Client | 69 | PASS |
| Fabric 26.2 | Complete | Server | 57 | PASS |
| Fabric 26.2 | Complete | Client | 79 | PASS |
| Fabric 26.3 | Complete | Server | 60 | PASS |
| Fabric 26.3 | Complete | Client | 82 | PASS |

Multiple Mixins can target one class. 26.2 selects 66 common Mixins; 26.3 selects 69. Both select
30 complete-client or 11 no-render client Mixins. All configs remain required with
`defaultRequire=1`, and validation enables injection counting.

Evidence:

- `build/neoforge-pilot/verify.log`
- `build/neoforge-26.2/verify.log` and `state.json`
- `build/neoforge-26.2/validation/results.json` and per-side `loader.log` files
- `build/neoforge-26.2/save-reload/fresh-world/results.json` and real server logs
- `build/neoforge-pilot/state.json`
- `build/neoforge-pilot/validation/results.json` (exact artifact hashes and completed checks)
- `build/neoforge-pilot/validation/{full,norender}/{server,client}/loader.log`
- `build/neoforge-pilot/fabric-26.3-build.log`
- `build/validation/26.3/{server,client}/loader.log`
- `build/validation/results.json` and `build/validation/artifacts.json` (inspect 26.3 entries)

Coverage includes the actual FML mod constructors, custom statistic registration, complete command
event, login packet/state transport, registered clientbound codec, client receiver scheduling,
disconnect cleanup and config persistence. Shared regressions cover saved-data defaults/migration,
custom dimension commands and UI, repeated movement across the seam, collisions, paths/leashes,
mob sight/aim/projectiles, three sky modes, client interpolation/light synchronization, water
vertices, terrain ordering, visibility, transparency, weather slices and GPU resource lifetimes.

The core checks use real game/FML classes with isolated world and GPU fixtures. The additional
save regression starts a real server against copies of the reported world, without modifying
the original save. No graphical client window or real multiplayer peers were used in automated
verification. Manual visual gameplay, other mod packs, cross-loader multiplayer and other
Minecraft/NeoForge versions remain outside the verified scope.

## Implementation

- `Biblioteca` and `client/BibliotecaClient` contain shared initialization, commands and state
  application. Fabric entrypoints now live in `fabric/` and `client/fabric/` and preserve the
  existing Stonecutter version branches.
- NeoForge entrypoints live in `platforms/neoforge/src/main/java/`. They bind `RegisterEvent`,
  command/login events, clientbound payload registration, client-side handlers and logout events.
  Config files use `FMLPaths.CONFIGDIR`; NeoForge JARs contain no Fabric API references.
- `gradle/26.3-sources.json` provides the active node's Mixin selection. Older nodes export their
  exact preprocessed selection via `exportNeoForgeSources`; `platforms/neoforge-common.gradle`
  consumes the corresponding tree and selection. Shared loader adapters are compiled per target.
- `META-INF/neoforge.mods.toml` and `META-INF/accesstransformer.cfg` provide the loader metadata
  and the terrain draw-group access equivalent to the Fabric 26.3 access widener.
- NeoForge adds overloads to explosion entity processing and block-entity render extraction.
  Those shared injection selectors now match the overloads while retaining strict validation.
- `tools/NeoForgeMixinVerifier.java`, `tools/neoforge-validation/` and `tools/verify_neoforge.py`
  provide reproducible checks of both packaged variants through FML with side filtering.

## CI and earlier recovery history

The existing `.github/workflows/neoforge-pilot.yml` is unchanged and still prepares only the
environment. Its earlier run `40294382112` at commit `2b2d6c1cb19349d1d26eaa26fc45f7c755f2a01f`
succeeded; its downloaded evidence is under `build/github-actions/2b2d6c1cb193/40294382112/`.
This session's complete integration was verified locally. Automatic approval rejected expanding
the workflow to upload new JARs/logs because that external upload destination was not explicitly
authorized; no part of that workflow patch was applied.

Earlier local Maven timeouts at `neoform-runtime:2.0.31` were resolved when network access
recovered. Local environment preparation succeeded in 2m 52s, with offline reuse in 873ms.
The original requested five files remain in `build/neoforge-pilot/manual-downloads/`; core
dependency size/SHA-256 checks are in `build/neoforge-pilot/download-verification.json`.

For further targets, reuse the common NeoForge build, version-source export and validation tasks.
Do not repeat dependency recovery or treat the old preparation-only CI result as integration
evidence. [The target README](platforms/neoforge-26.3/README.md) documents builds and usage.

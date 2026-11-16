# Biblioteca 1.0.4 release verification

Release date: **2026-11-16**. The release contains **118 exact-version JARs**: complete and
no-render editions for **36 Fabric** and **23 NeoForge** Minecraft targets. The source version
is `1.0.4` in `stonecutter.properties.toml`; every filename and embedded mod version uses that
release number. Installation and upgrade notes are in [CHANGELOG.md](CHANGELOG.md).

## Final release checks

| Check | Result and scope |
|---|---|
| Fabric build | All 36 targets rebuilt, producing 72 release JARs |
| Fabric native regressions | 72 client/server runs, with required injections and version-specific data checks |
| Fabric package audit | 72 exact Minecraft/version predicates, Java targets, refmaps, access wideners and variant contents checked |
| NeoForge build and package regressions | All 23 targets rebuilt; 92 actual FML runs cover both variants on client and server |
| Binary comparison | 117 JARs differ only in version metadata; NeoForge 1.20.6 complete also includes the explicit local-variable selector described below |
| Publisher tests | 36 local tests cover metadata, variant routing, receipts, retries, uncertain uploads and the unsupported CurseForge snapshot |
| Publication preview | The real Mod Publish Plugin previews all selected publications before uploads |

The binary comparison checks every uncompressed JAR entry, including nested libraries,
rendering classes, gameplay classes, mixin configurations, resources and access metadata.
There are no added/removed entries. Each changed metadata file is checked against the exact
replacement of `1.0.4-dev.1` by `1.0.4`.

The one class-file exception is `ClassicTerrainYLoopMixin` in the complete NeoForge 1.20.6 JAR.
The rebuilt artifact picks up the shared source's explicit `@Local(ordinal = 0)` selection for
the render section. The frozen development JAR had the implicit selector. Full disassembly
confirms this annotation is the only class-content difference; every method instruction and
signature is unchanged. The final 1.20.6 packaged client/server checks pass on both variants.
The exact old/new class hashes and disassembly diff are retained in the release evidence.

The final native runs use the release build. Existing graphical, world-save and Sodium results
below retain their original frozen development binaries and dates; they are not described as
new graphical runs of the release JARs. The complete binary comparison connects that earlier
evidence to the release while identifying that one annotation change explicitly.

## Earlier functional and graphical evidence

The final NeoForge backport baseline includes 30 selected Sodium variant combinations,
two expected rejection controls for Sodium 0.9.2 on NeoForge 26.3, and three real server
save/stop/reopen tests on 1.20.1, 1.20.4 and 1.21.8.

Six real clients cover complete/no-render editions on NeoForge 1.20.1 and 1.20.4 with vanilla
rendering, and 1.21.8 with Sodium 0.7.3. All 36 final screenshots were reviewed. Native world
creation/settings controls, three full-build video controls versus zero in no-render, actual
server synchronization, crossings in both directions, continued movement and restoration after
disabling looping passed. The camera probes recorded 1,032 frames, including 934 fractional
interpolation frames; maximum coordinate error was about **0.00000012 blocks**.

Earlier 1.0.4 development checks cover persistent particles, actual sculk activation and wool
occlusion, entity and block-entity copies, distant first-person player copies, multiple cloud
layers, independent network observers, and native falling-block expiry. The snapshot checks
also cover its 43 original Easter egg names and repeated camera movement after crossing.
See [SODIUM_COMPATIBILITY.md](SODIUM_COMPATIBILITY.md),
[INFINITE_COMPATIBILITY.md](INFINITE_COMPATIBILITY.md),
[NEOFORGE_COMPATIBILITY.md](NEOFORGE_COMPATIBILITY.md) and [ROADMAP.md](ROADMAP.md).

These checks do not establish compatibility with every modpack, Sodium release, GPU or shader
pack. Iris shader interfaces and far-LOD integration remain outside this release's scope.
Minecraft 1.21.2 with the historical NeoForge/Sodium stack uses **Java 21**. NeoForge 26.3
requires **Sodium 0.9.3-beta.1 or newer** when Sodium is installed.

## Distribution and audit records

| Destination | Release files |
|---|---|
| GitHub Release `1.0.4` | All 118 JARs plus `SHA256SUMS.txt` |
| Modrinth Biblioteca | 59 complete editions, one JAR per exact Minecraft/loader version |
| Modrinth Biblioteca No Render | 59 no-render editions in the separate project |
| CurseForge Biblioteca | 58 primary files and their 58 no-render Additional Files |

CurseForge's official author API has no `20w14infinite` game-version label as of the release
date. The publisher explicitly excludes that platform/target pair and reports the reason in
its manifest. Both snapshot variants are available on Modrinth and GitHub with the correct
version. No substitute Minecraft label is used.

The publisher synchronizes both Modrinth project descriptions. CurseForge's upload API has no
description-editing operation, so `curseforge.md` and each run's `curseforge-description.md`
remain the prepared source for its author dashboard.

Local records, excluded from Git, are preserved under `build/release-1.0.4/`: `report.json`,
`binary-equivalence.json`, `SHA256SUMS.txt`, frozen `artifacts/`, native evidence and publishing
results. Each platform publication also keeps its frozen file snapshots, metadata, hash checks
and remote receipts under `build/publishing/runs/`; the shared recovery ledger is
`build/publishing/state.json`. The final development baseline remains separately preserved in
`build/neoforge-backport/`.

No player instance or user save is modified by these release checks. Tests use isolated worlds
and local build directories. The source commit, annotated tag and GitHub release identify the
same source state. See [PUBLISHING.md](PUBLISHING.md) for build, preview, upload and recovery commands.

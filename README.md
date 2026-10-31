# Biblioteca

**A periodic Y axis for Minecraft dimensions, with version-specific vanilla renderer adapters.**

Biblioteca connects a dimension's top and bottom. The server stores one period;
the client displays the visible periodic copies. No blocks, items, dimensions or generation
presets are added, and existing terrain (including bedrock) is retained.

`multiversion` is the default and maintained branch, covering **all 35 releases from 1.16.5 through 26.3**.
`main` is permanently archived as the historical Minecraft 26.3 Fabric implementation.
Each release has an exact-version artifact; a JAR
compiled for one release is not relabelled to accept a broader API range.

**Loader support:** Biblioteca supports **Fabric and NeoForge**. The planned **Forge** port
has been cancelled. NeoForge development and maintenance will continue, including support
for additional Minecraft versions.

Minecraft **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3** also have **NeoForge** targets, sharing gameplay and renderer code
with Fabric. Complete and no-render variants are built and validated independently; see the
[26.1 family guide](platforms/neoforge-26.1/README.md), [26.2 build guide](platforms/neoforge-26.2/README.md)
and [26.3 build guide](platforms/neoforge-26.3/README.md).
The current release is **1.0.3**, with 70 Fabric and 12 NeoForge artifacts across the targets above.
It extends live sky-light synchronization to every supported version, adds the Fabric 1.16.5
Sodium bridge, and fixes the older world-creation button and settings-page background. Save/quit
and bulk sky-light memory fixes from 1.0.2 are retained.
Each target has a pinned and verified NeoForge baseline in its build guide.

The new target supports **Minecraft 1.21.11 / NeoForge 21.11.48 on Java 21**; see its
[build and validation guide](platforms/neoforge-1.21.11/README.md).

This README is developer documentation for GitHub. [modrinth.md](modrinth.md) describes the
Modrinth release, while [curseforge.md](curseforge.md) is the version-independent CurseForge
project description, including its complete/no-render Additional File layout.
[PARITY.md](PARITY.md) records the feature audit against `main` commit `603f9a7`, including
the changes through 1.0.0. [PORTING.md](PORTING.md) describes the version breakpoints.

## Toolchain and builds

| Component | Configuration |
|---|---|
| Minecraft nodes | 1.16.5; 1.17–1.17.1; 1.18–1.18.2; 1.19–1.19.4; 1.20–1.20.6; 1.21–1.21.11; 26.1–26.1.2; 26.2; 26.3 |
| Gradle runtime | JDK 25; wrapper uses Gradle 9.8.0 |
| Output Java targets | 17 for 1.16.5–1.20.4; 21 for 1.20.5–1.21.11; 25 for 26.1+ |
| Fabric Loader | 0.19.5 |
| Fabric API | Pinned per release in `stonecutter.properties.toml` |
| NeoForge (26.x targets) | 26.1.0.19-beta / 26.1.1.15-beta / 26.1.2.122 / 26.2.0.93 / 26.3.0.98 (also verified with 26.3.0.99); ModDevGradle 2.0.148, Java 25 |
| NeoForge (1.21.11 target) | 21.11.48; ModDevGradle 2.0.148, Java 21 |
| Source preprocessing | Stonecutter 0.9.8 |
| Loom | `loom-back-compat` 0.4.2 selects remapping/plain Loom 1.18 |

Shared source and Fabric adapters are under `src/main/java`; NeoForge adapters are under
`platforms/neoforge/src/main/java`. Client packages and client Mixin configs remain client-only
at runtime. Stonecutter writes inactive nodes under `versions/<mc>/build/generated/stonecutter`.
Edit the root sources, not the generated copies. The active source version is 26.3.

```powershell
# Every Fabric exact-version full + no-render artifact, under build/distributions
.\gradlew.bat build

# A smaller development build (26.3 remains configured as Stonecutter's active node)
$env:SC_VERSIONS = '1.16.5,1.21.5,26.3'
.\gradlew.bat :1.16.5:build :1.21.5:build :26.3:build
.\gradlew.bat :1.21.5:runClient
Remove-Item Env:SC_VERSIONS

# Export actual Fabric/Loom launch classpaths, then run headless regressions
.\gradlew.bat writeValidationConfig
python tools/verify_matrix.py --checks --workers 2
python tools/verify_artifacts.py

# NeoForge: build both variants and run packaged client/server regressions per target
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 1.21.11
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.1.2
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.2
.\tools\neoforge-pilot.ps1 verify
```

On Linux/macOS use `./gradlew`; `SC_VERSIONS=1.21.5,26.3 ./gradlew :1.21.5:build` selects a subset.
`verify_matrix.py --versions 1.21.5,26.3` validates a subset of exported configurations.
The verifier accepts `--java` and `--javac` paths and uses Python 3.11 or newer together with the artifact auditor.
The 1.16.5 and 1.17 artifacts also require Java 17 or newer at game runtime.

Outputs are named `biblioteca-<mod-version>+<minecraft>.jar` and
`biblioteca-<mod-version>+<minecraft>-norender.jar`. The mod version comes from
`stonecutter.properties.toml`; existing older artifacts are separate files. The normal JAR works on clients and servers.
The no-render variant removes visual Mixins but retains gameplay, prediction, audio and
configuration UI. Both use the same mod ID; install only one per instance. Clients need the
normal artifact for the visual loop. Source JARs stay in each node's `build/libs` directory.

The build resolves Fabric API from its pinned published bundle. Only directly used modules form
the compile classpath; the complete bundle and every nested module form the runtime classpath.
Loom prepares those modules in each node's mappings before launch. The verifier checks that every
applicable module is present, including companion renderer Mixins. Old umbrella IDs (`fabric`)
and newer IDs (`fabric-api`) are read from the actual bundle. Dependencies are cached by Gradle.

Publishing uses the isolated Mod Publish Plugin project under `publishing/`. Run
`python tools/publish.py --versions 26.3` for a dry run, or add `--build` to build and validate
the selected targets first. See [PUBLISHING.md](PUBLISHING.md) for external credentials,
batch uploads, resume behavior and synchronization of the project description.

## Coordinate model

Stored block rows occupy `[minY, minY + height)`. In the normal Overworld this is `[-64, 320)`;
the **boundary** identifies the planes at `y = -64` and `y = 320`.
The vanilla 1.16.5/1.17 Overworld instead uses `[0, 256)`. 1.16.5 has a fixed zero floor;
from 1.17 the helpers use the level's native minimum and height.

```text
canonical row = floorMod(y - minY, height) + minY
nearest copy  = y + round((referenceY - y) / height) * height
```

`SeamPositions` provides these operations. Canonical folding keeps block access and committed
entity coordinates inside the stored period. Nearest-copy selection keeps movement requests,
draws and effects near their observer. An in-range coordinate can still need the latter when
the observer is across the boundary.

`EntityYLoopMixin` folds `setPosRaw` and shifts previous coordinates for interpolation.
`ServerMovementYLoopMixin` folds movement requests and first-good baselines before speed and
collision validation, and awards the crossing statistic. Block/fluid access, collisions, reach,
neighbour updates, pistons, plant checks and block light have hooks at their own world-edge guards.

Entity distances, reach, explosion exposure/knockback, sight rays and path targets use the nearest
periodic point or box. Near a seam, `Level` entity searches widen the candidate area and retain
only intersections in the caller's coordinate frame. Collecting searches apply their result
limit after this filter; nested queries keep their own frame. The locator bar uses periodic
distance and pitch on releases that include it.

Remote interpolation folds the living entity's stored target before 1.20.2, per-step coordinates
through 1.21.4, stored handler targets through 26.2, and
linear/stepped paths in 26.3. Buffered paths remain continuous across packets, while committed
positions and previous rendering rows stay beside each other. Spawning aligns its temporary
entity box with the surface scan. Decorations move their boxes and saved attachments together;
support checks read wrapped level rows.

## Attributes and persistence

| Key | Meaning | Default |
|---|---|---|
| `looping` | Make the dimension's Y axis periodic | false |
| `beacon_ignores_sky` | Allow a periodic beacon beam scan | false |
| `no_void_fog` | Suppress vanilla void-fog darkening in a looping dimension | false |
| `sky_light_mode` | `disabled`, `looping`, or `hybrid` sky-light sources | `looping` |

The three boolean attributes are dimension-key sets; sky modes form a dimension-to-enum map in overworld `SavedData`. 26.1+ uses
`<world>/data/biblioteca/looping.dat`; older nodes use `<world>/data/biblioteca_looping.dat`.
The older `SavedData.Factory`/NBT and newer codec storage APIs share the same codec and legacy-flag migration.
The complete snapshot is sent on join and after commands; the client clears it on disconnect.
1.16.5–1.20.4 use the legacy channel transport; later nodes use `CustomPacketPayload`/`StreamCodec`.

The world-creation GUI carries all four choices through `PendingLoopingConfig` into the new
integrated server. Commands require permission level 2 and can address modded dimension IDs:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca beacon true
/biblioteca no_void_fog true
/biblioteca sky_light hybrid
```

`no_sky_light=true` saved by previous releases migrates to `disabled`; false/absent stays
`looping`. An explicit `sky_light_mode` entry wins. The old command alias is removed: `sky_light`
uses the three mode names and offers Tab completion; without an argument it reports the mode.
`looping set` uses the native dimension argument and suggests currently loaded dimension IDs.
The earlier pre-0.67 flags still have [historical migration notes](CHANGELOG.md#manual-migration).

`CreateWorldDimensions` reads the current successful world-preset/data-pack context using each
release's native API. `DimensionConfigModel` retains drafts by dimension ID and filters removed
rows when applying. 26.3 uses the native scrolling list from main; older releases use a bounded
list with native buttons, mouse-wheel scrolling and up/down controls. Only sky-setting buttons
have tooltips. The shared footer points users to `/biblioteca` for changes during play.

## Periodic sky light and ranged interactions

`looping` supplies direct sky only in columns transparent through a full vertical circuit.
`hybrid` uses native exposure from a cell to that dimension's physical top, while propagation,
attenuation, occlusion and sampling remain periodic. `disabled` masks the channel. A complete
bedrock floor closes all direct sources in `looping`; `hybrid` retains daylight above the floor.
Dimensions without a sky channel do not gain one, and non-looping worlds keep vanilla rules.

`SkyLightEngineYLoopMixin` uses the native increase/decrease queues from 1.20 onward.
`LegacySkyLightEngineYLoopMixin` implements the same source and neighbor rules with the older
inverse-level graph. Initial light and loaded borders enter through `checkEdge`, which publishes
its work flag. The solvers preserve explicit empty-section light data for packets, clear stale
saved light on rebuild, restore native behavior when looping stops, and invalidate the opposite
seam's mesh. All writes run on the normal lighting thread; clients apply server light layers.

Ranged mob aim corrects target-minus-origin coordinates before ballistic compensation or
normalization. Version adapters cover the older scalar projectile constructors and later vector
constructors. Projectile hits use periodic candidate boxes; initial owner protection compares
nearby boxes too. Gaze cones, direct look targets and shulker homing use the same nearest-copy
rule. Leash adapters cover the old inline spring, the intermediate shared spring and modern
attachment-point forces. Ground pathfinding reads support and collisions across the bottom,
with downward scans bounded to one period.

## Renderer architecture

All versions discover real source sections whose periodic images are visible, then test each
displayed copy against the plain frustum and render distance. Snapshot lookups wrap neighbours
at the boundary, including fluid/face/AO probes; changing an edge block invalidates the opposite
edge's source geometry. Source vertex meshes are shared.

| Releases | Native submission adapter |
|---|---|
| 1.16.5 | `FixedTerrainBackend`: native fixed-function matrices and VBOs, globally sorted alpha indices |
| 1.17–1.21.4 | `ClassicTerrainBackend`: shader-based OpenGL VBOs within native material passes |
| 1.21.5 | `GpuLayerTerrainYLoopMixin`: GPU draw records inside native per-layer passes |
| 1.21.6–1.21.10 | `GpuTerrainYLoopMixin`: draw lists and relative `DynamicTransforms` |
| 1.21.11 | Same planner, absolute `ChunkSectionInfo` uniforms |
| 26.1–26.2 | Shared Uber buffers, preserved base vertices and native draw groups |
| 26.3 | `LevelRendererYLoopMixin` / `LoopingTranslucency`: RenderPearl direct/indirect draws and optional OIT |

`PeriodicTerrainPlan` and `TranslucentQuadOrder` implement independent per-copy ordering and
a global far-to-near merge. The classic backend captures centroids during vertex upload;
GPU backends use the actual source mesh's sort state. `QuadCenters` adapts both old vectors and
compressed arrays, working around the incorrect `getZ()` accessor in 1.21.9–26.1.2.

The vanilla **Improved Transparency** choice is preserved on 26.3. **On newer GPUs that support
OIT, we recommend enabling it for better performance.** Earlier versions use sorted transparency;
they do not gain a 26.3-only graphics option.

Renderer contracts:

- Sort with the camera relative to each displayed copy. Never mutate a source mesh's shared indices.
- Preserve index width, first index, base vertex and per-copy uniform bindings through native submission.
- In draw-list backends, undo the native translucent-list reversal while retaining far-first indices.
- Keep all translucent draws in one ordered group when native group iteration would scramble them.
- Read shared-buffer allocations under the dispatcher's lock and keep vanilla upload scheduling.
- Own and release only additional CPU/GPU index storage. GL replaces buffer storage; 1.21.5–26.2
  use ordered command uploads; 26.3 rotates mapped buffers with fences.

Entities, ordinary block entities and clouds use one nearest copy, matching `main`. Beacons
repeat their bounded scanned lengths. Rain/snow use boundary slices with source-world biome,
surface and light sampling. Rain impact audio, splashes, smoke, positional sounds, subtitles
and particle range tests use the same periodic coordinate model.

## Verification

`tools/verify_matrix.py --checks` starts the real Fabric transformer for each exported client/server
runtime, checks complete Fabric API module loading, loads every registered target with strict
injection counts, checks common method/field references against the actual side, bootstraps registries, and
executes the version's regression checks. It does not open a game window or render to a GPU.

Checks include three boolean sets and the sky-mode map, legacy-save migration, defaults,
pending/client cache lifecycle, native command completion and live dimension-model changes,
coordinate folding, transformed entity searches and result limits, continuous interpolation,
spawn/path surfaces, path-node positions and decoration anchors in 16- and 384-row test levels (16/256 with floor zero on 1.16.5).
`SkyChecks` runs native light engines on every node: all three modes, saved-light replacement,
block changes, source removal, both seam directions, client layers and neighboring/reloaded
chunks. `LightSyncChecks` runs native chunk light-packet broadcasting on every node, checking
interior and border recipients, mode restoration, newly created holders and server cleanup.
The 26.3 node additionally runs the relocated main 1.0.0 suite in `tools/validation/main100`,
including native leash ticks, full walking paths, ranged mob attacks, water-vertex samples and
67-row GUI layouts. Older nodes retain native source/injection/API coverage for those gameplay
adapters; the complete 26.3 fixture is not presented as an older-version gameplay test.
Rendering checks cover weather slice coverage, centroid formats, exhaustive reference alpha ordering,
16/32-bit indices and copy visibility. GPU draw records are inspected for buffer/uniform/address
preservation; 26.3 also exercises direct/indirect command data, OIT selection and buffer/fence cleanup
using in-memory devices. `tools/verify_artifacts.py` audits all 70 distributable JARs, their exact
Minecraft requirements, class versions, access wideners, refmaps and no-render contents.

Reports live in `build/validation`; CI uploads them with `build/distributions`. Gameplay, audio
output and driver-specific rendering still need an interactive client: see the manual scenarios
in [PARITY.md](PARITY.md). Passing data and injection checks is not a claim of visual GPU testing.

`tools/verify_neoforge_save.py --loader fabric` also runs the packaged Fabric 26.3 JAR through
real server save/stop/reopen, with the same checks used by NeoForge. Pass the exported runtime
configuration, JAR and a world template; the runner copies the template into `build/` and uses
a 1 GB heap. `--hybrid-dig` adds three isolated 8x8 excavation/roof scenarios. Full-height walls
prevent surrounding water in player worlds from invalidating the test's direct-sky assumptions.
The shutdown observer runs after default-order TAIL callbacks to avoid depending on Fabric's
event/Mixin ordering.

A real Fabric 1.16.5 client also reproduced stale sky light after enabling looping in a copied
world: 74 of 294 sampled positions disagreed with the integrated server, concentrated at Y=80
and Y=96. With the synchronization fix included in 1.0.3, all 294 client/server samples matched across
Y=80, 96, 112, 128, 160 and 240. This checks live light values, not screenshot appearance.
The existing 1.0.2 artifacts retain the old behavior on pre-26.1 targets.

`tools/verify_sodium116.py` launches a real Fabric 1.16.5 client with Sodium 0.2.0+build.4 on a
copied world. It records native drawing and shifted model origins, tests boundary block/light
snapshots and invalidation, and saves frames for ordinary terrain, looping terrain, the upper
periodic bedrock layer and restored ordinary rendering. Supply `--sodium-jar`, `--world-template`,
`--assets-dir` and optionally the remapped Biblioteca `--jar`; `--oneshot` selects Sodium's other
drawing backend. First export `:1.16.5:writeValidationConfig`. Reports, worlds and screenshots
stay under `build/validation/1.16.5/sodium/`.

`tools/verify_world_creation.py` checks the real creation screen on every supported version from
1.16.5 through 1.20.4. The classic layout ends at 1.19.3; 1.19.4 introduces the Game/World/More
tabs. The checks verify native button alignment, visibility when changing pages, no overlapping
controls, entry into dimension settings, retained choices after returning, GUI scales 3 and 4,
and the classic flat-world customization layout. A framebuffer check also verifies that the
settings page repaints its background after the previous frame is cleared; screenshots of the
settings page must be reviewed alongside the entry-page screenshots. Supply `--mod-version` and `--artifact-dir` for
the built JARs; `--versions` selects a subset. Matching launcher assets can be reused with
`--assets-dir`; otherwise prepare each node's `downloadAssets` task. Each client runs in a separate
directory and stops after exercising the menus. Reports and frames are under
`build/validation/world-creation/`.

`tools/verify_visibility.py` runs a real 26.3 graphical client on a copied world. It tests 74
camera/FOV poses, checks compiled bottom-section copies against submitted solid draws, verifies
the actual pose and captures six screenshots. Both loader adapters use the same world and
settings, with dynamic FOV disabled. This diagnostic is limited to the selected graphics device
and draw path. Reports and screenshots are written under `build/validation/26.3/visibility/`.

## Compatibility and documentation

The Fabric 1.16.5 build includes an optional bridge for **Sodium 0.2.0+build.4**.
It preserves Sodium's native buffers, shaders and drawing backends while adding periodic terrain
copies and boundary snapshots. Sodium's native section-based transparency behavior remains in
use; this is separate from the vanilla renderer's quad sorting. The bridge is excluded from
no-render builds. Compatibility with other Sodium versions is not implied by this 1.16.5 adapter.

Voxy and other far-LOD/replacement renderers bypass vanilla terrain extraction and need a separate
integration. That is a shared compatibility limitation of `main` and this branch. Shader packs
have not been comprehensively tested. Conventional polygon sorting retains artifacts with
intersecting faces and cyclic overlap.

- [PORTING.md](PORTING.md): version boundaries and implementation decisions.
- [PARITY.md](PARITY.md): feature-by-feature audit and per-version validation.
- [PERFORMANCE.md](PERFORMANCE.md): cost model and profiling scenarios.
- [modrinth.md](modrinth.md): Modrinth user copy covering installation, settings and commands.
- [curseforge.md](curseforge.md): version-independent CurseForge copy with both variants in one project.
- [CHANGELOG.md](CHANGELOG.md): release history and manual setting migration.

Keep implementation details and verification evidence in the GitHub developer documentation.
Keep private handoff notes, machine paths and installation records in `.local/` or the ignored
local checkpoint files. Generated runtime configurations, worlds, screenshots, logs and backups
stay outside version control; commit portable validation tools and sanitized technical summaries.
Use complete HTTPS URLs for every hyperlink and image in the platform descriptions so their
Markdown works outside GitHub. Repository-relative links are suitable here.

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra. Licensed under [GPLv3](LICENSE).

# Biblioteca

**A periodic Y axis for Minecraft dimensions, with version-specific vanilla renderer adapters.**

Biblioteca **0.676.7** connects a dimension's top and bottom. The server stores one period;
the client displays the visible periodic copies. No blocks, items, dimensions or generation
presets are added, and existing terrain (including bedrock) is retained.

`main` is the clean **Minecraft 26.3** implementation. `multiversion` adapts that implementation
to **all 35 releases from 1.16.5 through 26.3**. Each release has an exact-version artifact; a JAR
compiled for one release is not relabelled to accept a broader API range.

This README covers development. [modrinth.md](modrinth.md) is the player-facing project page.
[PARITY.md](PARITY.md) records the feature audit against `main` commit `844eb61`, including
the changes through 0.676.7. [PORTING.md](PORTING.md) describes the version breakpoints.

## Toolchain and builds

| Component | Configuration |
|---|---|
| Minecraft nodes | 1.16.5; 1.17–1.17.1; 1.18–1.18.2; 1.19–1.19.4; 1.20–1.20.6; 1.21–1.21.11; 26.1–26.1.2; 26.2; 26.3 |
| Gradle runtime | JDK 25; wrapper uses Gradle 9.8.0 |
| Output Java targets | 17 for 1.16.5–1.20.4; 21 for 1.20.5–1.21.11; 25 for 26.1+ |
| Fabric Loader | 0.19.5 |
| Fabric API | Pinned per release in `stonecutter.properties.toml` |
| Source preprocessing | Stonecutter 0.9.8 |
| Loom | `loom-back-compat` 0.4.2 selects remapping/plain Loom 1.18 |

All source is under `src/main/java`; client packages and client Mixin configs remain client-only
at runtime. Stonecutter writes inactive nodes under `versions/<mc>/build/generated/stonecutter`.
Edit the root sources, not the generated copies. The active source version is 26.3.

```powershell
# Every exact-version full + no-render artifact, under build/distributions
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
```

On Linux/macOS use `./gradlew`; `SC_VERSIONS=1.21.5,26.3 ./gradlew :1.21.5:build` selects a subset.
`verify_matrix.py --versions 1.21.5,26.3` validates a subset of exported configurations.
The verifier accepts `--java` and `--javac` paths and uses Python 3.11 or newer together with the artifact auditor.
The 1.16.5 and 1.17 artifacts also require Java 17 or newer at game runtime.

Outputs are named `biblioteca-0.676.7+<minecraft>.jar` and
`biblioteca-0.676.7+<minecraft>-norender.jar`. The normal JAR works on clients and servers.
The no-render variant removes visual Mixins but retains gameplay, prediction, audio and
configuration UI. Both use the same mod ID; install only one per instance. Clients need the
normal artifact for the visual loop. Source JARs stay in each node's `build/libs` directory.

The build resolves Fabric API from its pinned published bundle. Only directly used modules form
the compile classpath; the complete bundle and every nested module form the runtime classpath.
Loom prepares those modules in each node's mappings before launch. The verifier checks that every
applicable module is present, including companion renderer Mixins. Old umbrella IDs (`fabric`)
and newer IDs (`fabric-api`) are read from the actual bundle. Dependencies are cached by Gradle.

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

| Key | Meaning when true | Default |
|---|---|---|
| `looping` | Make the dimension's Y axis periodic | false |
| `beacon_ignores_sky` | Allow a periodic beacon beam scan | false |
| `no_void_fog` | Suppress vanilla void-fog darkening in a looping dimension | false |
| `no_sky_light` | Suppress the vanilla sky-light channel in a looping dimension | false |

These are dimension-key sets in overworld `SavedData`. 26.1+ uses
`<world>/data/biblioteca/looping.dat`; older nodes use `<world>/data/biblioteca_looping.dat`.
The older `SavedData.Factory`/NBT and newer codec storage APIs share the four-field codec.
The complete snapshot is sent on join and after commands; the client clears it on disconnect.
1.16.5–1.20.4 use the legacy channel transport; later nodes use `CustomPacketPayload`/`StreamCodec`.

The world-creation GUI carries all four choices through `PendingLoopingConfig` into the new
integrated server. Commands require permission level 2 and can address modded dimension IDs:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca beacon true
/biblioteca no_void_fog true
/biblioteca no_sky_light false
```

Old lighting flags are not automatically migrated. See [manual migration](CHANGELOG.md#manual-migration).

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

Checks include all four saved/payload fields and false defaults, pending/client cache lifecycle,
coordinate folding, transformed entity searches and result limits, continuous interpolation,
spawn/path surfaces, path-node positions and decoration anchors in 16- and 384-row test levels (16/256 with floor zero on 1.16.5).
Rendering checks cover weather slice coverage, centroid formats, exhaustive reference alpha ordering,
16/32-bit indices and copy visibility. GPU draw records are inspected for buffer/uniform/address
preservation; 26.3 also exercises direct/indirect command data, OIT selection and buffer/fence cleanup
using in-memory devices. `tools/verify_artifacts.py` audits all 70 distributable JARs, their exact
Minecraft requirements, class versions, access wideners, refmaps and no-render contents.

Reports live in `build/validation`; CI uploads them with `build/distributions`. Gameplay, audio
output and driver-specific rendering still need an interactive client: see the manual scenarios
in [PARITY.md](PARITY.md). Passing data and injection checks is not a claim of visual GPU testing.

## Compatibility and documentation

Voxy and other far-LOD/replacement renderers bypass vanilla terrain extraction and need a separate
integration. That is a shared compatibility limitation of `main` and this branch. Shader packs
have not been comprehensively tested. Conventional polygon sorting retains artifacts with
intersecting faces and cyclic overlap.

- [PORTING.md](PORTING.md): version boundaries and implementation decisions.
- [PARITY.md](PARITY.md): feature-by-feature audit and per-version validation.
- [PERFORMANCE.md](PERFORMANCE.md): cost model and profiling scenarios.
- [modrinth.md](modrinth.md): player installation, settings and commands.
- [CHANGELOG.md](CHANGELOG.md): release history and manual setting migration.

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra. Licensed under [GPLv3](LICENSE).

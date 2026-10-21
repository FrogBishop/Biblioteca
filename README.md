# Biblioteca

**A periodic Y axis for Minecraft dimensions.**

Biblioteca **0.67.0** is a Fabric mod for Minecraft 26.3. It makes a dimension's vertical
coordinate periodic: the boundary at `minY` is the same place as `minY + height`. The server
stores one period, and the client renders visible copies above and below it.

The mod adds no blocks, items, dimensions or world-generation presets. Looping is an opt-in
attribute of an existing dimension. Terrain generation, including bedrock, is retained.

This README covers the implementation and development workflow. [modrinth.md](modrinth.md)
contains the player-facing project description, setup instructions and command reference.

## Toolchain

| Component | Project configuration |
|---|---|
| Minecraft | 26.3 |
| Java | 25 or newer; compiled with `--release 25` |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.162.0+26.3 |
| Fabric Loom | 1.18-SNAPSHOT |
| Gradle | 9.7.1, via the wrapper |

Dependency and mod versions are declared in [gradle.properties](gradle.properties).
Loom separates common code in `src/main` from client code in `src/client`.

## Build and run

With Java 25 available to Gradle, run these commands from the repository root:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

On Linux or macOS, use `./gradlew` for the same tasks. `build` compiles both environments,
validates the access widener and creates these artifacts in `build/libs`:

| Artifact | Purpose |
|---|---|
| `biblioteca-0.67.0.jar` | Complete mod for clients and servers |
| `biblioteca-0.67.0-norender.jar` | Variant intended for dedicated servers |
| `biblioteca-0.67.0-sources.jar` | Common and client source code |

The no-render variant is derived from the primary JAR by removing the visual Mixin hooks and
rewriting its manifest. It retains server logic, networking, client prediction, audio and
configuration UI. Both variants have the same mod ID: install only one per game instance.
Clients need the complete mod to see the periodic world. Use matching mod versions on both sides.

For local deployment:

```powershell
.\gradlew.bat deployMod "-PmodsDir=D:/Minecraft/TestInstance/mods"
```

Without `modsDir`, the destination is
`%APPDATA%/.minecraft/versions/26.3-Fabric 0.19.5/mods` on Windows. `deployMod` builds and copies
the normal JAR, removing older `biblioteca-*.jar` files from that destination.

## Coordinate model

Stored block rows occupy `[minY, minY + height)`. For the normal Overworld, the stored rows
are `-64` through `319`; the boundary identifies the planes at `y = -64` and `y = 320`.

Two operations serve different purposes:

```text
canonical row = floorMod(y - minY, height) + minY
nearest copy  = y + round((referenceY - y) / height) * height
```

Canonical folding keeps block access and committed entity positions inside the stored period.
Nearest-copy selection places movement requests, rendered objects and effects beside their
observer. A coordinate already inside the world can still need the second operation when the
observer is across the boundary. Shared helpers live in
[SeamPositions.java](src/main/java/babel/biblioteca/SeamPositions.java).

`EntityYLoopMixin` folds positions at `Entity.setPosRaw` and shifts previous positions by the
same period for interpolation. Movement packet handlers separately select the nearest copy
before vanilla validates movement. Block access, collisions, reach, neighbour updates, pistons,
plant checks and block-light propagation have their own hooks where vanilla assumes a world edge.

Distances and search areas use the same two operations. `Entity.distanceToSqr` and the helpers
behind it compare with the copy of the other point nearest the entity, which is what makes despawn,
nearest-player selection and AI reach work at the boundary. `Level.getEntities` widens its box by
one world height when the box comes within reach of a seam, then keeps only the entities whose ring
position is inside the original box, so a search still means "what is in this box". Line of sight,
path nodes, look targets, interaction points, interpolation targets and explosion samples are placed
on the nearest copy the same way.

## Source map

| Area | Main code |
|---|---|
| Initialization, commands and state broadcasts | [Biblioteca](src/main/java/babel/biblioteca/Biblioteca.java) |
| Attribute queries and per-level cache invalidation | [LoopingRegistry](src/main/java/babel/biblioteca/LoopingRegistry.java), [LevelLoopingCacheMixin](src/main/java/babel/biblioteca/mixin/LevelLoopingCacheMixin.java) |
| World persistence and server snapshots | [world package](src/main/java/babel/biblioteca/world) |
| State payload and client cache | [LoopingStatePayload](src/main/java/babel/biblioteca/network/LoopingStatePayload.java), [LoopingDimensionCache](src/client/java/babel/biblioteca/client/LoopingDimensionCache.java) |
| World-creation choices | [PendingLoopingConfig](src/main/java/babel/biblioteca/PendingLoopingConfig.java), [LoopingConfigScreen](src/client/java/babel/biblioteca/client/LoopingConfigScreen.java) |
| Terrain source discovery and copies | [SectionOcclusionGraphLoopMixin](src/client/java/babel/biblioteca/client/mixin/SectionOcclusionGraphLoopMixin.java), [LevelRendererYLoopMixin](src/client/java/babel/biblioteca/client/mixin/LevelRendererYLoopMixin.java) |
| Transparency without OIT | [LoopingTranslucency](src/client/java/babel/biblioteca/client/LoopingTranslucency.java), [TranslucentQuadOrder](src/client/java/babel/biblioteca/client/TranslucentQuadOrder.java) |
| Weather geometry and impacts | [LoopingWeather](src/client/java/babel/biblioteca/client/LoopingWeather.java), [WeatherEffectRendererYLoopMixin](src/client/java/babel/biblioteca/client/mixin/WeatherEffectRendererYLoopMixin.java), [ClientLevelWeatherYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ClientLevelWeatherYLoopMixin.java) |
| Sound, particles and subtitles | [SeamEffects](src/main/java/babel/biblioteca/SeamEffects.java), [ServerLevelEffectsYLoopMixin](src/main/java/babel/biblioteca/mixin/ServerLevelEffectsYLoopMixin.java), [LoopingSounds](src/client/java/babel/biblioteca/client/LoopingSounds.java) |
| Cross-seam reach, sight and interaction | [PlayerEntityInteractionMixin](src/main/java/babel/biblioteca/mixin/PlayerEntityInteractionMixin.java), [AttackRangeYLoopMixin](src/main/java/babel/biblioteca/mixin/AttackRangeYLoopMixin.java), [LivingEntitySightYLoopMixin](src/main/java/babel/biblioteca/mixin/LivingEntitySightYLoopMixin.java), [PlayerInteractionLocationYLoopMixin](src/main/java/babel/biblioteca/mixin/PlayerInteractionLocationYLoopMixin.java), [LookControlYLoopMixin](src/main/java/babel/biblioteca/mixin/LookControlYLoopMixin.java) |
| Entity searches, AI and pathfinding | [EntitySearchYLoopMixin](src/main/java/babel/biblioteca/mixin/EntitySearchYLoopMixin.java), [EntityDistanceYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityDistanceYLoopMixin.java), [MobMeleeRangeYLoopMixin](src/main/java/babel/biblioteca/mixin/MobMeleeRangeYLoopMixin.java), [PathNavigationRegionYLoopMixin](src/main/java/babel/biblioteca/mixin/PathNavigationRegionYLoopMixin.java), [PathNavigationYLoopMixin](src/main/java/babel/biblioteca/mixin/PathNavigationYLoopMixin.java), [PathYLoopMixin](src/main/java/babel/biblioteca/mixin/PathYLoopMixin.java), [GroundPathNavigationYLoopMixin](src/main/java/babel/biblioteca/mixin/GroundPathNavigationYLoopMixin.java) |
| Remote entity movement | [EntityInterpolationYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityInterpolationYLoopMixin.java), [ClientPacketListenerYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ClientPacketListenerYLoopMixin.java) |
| Spawning, decorations, falling blocks and explosions | [EntityTypeSpawnYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityTypeSpawnYLoopMixin.java), [HangingEntityYLoopMixin](src/main/java/babel/biblioteca/mixin/HangingEntityYLoopMixin.java), [FallingBlockEntityYLoopMixin](src/main/java/babel/biblioteca/mixin/FallingBlockEntityYLoopMixin.java), [ServerExplosionYLoopMixin](src/main/java/babel/biblioteca/mixin/ServerExplosionYLoopMixin.java) |
| Client pick, name distance and locator bar | [ProjectileUtilYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ProjectileUtilYLoopMixin.java), [EntityRenderDispatcherYLoopMixin](src/client/java/babel/biblioteca/client/mixin/EntityRenderDispatcherYLoopMixin.java), [TrackedWaypointYLoopMixin](src/client/java/babel/biblioteca/client/mixin/TrackedWaypointYLoopMixin.java) |

## Attributes and persistence

Attributes are dimension-key sets in the overworld's `SavedData`, stored at
`<world>/data/biblioteca/looping.dat`. Absence from a set means `false`.

| Saved key | Meaning when `true` | Default |
|---|---|---|
| `looping` | Make the dimension's Y axis periodic | `false` |
| `beacon_ignores_sky` | Allow the looping dimension's beacons to use a periodic beam scan | `false` |
| `no_void_fog` | Suppress vanilla void-fog darkening in a looping dimension | `false` |
| `no_sky_light` | Suppress the vanilla sky-light channel in a looping dimension | `false` |

The server sends all four sets on player join and after command changes. Clients clear their
cache on disconnect. `LoopingRegistry` routes queries to the appropriate side and publishes a
revision for cached looping-state checks. The world-creation screen passes its choices through
`PendingLoopingConfig` to the integrated server on world load.

Commands require permission level 2. A minimal development setup is:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca beacon true
```

The GUI lists the three vanilla dimensions; commands also accept modded dimension IDs.
The lighting attributes use the inverse of the old `void_fog` / `sky_light` meaning. See the
[0.67.0 migration notes](CHANGELOG.md#manual-migration) before comparing older saves.

## Rendering

Terrain copies share the source section's vertex mesh. The section graph supplies source
sections whose periodic copies are visible, and copy submission tests their actual bounds
against the frustum and render distance. Ordinary entities, block entities and clouds use the
nearest copy. Beacon beams repeat by period, with a bounded copy count. Weather columns are
split at the boundary and use source coordinates for terrain, biome and light sampling.

The vanilla **Improved Transparency** option controls OIT. **On newer GPUs that support OIT,
we recommend enabling it for better performance.** Biblioteca never forces the option.

When OIT is off, each visible translucent copy has its own quad order. Orders are merged across
sections and periods, and consecutive compatible draws are grouped. Vertex buffers stay shared;
16-bit and 32-bit indices are written into rotating, fenced buffers. The direct and indirect
submission paths consume the same ordering.

Ordering details that matter when changing the renderer:

- Capture the source `SortState` while vanilla holds the section dispatcher's buffer lock.
- Sort against the camera relative to the displayed copy, not the source section's original row.
- Vanilla reverses translucent groups and their draw lists. The replacement containers must be
  near-first while each draw's index range remains far-first.
- Rotate index buffers after frame submission and release them when the world/render data resets
  or the ordered path stops being used.

[biblioteca.accesswidener](src/main/resources/biblioteca.accesswidener) exposes the vanilla
draw-group type and constructor needed to build those batches. The renderer targets Minecraft
26.3's official class names and signatures. Sorted transparency retains the limitations of
polygon sorting for intersecting faces and cyclic overlap.

See [PERFORMANCE.md](PERFORMANCE.md) for costs, buffer ownership and profiling scenarios.
The mod uses vanilla renderer internals; replacement renderers and far-LOD systems may need a
separate integration. Shader packs have not been comprehensively tested.

## Development checks

Mixin configs are required and use `defaultRequire: 1`. A build validates compilation and the
access widener; launch a development client and server to check injection compatibility after
changing Minecraft targets. Useful regression scenarios include:

- Enter and leave a looping dimension, toggle its attributes and reconnect as a guest.
- Walk, fly and ride through both sides of the boundary; place and break blocks there.
- Check fluid surfaces, light sources, pistons, plants and beacons at the boundary.
- Compare water, stained glass and slime with Improved Transparency on and off, including a
  short dimension with several periods visible at once.
- Check rain/snow, rain impacts, local and entity sounds, subtitles and particle effects while
  standing near the boundary and while crossing it.
- Watch a second player cross the boundary: name tag, movement, locator bar, and interaction
  with a villager or an item frame from the other side.
- Let a mob acquire a target across the boundary, path to it and land the hit.
- Grow a sapling on the top row with and without `no_sky_light`, and break a block on either seam
  row to confirm the newly exposed face appears immediately.
- Detonate a charge on either seam row and check that the entity on the other side is damaged and
  thrown away from the blast, not straight up.
- Inspect both JAR variants: every registered Mixin must have its class, and the no-render
  variant must omit the visual hooks listed in `build.gradle`.

## Documentation and license

- [Modrinth project-page text](modrinth.md) — player-facing features, settings and commands.
- [Performance notes](PERFORMANCE.md) — renderer costs and profiling guidance.
- [Changelog](CHANGELOG.md) — releases and manual migration instructions.
- [Issue tracker](https://github.com/FrogBishop/Biblioteca/issues).

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra. Licensed under [GPLv3](LICENSE).

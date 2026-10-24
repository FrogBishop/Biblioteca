# Biblioteca

**A periodic Y axis for Minecraft dimensions.**

Biblioteca **1.0.0** is a Fabric mod for Minecraft 26.3. It makes a dimension's vertical
coordinate periodic: the boundary at `minY` is the same place as `minY + height`. The server
stores one period, and the client renders visible copies above and below it.

The mod adds no blocks, items, dimensions or world-generation presets. Looping is an opt-in
attribute of an existing dimension. Terrain generation, including bedrock, is retained.

This is the developer README for GitHub: implementation, build configuration and verification.
[modrinth.md](modrinth.md) is the shared user-facing description for Modrinth and CurseForge,
covering features, installation, dimension settings and commands.

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
| `biblioteca-1.0.0.jar` | Complete mod for clients and servers |
| `biblioteca-1.0.0-norender.jar` | Variant intended for dedicated servers |
| `biblioteca-1.0.0-sources.jar` | Common and client source code |

The no-render variant is derived from the primary JAR by removing the visual Mixin hooks and
rewriting its manifest. It retains server logic, networking, client prediction, audio and
configuration UI. Both variants have the same mod ID: install only one per game instance.
Clients need the complete mod to see the periodic world. Use matching mod versions on both sides.
The version in `gradle.properties` supplies the normal manifest; the no-render task appends
`-norender` to its manifest version, so crash reports identify the variant as `1.0.0-norender`.

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
plant checks, decoration support scans, spawn surface queries and block-light propagation have
their own hooks where vanilla assumes a world edge.

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
| Sky-light modes, sources and propagation | [SkyLightMode](src/main/java/babel/biblioteca/SkyLightMode.java), [SkyLightEngineYLoopMixin](src/main/java/babel/biblioteca/mixin/SkyLightEngineYLoopMixin.java), [SkyLightStorageYLoopMixin](src/main/java/babel/biblioteca/mixin/SkyLightStorageYLoopMixin.java) |
| Terrain source discovery and copies | [SectionOcclusionGraphLoopMixin](src/client/java/babel/biblioteca/client/mixin/SectionOcclusionGraphLoopMixin.java), [LevelRendererYLoopMixin](src/client/java/babel/biblioteca/client/mixin/LevelRendererYLoopMixin.java) |
| Transparency without OIT | [LoopingTranslucency](src/client/java/babel/biblioteca/client/LoopingTranslucency.java), [TranslucentQuadOrder](src/client/java/babel/biblioteca/client/TranslucentQuadOrder.java) |
| Weather geometry and impacts | [LoopingWeather](src/client/java/babel/biblioteca/client/LoopingWeather.java), [WeatherEffectRendererYLoopMixin](src/client/java/babel/biblioteca/client/mixin/WeatherEffectRendererYLoopMixin.java), [ClientLevelWeatherYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ClientLevelWeatherYLoopMixin.java) |
| Sound, particles and subtitles | [SeamEffects](src/main/java/babel/biblioteca/SeamEffects.java), [ServerLevelEffectsYLoopMixin](src/main/java/babel/biblioteca/mixin/ServerLevelEffectsYLoopMixin.java), [LoopingSounds](src/client/java/babel/biblioteca/client/LoopingSounds.java) |
| Cross-seam reach, sight and interaction | [PlayerEntityInteractionMixin](src/main/java/babel/biblioteca/mixin/PlayerEntityInteractionMixin.java), [AttackRangeYLoopMixin](src/main/java/babel/biblioteca/mixin/AttackRangeYLoopMixin.java), [LivingEntitySightYLoopMixin](src/main/java/babel/biblioteca/mixin/LivingEntitySightYLoopMixin.java), [PlayerInteractionLocationYLoopMixin](src/main/java/babel/biblioteca/mixin/PlayerInteractionLocationYLoopMixin.java), [LookControlYLoopMixin](src/main/java/babel/biblioteca/mixin/LookControlYLoopMixin.java) |
| Entity searches, AI and pathfinding | [EntitySearchYLoopMixin](src/main/java/babel/biblioteca/mixin/EntitySearchYLoopMixin.java), [EntityDistanceYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityDistanceYLoopMixin.java), [MobMeleeRangeYLoopMixin](src/main/java/babel/biblioteca/mixin/MobMeleeRangeYLoopMixin.java), [PathNavigationRegionYLoopMixin](src/main/java/babel/biblioteca/mixin/PathNavigationRegionYLoopMixin.java), [PathNavigationYLoopMixin](src/main/java/babel/biblioteca/mixin/PathNavigationYLoopMixin.java), [PathYLoopMixin](src/main/java/babel/biblioteca/mixin/PathYLoopMixin.java), [GroundPathNavigationYLoopMixin](src/main/java/babel/biblioteca/mixin/GroundPathNavigationYLoopMixin.java) |
| Remote entity movement | [EntityInterpolationYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityInterpolationYLoopMixin.java), [ClientPacketListenerYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ClientPacketListenerYLoopMixin.java) |
| Mob aim, gaze and projectile hits | [RangedAttackYLoopMixin](src/main/java/babel/biblioteca/mixin/RangedAttackYLoopMixin.java), [SnowGolemAimYLoopMixin](src/main/java/babel/biblioteca/mixin/SnowGolemAimYLoopMixin.java), [ProjectileHitYLoopMixin](src/main/java/babel/biblioteca/mixin/ProjectileHitYLoopMixin.java), [LivingEntitySightYLoopMixin](src/main/java/babel/biblioteca/mixin/LivingEntitySightYLoopMixin.java) |
| Spawning, decorations, falling blocks and explosions | [EntityTypeSpawnYLoopMixin](src/main/java/babel/biblioteca/mixin/EntityTypeSpawnYLoopMixin.java), [HangingEntityYLoopMixin](src/main/java/babel/biblioteca/mixin/HangingEntityYLoopMixin.java), [FallingBlockEntityYLoopMixin](src/main/java/babel/biblioteca/mixin/FallingBlockEntityYLoopMixin.java), [ServerExplosionYLoopMixin](src/main/java/babel/biblioteca/mixin/ServerExplosionYLoopMixin.java) |
| Client pick, name distance and locator bar | [ProjectileUtilYLoopMixin](src/client/java/babel/biblioteca/client/mixin/ProjectileUtilYLoopMixin.java), [EntityRenderDispatcherYLoopMixin](src/client/java/babel/biblioteca/client/mixin/EntityRenderDispatcherYLoopMixin.java), [TrackedWaypointYLoopMixin](src/client/java/babel/biblioteca/client/mixin/TrackedWaypointYLoopMixin.java) |

## Attributes and persistence

Attributes live in the overworld's `SavedData` at `<world>/data/biblioteca/looping.dat`.
Boolean attributes use dimension-key sets; sky light uses a dimension-to-mode map.

| Saved key | Meaning | Default |
|---|---|---|
| `looping` | Make the dimension's Y axis periodic | `false` |
| `beacon_ignores_sky` | Allow the looping dimension's beacons to use a periodic beam scan | `false` |
| `no_void_fog` | Suppress vanilla void-fog darkening in a looping dimension | `false` |
| `sky_light_mode` | Sky-light source rule: `disabled`, `looping`, or `hybrid` | `looping` |

The server sends the three sets and sky-mode map on player join and after command changes.
Clients clear their cache on disconnect. `LoopingRegistry` routes queries to the appropriate side and publishes a
revision for cached looping-state checks. The world-creation screen passes its choices through
`PendingLoopingConfig` to the integrated server on world load.

Commands require permission level 2. A minimal development setup is:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca beacon true
/biblioteca sky_light hybrid
```

The GUI lists the dimensions from the selected world preset and currently enabled data packs,
including custom dimensions. It follows the latest successful pack reload, keeps edits attached
to dimension IDs when the list changes, and scrolls when more rows are available. Custom rows
show their full namespaced ID; hover a row name to inspect its ID. The **Sky light**
button cycles **Periodic → Vanilla sky → Disabled** and explains the current choice in its tooltip.

After `/biblioteca looping set `, Tab completion offers the dimensions currently loaded by the
server, including custom dimensions. Partial names and namespaces use vanilla completion rules.
`/biblioteca sky_light <disabled|looping|hybrid>` changes the current dimension's sky mode and
supports Tab completion. With no mode it reports the current setting. It replaces the old
`no_sky_light` command and takes mode names instead of `true` or `false`.

Old saved `no_sky_light=true` becomes `disabled`; false/absent becomes `looping`. An explicit new
mode takes precedence over a legacy disabled entry. For worlds predating 0.67.0, see the historical
[migration notes](CHANGELOG.md#manual-migration); use the current mode names for sky-light commands.

## Sky light in a looping dimension

| Mode | Direct sky rule |
|---|---|
| `disabled` | No sky light; block light sources still work. |
| `looping` (default) | A column supplies level-15 direct sky only when the entire vertical circuit is transparent, including the bottom/top edge. |
| `hybrid` | Vanilla exposure supplies direct sky: from this cell to the dimension's top must be unobstructed. The Overworld top is y=319; custom dimensions use their own height. |

Both lit modes propagate light through periodic neighbours with native opacity and face occlusion.
Diffuse light loses at least one level per step in either vertical direction. Queries above the
last stored row sample the wrapped bottom row, including water vertices and entity/block light
checks; the query itself never invents full-bright sky outside the world.

A complete bedrock floor closes every column in `looping` mode. Use `hybrid` to keep normal sky
illumination above that floor while still wrapping light across the seam, or open a transparent
full-period column in `looping` mode. Dimensions without a native sky-light channel do not gain
one. Non-looping dimensions retain vanilla sky light regardless of the saved mode.

Loaded chunks rebuild their saved sky light when looping starts/stops or the mode changes.
Lighting updates and light packets use the native engine, including explicit light layers for empty sections, and
changes invalidate the mesh on both sides of the seam. A client attribute change also rebuilds
baked terrain light. Existing saves do not need their light data deleted manually.

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
access widener. With JDK 25 and Python 3 available, run the headless seam regressions with:

```powershell
.\gradlew.bat build verifySeams --no-configuration-cache
```

This loads every registered Mixin target through the real Fabric transformer in both client and
server environments, then exercises native leash ticks, bidirectional navigation, repeated
position writes, player/mob interpolation and entity/leash render states. It covers 16- and
384-row worlds, ordinary dimensions, genuine leash breaks and unsafe falls. Logs and a JSON
report are saved under `build/seam-regression`. The checks use in-memory world fixtures; launch
a development client and server to verify the final graphics and multiplayer behavior.
The same task checks dimension command completion, live creation-context changes, attribute
preservation and scrolling/button placement with 67 dimensions at several GUI sizes. Sky-light
checks run the native height maps, light queues and water-vertex samplers: full-height sources,
water/glass/opaque occlusion, both seam directions, light removal, chunk loading/reloading,
new-chunk initialization, saved vanilla light, client packet layers and looping toggles. Block
light propagation/removal is checked alongside sky light.
Mob checks exercise native nearest-target goals, line-of-sight block rays, eye-based gaze tests,
projectile creation/aiming and entity hit detection. Seam trajectories are compared with ordinary
shots at the same local displacement, including ballistic compensation in short dimensions.
Useful regression scenarios include:

- Enable and disable a data pack containing custom dimensions, then open Dimension Attributes.
  Confirm the rows match the active preset/packs and all four attributes save for a custom row.
- Type `/biblioteca looping set ` and a partial dimension ID; confirm Tab offers the loaded
  custom dimensions in both singleplayer and multiplayer.
- Enter and leave a looping dimension, toggle its attributes and reconnect as a guest.
- Walk, fly and ride through both sides of the boundary; place and break blocks there.
- Check fluid surfaces, light sources, pistons, plants and beacons at the boundary.
- Compare water at y=319 and the wrapped bottom with sky light enabled. Open and close a full-height
  transparent column, cycle all three sky modes, and reload a saved world to check light updates.
  A complete bedrock floor blocks direct sky in `looping` mode; `hybrid` keeps the sky above it.
- Compare water, stained glass and slime with Improved Transparency on and off, including a
  short dimension with several periods visible at once.
- Check rain/snow, rain impacts, local and entity sounds, subtitles and particle effects while
  standing near the boundary and while crossing it.
- Watch a second player and a mob cross the boundary: name tag, movement, locator bar, and
  interaction with a villager or an item frame from the other side.
- Let a mob acquire a target across the boundary, path up and down a staircase through both
  seam directions, and land the hit.
- Lead a mob through both seam directions. Check that the rope remains short and attached,
  that the mob follows toward the nearby holder, and that pulling genuinely too far breaks it.
- Put a snow golem or skeleton at y=319 with a visible target nearby, then on the opposite side
  of the seam. Check target acquisition, shot direction, projectile impact and wall occlusion.
  Repeat with thrown tridents/potions/spit, mob crossbows, fireballs, wind charges and shulker
  bullets. A shot spawned across the seam must retain its initial protection against its owner.
- Look at an enderman across the seam, then turn away or put a wall between the eye positions;
  the gaze cone and the sight ray must agree. Check the multiple gaze heights used by creakings.
- Place a two-block-high painting at the top row and check that it occupies the top row and the
  row below the world, then place a taller one that spans several rows of the ring.
- Use a spawn egg on a block one row below the top row and on the top row itself; the mob should
  stand on the clicked block in both cases.
- Grow a sapling on the top row in each sky mode, and break a block on either seam
  row to confirm the newly exposed face appears immediately.
- Detonate a charge on either seam row and check that the entity on the other side is damaged and
  thrown away from the blast, not straight up.
- Inspect both JAR variants: every registered Mixin must have its class, and the no-render
  variant must omit the visual hooks listed in `build.gradle`.

## Documentation and license

- [Modrinth / CurseForge project-page text](modrinth.md) — user-facing features, installation, settings and commands.
- [Performance notes](PERFORMANCE.md) — renderer costs and profiling guidance.
- [Changelog](CHANGELOG.md) — releases and manual migration instructions.
- [Issue tracker](https://github.com/FrogBishop/Biblioteca/issues).

Keep implementation details, source references and development checks in this README and the
performance notes. Maintain `modrinth.md` as the shared project-page copy for players on Modrinth
and CurseForge. Use complete HTTPS URLs for every hyperlink and image in that file so the same
Markdown works on both platforms. Repository-relative source and documentation links are suitable
for this GitHub README.

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra. Licensed under [GPLv3](LICENSE).

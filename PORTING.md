# Multi-version port (1.20.4 – 26.3)

This branch turns the 26.3-only project into a Stonecutter matrix. The build
infrastructure works and 26.3 is verified; the source ports for the other
releases are tracked below.

## Status

| Line | Java | Build |
|---|---|---|
| 1.20.4 | 17 | builds; grouped artifact |
| 1.20.5 – 1.20.6 | 21 | builds from the 1.20.5 representative |
| 1.21 – 1.21.1 | 21 | builds in the 1.20.5 – 1.21.1 group |
| 1.21.2 – 1.21.4 | 21 | builds from the 1.21.2 representative |
| 1.21.5 – 1.21.10 | 21 | builds from the 1.21.5 representative |
| 1.21.11 | 21 | builds; single-version group |
| 26.1 – 26.1.2, 26.2 | 25 | builds; common group |
| 26.3 | 25 | builds full + no-render; also in the common group |

All 20 nodes compile: `SC_ALL=1 ./gradlew compileJava` is the full audit
command. The default build compiles only the group representatives. The 26.3
renderer is complete; 1.21.1 and 1.21.8 now have their own terrain, entity and
block-entity renderer ports. The other legacy nodes and the 1.20.5 / 1.21.5
group representatives still build without the renderer (see below).

The 20 nodes are declared in `settings.gradle.kts`; the default build
configures the six group representatives plus 26.3 and emits the seven grouped
jars below. Set `SC_VERSIONS=...` or `SC_ALL=1` to configure individual nodes
or the full matrix.

### Non-render parity (audit)

Reference: the effective 26.3 server config has 29 mixins and the client config
has 20. The legacy grouped jars (1.20.4 – 1.21.11) and the 26.1 – 26.3 common
jar now carry the same **gameplay** hooks; only renderer-coupled client state is
still out.

| Area | Status |
|---|---|
| Beacon beam scan | **ported** — `LegacyBeaconBlockEntityYLoopMixin` before 1.21.5, `BeaconBlockEntityYLoopMixin` from 1.21.5 |
| Sounds / particles / level events | **ported** — `LegacyServerLevelEffectsYLoopMixin` before 1.21.5, `MidServerLevelEffectsYLoopMixin` for 1.21.5 – 26.2 |
| Dripstone growth | **ported** — `DripstoneYLoopMixin` targets `PointedDripstoneBlock` / `SpeleothemBlock` |
| Build-limit message | **ported** — `LegacyBuildLimitMixin` before 26.1 |
| Client block prediction | **ported** — `ClientLevelDirtyMixin` is now in the legacy client config; its mirror-dirty half is inert without the renderer |
| Grass / bamboo / growing-plant growth | **covered indirectly** — 26.3 added explicit height guards; older code reaches the folded blocks through `LevelBlockAccessMixin`, so the 26.3 hooks have no target there |
| Seam preload slider | **deferred with the renderer** — `VideoSettingsScreenMixin` / `OptionsSubScreenAccessor` only control the renderer's warm-up distance |

### Renderer port

| Node | Renderer status |
|---|---|
| 26.3 | complete: OIT, terrain copies, section discovery, entities/block entities, beacons, clouds, preload slider |
| 1.21.1 | terrain copies via `renderSectionLayer` replay, exact source-section scan, entity and block-entity layer folding; no beacon-beam repetition, cloud folding or OIT |
| 1.21.8 | terrain copies via `prepareChunkRenders` + `renderGroup` replay, exact source-section scan, entity and block-entity layer folding; no beacon-beam repetition, cloud folding or OIT |
| other legacy nodes | no renderer; gameplay loop only |

The ported renderer classes are compiled only on their exact nodes. The grouped
`1.20.5-1.21.1` and `1.21.5-1.21.10` jars are built from 1.20.5 and 1.21.5, so
they do not contain the 1.21.1 / 1.21.8 renderer yet. Use the exact-node jar for
testing; moving the renderer into a group representative is a separate port.

1.21.1 repeats the classic `renderSectionLayer` call with a shifted camera Y;
1.21.8 builds one `ChunkSectionsToRender` per period and replays the extra
lists at the original `renderGroup` call. Neither copies a compiled mesh. The
1.21.8 replay currently creates one render pass per copy, which is correct but
not the final performance shape.

The 26.3 seam-preload slider remains 26.3-only. The draw-command cost model and
the remaining performance ceiling are recorded in `PERFORMANCE.md`.

Present on every port: the server loop (entity/block collisions, light, piston,
TNT, falling blocks, minecarts, neighbour updates, packet positions), saved data
and payload networking, reach (`LegacyReachMixin` before 26.1,
`PlayerReachMixin`/`ServerPlayerGameModeMixin` from 26.1), beacons, effect
broadcasts, dripstone, build-limit suppression, block prediction, and the
door/tall-plant/vine/chorus growth hooks. The create-world GUI and the
dimension-attribute screen are also present everywhere.

## Build layout

- Stonecutter 0.9.8, `dev.kikugie.loom-back-compat` 0.4.2, Loom 1.18, Gradle 9.8.
- Every release is a Gradle subproject named after the version.
- Client and server sources live in one source set (`src/main/java`) so
  Stonecutter can preprocess both; the client entrypoint and client mixin
  config are still client-only at runtime.
- Per-version properties (Fabric API build and URL, compatibility range) live in
  `stonecutter.properties.toml`.
- Fabric API is fetched as its published bundle and the six modules this mod
  needs are extracted under `.gradle/fabric-api/<version>/`. The bundle URL is
  pinned per release; the modules are remapped by Loom where needed.
- Output jars: `versions/<version>/build/libs/biblioteca-<mod version>+<mc>.jar`
  and `...-norender.jar`.

### Grouped jars

The default build compiles one representative per API era and writes a copy
into `build/grouped/` whose `fabric.mod.json` accepts the whole range:

| Artifact | Covers | Compiled at |
|---|---|---|
| `biblioteca-<v>+1.20.4.jar` | 1.20.4 | 1.20.4 |
| `biblioteca-<v>+1.20.5-1.21.1.jar` | 1.20.5 – 1.21.1 | 1.20.5 |
| `biblioteca-<v>+1.21.2-1.21.4.jar` | 1.21.2 – 1.21.4 | 1.21.2 |
| `biblioteca-<v>+1.21.5-1.21.10.jar` | 1.21.5 – 1.21.10 | 1.21.5 |
| `biblioteca-<v>+1.21.11.jar` | 1.21.11 | 1.21.11 |
| `biblioteca-<v>+26.1-26.3-norender.jar` | 26.1 – 26.3 (servers, 26.1/26.2 clients) | 26.1 |
| `biblioteca-<v>+26.3.jar` | 26.3 client with the renderer | 26.3 |

A group is only valid when the intermediary names, Java version and the APIs
this mod touches do not change inside it. The 26.3 renderer is the exception:
it uses 26.3-only classes, so `+26.3.jar` remains a separate full artifact
while the common jar serves the rest of the 26.x range.

The per-version jars under `versions/*/build/libs` are still produced as build
inputs; only `build/grouped/` is meant for distribution.

### Commands

```bash
# default: the seven grouped jars in build/grouped
./gradlew build

# one or more nodes
SC_VERSIONS=1.20.4,26.3 ./gradlew :26.3:build :1.20.4:compileJava

# whole matrix (downloads twenty Minecraft installations, no grouping)
SC_ALL=1 ./gradlew build
```

## Tooling eras

The range crosses three external boundaries before the game code is considered:

1. **Obfuscation.** 1.20.4 – 1.21.11 ship obfuscated and need mappings plus a
   remapping Loom; 26.1+ ship unobfuscated, use official names and the plain
   Loom variant. `loom-back-compat` selects the right plugin per node and
   `loomx.applyMojangMappings()` applies Mojang mappings only where needed.
2. **Java.** 17 for 1.20.4, 21 for 1.20.5 – 1.21.11, 25 for 26.1+.
3. **Mappings.** Mojang names are stable in intent but still rename classes
   between releases (`ResourceLocation` → `Identifier` in 1.21.11,
   `DynamicGpuData` → `SectionMesh`/`DynamicUniforms` in 26.2).

## Source breakpoints, in porting order

### 26.2 (closest)

- `net.minecraft.client.renderer.DynamicGpuData` no longer exists.
  `ChunkSectionInfo` is `DynamicUniforms.ChunkSectionInfo`; indexed draws are
  `SectionMesh.SectionDraw`.
- `ClientboundLevelParticlesPacket.RandomizationType` moved.
- `LevelRenderState.renderWireframeTerrain` moved.

### 26.1.x

- `Gui.screen()` → `Gui.getScreen()`.
- `GridLayoutTab.getLayout()` → accessor/field.
- `GameRenderer.mainCamera()` and `gameRenderState()` → getter names.
- `ViewArea.minY()/maxY()/minSectionY()/maxSectionY()` → `getMinY()` etc.;
  `getRenderSectionAt` is protected (add an invoker/accessor).

### 1.21.9 – 1.21.11

The render-state/submit renderer is present but the seam code uses 26.x helper
types. Expect import and accessor changes; `Identifier` starts in 1.21.11.

### 1.21.5 – 1.21.8

- `SavedData` becomes `SavedDataType` + `SavedDataStorage`.
- Block entity render states, particle packet shape and entity render states
  diverge from 26.x.
- `Identifier` is still `ResourceLocation`.

### 1.21.2 – 1.21.4

- `BlockUpdateFlags`/setBlock signature changes.
- Renderer still the pre-render-state one: the 26.x client mixins do not map.
- `Level.getMinY()/getMaxY()` exist from 1.21.2; older code uses
  `getMinBuildHeight()/getMaxBuildHeight()`.

### 1.20.5 – 1.21.1

- Networking changes from `PacketByteBuf` + channel handlers to
  `CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry`.
- Persistence still uses `SavedData.Factory`.
- Client renderer is the classic `LevelRenderer`, not the 26.x pipeline.

### 1.20.4

- `ResourceLocation`, `ResourceLocationArgument`, integer permission checks.
- `SavedData.Factory` + `DimensionDataStorage.computeIfAbsent(factory, name)`.
- `Level.getMinBuildHeight()/getMaxBuildHeight()`.
- Reach is an inline `Vec3.distanceToSqr` check in `ServerPlayerGameMode`;
  there is no `Player.isWithinBlockInteractionRange`.
- There is no `sendBuildLimitMessage`; the build-limit message is sent from a
  different path.
- `ClientboundLevelParticlesPacket` has the old 10-argument constructor.
- Beacon internals use `BeaconBeamSection` and no `BeaconBeamOwner`.
- The client renderer needs a separate implementation.

## What a complete port needs

1. The shared `Compat` + version scopes now cover identifiers, saved data,
   payload registration, permissions and the height accessors; the remaining
   server mixins are the ones listed as disabled above.
2. A legacy client renderer for 1.20.4 / 1.21.1 / 1.21.8 (the current
   `LevelRenderer` hooks are 26.3-only), then re-enable the client-side
   interaction hooks that were excluded with `PlayerReachMixin`
3. Client renderer implementations:
   - 1.21.9 – 26.3: adapt the current draw-group renderer;
   - 1.21.2 – 1.21.8: classic `LevelRenderer` section layers;
   - 1.20.4 – 1.21.1: classic `LevelRenderer` / `RenderChunk`.
4. Mixin configs filtered per era so an absent target class does not crash the
   game; `require = 0` on optional hooks.
5. Runtime smoke tests per release (client + dedicated server), which the build
   matrix alone cannot provide.
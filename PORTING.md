# Multi-version port (1.20.4 – 26.3)

`multiversion` is the Stonecutter matrix. `main` stays the 26.3-only source;
this branch carries the per-release preprocessing, the grouped artifacts and the
legacy renderer ports.

## Where the port stands

- All twenty nodes compile. The default build configures the six group
  representatives plus 26.3, and writes the seven grouped jars.
- 26.3 is the reference implementation: complete renderer (order-independent
  transparency, terrain copies, seam section discovery, entities, block
  entities, beacons, clouds, seam-preload slider).
- 1.21.1 and 1.21.8 have exact-node classic-renderer ports: terrain copies with
  per-copy frustum filtering, the sky/fog seam fixes, entity and block-entity
  layer folding, repeated beacon beams and a folded cloud layer. No OIT.
- The other seventeen nodes are gameplay-only: the server loop, persistence,
  networking, reach, beacons, effects, growth and seam placement work, but the
  client draws vanilla terrain (no periodic copies).

## Status

| Line | Java | Build |
|---|---|---|
| 1.20.4 | 17 | builds; grouped artifact |
| 1.20.5 – 1.20.6 | 21 | builds from the 1.20.5 representative |
| 1.21 – 1.21.1 | 21 | builds in the 1.20.5 – 1.21.1 group; 1.21.1 has its own renderer jar |
| 1.21.2 – 1.21.4 | 21 | builds from the 1.21.2 representative |
| 1.21.5 – 1.21.10 | 21 | builds from the 1.21.5 representative; 1.21.8 has its own renderer jar |
| 1.21.11 | 21 | builds; single-version group |
| 26.1 – 26.1.2, 26.2 | 25 | builds; common group, no client renderer |
| 26.3 | 25 | builds full + no-render; also in the common group |

The twenty nodes are declared in `settings.gradle.kts`; the default build
configures the six group representatives plus 26.3. Set `SC_VERSIONS=...` or
`SC_ALL=1` to configure individual nodes or the full matrix.

### Non-render parity (audit)

Reference: the 26.3 server config carries 29 mixins and the client config 20.
The legacy grouped jars (1.20.4 – 1.21.11) and the 26.1 – 26.3 common jar carry
the same **gameplay** hooks; only renderer-coupled client state is still out.

| Area | Status |
|---|---|
| Beacon beam scan | **ported** — `LegacyBeaconBlockEntityYLoopMixin` before 1.21.5, `BeaconBlockEntityYLoopMixin` from 1.21.5 |
| Sounds / particles / level events | **ported** — `LegacyServerLevelEffectsYLoopMixin` before 1.21.5, `MidServerLevelEffectsYLoopMixin` for 1.21.5 – 26.2 |
| Dripstone growth | **ported** — `DripstoneYLoopMixin` targets `PointedDripstoneBlock` / `SpeleothemBlock` |
| Seam placement | **ported** — `ServerGamePacketListenerMixin` folds packet positions to the canonical row before any check, `BlockPlaceContextYLoopMixin` opens the one row just outside the build height, and the reach target is folded back by `LegacyBlockReachMixin` (1.20.5 – 1.21.10) / `PlayerReachMixin` (1.21.11+) |
| Build-limit message | **ported** — 26.1+ suppresses the misleading "too high" message on the may-not-interact branch (`ServerPlayerBuildLimitMixin`); older releases never reach the message because the packet position is already folded |
| Client block prediction | **ported** — `ClientLevelDirtyMixin` is in the legacy client config; its mirror-dirty half is inert without the renderer |
| Grass / bamboo / growing-plant growth | **covered indirectly** — 26.3 added explicit height guards; older code reaches the folded blocks through `LevelBlockAccessMixin`, so the 26.3 hooks have no target there |
| Seam preload slider | **deferred with the renderer** — `VideoSettingsScreenMixin` / `OptionsSubScreenAccessor` only control the renderer's warm-up distance |

### Renderer port

| Node | Terrain copies | Sky / fog | Entities, block entities | Beacons | Clouds | OIT |
|---|---|---|---|---|---|---|
| 26.3 | yes | fixed | nearest layer | beams | folded | yes |
| 1.21.1 | yes | fixed | nearest layer | beams | folded | no |
| 1.21.8 | yes | fixed | nearest layer | beams | folded | no |
| other legacy | — | — | — | — | — | — |

The ported renderer classes are compiled only on their exact nodes. The grouped
`1.20.5-1.21.1` and `1.21.5-1.21.10` jars are built from 1.20.5 and 1.21.5, so
they do not contain the 1.21.1 / 1.21.8 renderer. Use the exact-node jar for
testing; moving the renderer into a group representative is a separate port.

1.21.1 repeats the classic `renderSectionLayer` call with a shifted camera Y;
1.21.8 builds one `ChunkSectionsToRender` per period and replays the extra
lists at the original `renderGroup` call. Neither copies a compiled mesh.

Both nodes filter each copy against its own box before submitting it. The
section graph fills `visibleSections` with every source section that is visible
in *some* periodic row, so replaying that whole list at every offset used to draw
a section seen overhead at the player's feet (and the other way round). The
copy lists are rebuilt once per frame, reused across the five terrain render
types, and an empty list skips the layer / pass entirely. Rows at the source
position keep vanilla's list because the section graph already culled them
against the slightly wider offset frustum.

The classic sky problems are fixed on both nodes with the same two rules as
26.3: the hard-coded black lower sky disc is suppressed (`getHorizonHeight` is
folded far below the world on 1.21.1, where the disc is inline;
`shouldRenderDarkDisc` is answered `false` on 1.21.8), and the fog colour no
longer receives the void-darkening factor (`setupColor` on 1.21.1,
`computeFogColor` on 1.21.8). The fog colour is also the frame's clear colour, so
both halves of the old black lower sky are covered.

The 1.21.8 hook is on `prepareChunkRenders` itself, not on `addMainPass`: the
vanilla call lives in the synthetic frame-graph lambda, so a `@WrapOperation`
aimed at `addMainPass` found no target and, with the legacy config's
`defaultRequire` of 0, silently produced no copies. The port now re-enters
`prepareChunkRenders` through `MidLevelRendererInvoker` with a recursion guard,
which is independent of the synthetic name. The replay still creates one render
pass per copy, which is correct but not the final performance shape. Both nodes
also repeat the beacon beam at whole periods; the beam still uses the vanilla
2048-block stretch, so only the copies that fit the render distance are emitted
(capped at eight per direction).

Clouds are a single layer on every port. `CloudFold` moves it to the periodic
copy nearest the eye before the layer is built (`DimensionSpecialEffects`
on 1.21.1, `DimensionType.cloudHeight` on 1.21.8, `CloudRenderer.prepare` on
26.3), so the layer the player sees is the one that belongs to their row.

The 26.3 seam-preload slider remains 26.3-only. The draw-command cost model and
the remaining performance ceiling are recorded in `PERFORMANCE.md`.

Present on every port: the server loop (entity/block collisions, light, piston,
TNT, falling blocks, minecarts, neighbour updates, packet positions), saved data
and payload networking, reach (1.20.5 – 1.21.10 through `LegacyBlockReachMixin`,
1.21.11+ through `PlayerReachMixin`; 1.20.4 keeps its inline check), beacons,
effect broadcasts, dripstone,
block prediction, the door/tall-plant/vine/chorus growth hooks and seam
placement. The create-world GUI and the dimension-attribute screen are also
present everywhere.

## Not done

1. **Renderer ports for the remaining legacy nodes.** 1.21.2 – 1.21.4 can follow
   the 1.21.1 classic port; 1.21.5 – 1.21.7 share 1.21.8's pre-render-state
   `LevelRenderer` but a different chunk scheduler, so the 1.21.8 port is the
   working example; 1.21.9 – 1.21.11 use the render-state/submit pipeline with
   26.x helper types. 1.20.4 needs the oldest `RenderChunk` shape.
2. **The grouped jars do not carry the renderer.** `+1.20.5-1.21.1` is compiled
   at 1.20.5 and `+1.21.5-1.21.10` at 1.21.5, so a 1.21.1 / 1.21.8 user who
   installs the group artifact gets gameplay only. Either split those groups or
   build the representative on the renderer node.
3. **26.1 and 26.2 clients have no renderer.** The common jar is no-render and
   the 26.3 renderer uses 26.3-only types (`DynamicGpuData`,
   `ChunkSectionInfo`, indexed draws).
4. **OIT and seam translucency are 26.3-only.** The legacy ports share one
   compiled mesh across copies and do not re-sort water at the seam.
5. **Cloud, entity and block-entity copies are intentionally single-layer.**
   Terrain repeats; a mirrored mob or a second cloud deck is not drawn.
6. **The seam-preload slider is 26.3-only.**
7. **Legacy renderer performance.** 1.21.1 re-walks `renderSectionLayer` per
   copy and 1.21.8 submits one render pass per non-empty copy; neither batches
   or instances copies yet.
8. **Filtered builds relax `defaultRequire` to 0 on every node, including
   26.3.** A hook that loses its target is therefore silent. Restoring strict
   injection checking on 26.3 (and keeping the export-based check in the test
   procedure) is pending.
9. **Runtime smoke tests.** Only 26.3, 1.21.1 and 1.21.8 have been launched and
   played. 1.20.4, 1.20.5, 1.21.2, 1.21.5, 1.21.11 and 26.1/26.2 are
   build-tested only.
10. **Voxy / far-LOD bridge.** Far-LOD renderers bypass vanilla `ViewArea` /
    `LevelRenderer`, so the seam scan never sees their sections.
11. **1.20.4 seam reach.** That node has an inline `Vec3.distanceToSqr` reach
    check and no `Player.canInteractWithBlock`; it is not folded. Invisible
    today because the node has no renderer.

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
`Player.canInteractWithBlock` is gone from 1.21.11; reach goes through
`isWithinBlockInteractionRange`.

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

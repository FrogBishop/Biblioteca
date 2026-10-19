# Multi-version port (1.20.4 – 26.3)

This branch turns the 26.3-only project into a Stonecutter matrix. The build
infrastructure works and 26.3 is verified; the source ports for the other
releases are tracked below.

## Status

| Release | Java | Fabric API | Build state |
|---|---|---|---|
| 26.3 | 25 | 0.164.0+26.3 | **builds** (normal + no-render jar) |
| 26.2 | 25 | 0.161.0+26.2 | 28 compile errors (renderer renames) |
| 26.1 / 26.1.1 / 26.1.2 | 25 | 0.155.3+26.1.2 | 33 compile errors (client API renames) |
| 1.21.11 | 21 | 0.141.6+1.21.11 | not started |
| 1.21.10 | 21 | 0.138.4+1.21.10 | not started |
| 1.21.9 | 21 | 0.134.1+1.21.9 | not started |
| 1.21.8 | 21 | 0.136.1+1.21.8 | **builds** (server loop + client sync; legacy renderer pending) |
| 1.21.7 | 21 | 0.129.0+1.21.7 | not started |
| 1.21.6 | 21 | 0.128.2+1.21.6 | not started |
| 1.21.5 | 21 | 0.128.2+1.21.5 | not started |
| 1.21.4 | 21 | 0.119.4+1.21.4 | not started |
| 1.21.3 | 21 | 0.114.1+1.21.3 | not started |
| 1.21.2 | 21 | 0.106.1+1.21.2 | not started |
| 1.21.1 | 21 | 0.116.17+1.21.1 | **builds** (server loop + client sync; legacy renderer pending) |
| 1.21 | 21 | 0.102.0+1.21 | not started |
| 1.20.6 | 21 | 0.100.8+1.20.6 | not started |
| 1.20.5 | 21 | 0.97.8+1.20.5 | not started |
| 1.20.4 | 17 | 0.97.3+1.20.4 | **builds** (server loop + client sync; legacy renderer pending) |

The 20 nodes are already declared in `settings.gradle.kts`; the default build
configures the four versions whose source port is complete. Set
`SC_VERSIONS=...` or `SC_ALL=1` to configure the rest.

### Legacy client status (1.20.4 / 1.21.1 / 1.21.8)

Those three jars compile the entrypoint, the payload cache and the complete
server-side loop. The 26.3 renderer is not compiled for them and their
generated client mixin config is empty: the client receives the dimension
attributes but still draws the vanilla single-period world. The create-world
tab, video-settings slider, mirrored terrain, entity/block-entity folding,
beacon beams, light ring, effect folding, neighbour updates, dripstone and
grass bonemeal hooks are disabled on those nodes. The dedicated-server jar is
the complete artifact for those releases today.

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

### Commands

```bash
# default: the supported node (26.3)
./gradlew build

# one or more nodes
SC_VERSIONS=1.20.4,26.3 ./gradlew :26.3:build :1.20.4:compileJava

# whole matrix (downloads twenty Minecraft installations)
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

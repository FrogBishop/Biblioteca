# Multi-version implementation

`main` remains the single-version Minecraft 26.3 branch. `multiversion` contains the adaptation
of `main` commit `844eb61` (0.676.7, including the 0.67.67 seam fixes) to all 35 formal-release nodes in `settings.gradle.kts`.
The feature and validation audit is in [PARITY.md](PARITY.md).

## Distribution model

Every node has a full renderer and a matching no-render JAR. The old broad groups were removed:
even releases in one visual era change method descriptors, mapping IDs, buffer types and uniform
layouts. Exact artifacts avoid declaring compatibility that the compiled bytecode does not have.

`./gradlew build` builds all nodes and collects 70 deliverables in `build/distributions/`.
`SC_VERSIONS` selects a smaller configured set; 26.3 is always added as the active source node.
To build only one selected node, use its qualified task, for example `:1.21.8:build`.
The former `SC_ALL` switch and `build/grouped` directory are no longer used.

| Releases | Output Java | Mapping/runtime |
|---|---|---|
| 1.16.5–1.20.4 | 17 | Mojang development names; intermediary release JAR/refmap |
| 1.20.5–1.21.11 | 21 | Mojang development names; intermediary release JAR/refmap |
| 26.1–26.3 | 25 | Official unobfuscated classes |

All source edits belong in `src/main/java`. Stonecutter conditionals describe structural API
differences; exact string replacements handle a few renames. Replacements are bidirectional,
so use complete class names rather than broad package prefixes. Client Mixin selection in
`build.gradle.kts` controls both compilation and the generated config; the checked-in empty
client list is a template. All generated configs require their injections (`defaultRequire: 1`).

## Rendering generations

### Fixed-function OpenGL: 1.16.5

`FixedTerrainBackend` preserves native material setup, pose transforms and source VBOs. Opaque
copies use native quad draws; alpha copies share a globally ordered triangle-index stream. The
additional index buffer is the mod's only GPU allocation, and array/element bindings are restored.
`BufferedVertexYLoopMixin` captures centres from the actual upload before the CPU bytes are released.

### Shader OpenGL: 1.17–1.21.4

`ClassicTerrainYLoopMixin` keeps native layer/shader setup and submits each visible opaque copy
within that pass. `LegacySectionBufferMixin` marks alpha VBOs; `LegacyVertexBufferMixin` captures
quad centres before the CPU vertex data is released. `ClassicTerrainBackend` creates an ordered
alpha stream with independent indices for every displayed copy and restores each source VAO's
element-buffer binding afterward. It never replays a whole pass for every world period.

Before 1.19, vertex uploads use `BufferBuilder` and an explicit VAO/format setup; later releases
use `RenderedBuffer`. Before 1.20.2, `LegacyTerrainVisibilityMixin` extends the native
`RenderChunkInfo` list used by both compilation and drawing. Access wideners expose its exact
constructor, including 1.16's outer instance. The 1.20.2 refactor moves visibility to
`SectionOcclusionGraph` and renames the dispatcher and section types.

1.20.4 includes a PoseStack argument in `renderSectionLayer`. 1.20.5 removes it. 1.21 changes
`BufferBuilder.RenderedBuffer` to `MeshData`; 1.21.2 replaces the integer index-buffer ID with a
GPU buffer object and renames the shader offset uniform. The hook captures the actual Uniform
local, so it does not guess shader fields across releases.

### Blaze3D GPU draw records: 1.21.5–26.2

1.21.5 still opens a pass per material layer. `GpuLayerTerrainYLoopMixin` replaces its draw list
before the pass opens; its native uploader receives camera-relative `ModelOffset` values.

1.21.6–1.21.10 use `prepareChunkRenders` and `ChunkSectionsToRender`, with per-copy relative
`DynamicTransforms` slices. 1.21.11 changes these to absolute `ChunkSectionInfo` uniforms and an
explicit atlas. `GpuTerrainYLoopMixin` handles both forms and compensates for the native reversal
of the translucent list.

26.1–26.2 use shared Uber vertex/index allocations. The adapter retains base vertices and index
offsets, reads allocations under the dispatcher lock, and preserves 26.1's required upload step.
One translucent group keeps global order across different underlying allocations. 26.2 moves
IndexType and changes the pipeline's vertex-format accessor.

`GpuTerrainDraws` materializes the native draw records and is exercised by the matrix checks.
Only additional index storage is owned by Biblioteca; command-encoder writes order updates
against queued draws. Original vertex buffers remain Minecraft's responsibility.

### RenderPearl and optional OIT: 26.3

The port uses the main branch's source-state capture, periodic draw groups and ordered
`LoopingTranslucency` implementation. Direct and indirect submission consume the same order.
Sorted indices use a rotating, fenced buffer set. The access widener exposes the vanilla private
draw-group type/constructor needed to retain its native bindings and execution path.

The vanilla Improved Transparency option remains under player control. Sorted transparency is
available when it is off or unsupported; newer GPUs that support OIT are recommended to enable
it for better performance. Earlier Minecraft versions use the sorted path without a new OIT option.

### Shared rendering rules

- `LegacyTerrainVisibilityMixin` / `SectionOcclusionGraphLoopMixin` complete the visible source list by checking every eligible
  periodic image. This handles far-side source sections without relying on vanilla's vertical BFS.
- `PeriodicTerrainPlan` computes visible copies and merges independent per-copy quad orders
  globally. 16-bit and 32-bit index streams can coexist without changing source mesh data.
- `QuadCenters` adapts packed floats, Vector3f arrays and CompactVectorArray. The latter's
  `getZ()` returns Y in 1.21.9–26.1.2; the adapter uses the correct vector getter instead.
- Render snapshots use canonical boundary neighbours. Before 1.18, `RenderChunkRegion` fills
  padded arrays: sampling folds while array indices keep the original frame. From 1.18 through
  1.21.5 the snapshot is a column (`RenderChunk`); afterward it is a 3×3×3 section region. Edge block changes invalidate
  the source mesh on the other side as well.
- Entities and ordinary block entities appear at their nearest periodic row. Distance checks
  happen before extraction and are adapted too. Beacon rendering uses the scanned beam height,
  never the vanilla 1024/2048-block stretch, with at most nine visible-range copies.
- Clouds retain one nearest layer. The lower sky disc is suppressed through the active level's
  horizon query. Void fog is suppressed only when `no_void_fog` is true.
- Rain/snow ranges are sliced at the boundary. Sampling stays in source coordinates, geometry
  moves by whole periods. The native animation and graphics path remain in use. Impact generation
  moves between LevelRenderer, WeatherEffectRenderer and ClientLevel across releases; all three
  use the shared periodic impact implementation and their native density/timing options.

## Gameplay and client API boundaries

| Change | Adapter |
|---|---|
| Fixed height constants in 1.16.5 → level-height API in 1.17 | `Compat` uses floor zero/build height on 1.16; guarded constant hooks cover plants, trees, falling blocks and beacon bases |
| SavedData constructor/load → function loaders → factories | Same codec and four attributes, using each release's real storage callbacks |
| Collision streams before 1.18 | Preserve caller predicates and read loaded collision chunks through the periodic level |
| LayerLightEngine graph before 1.20 | Fold neighbor nodes and choose the ring direction for opacity/shape propagation |
| Classic world options before 1.19.4 | Attribute button occupies the Game Rules row in More World Options; native option sliders remain available |
| Old server distributions strip client-only helper methods | Explicit UTF read bounds and typed beacon accessors; runtime API linkage checks cover common bytecode |
| 1.16.5–1.20.4 channel networking → 1.20.5 custom payload codecs | Shared four-field payload, version-specific registration/transport |
| SavedData.Factory → SavedDataType → namespaced storage | One codec, native factory and storage calls per release |
| Inline 1.20.4 reach → canInteractWithBlock → isWithinBlockInteractionRange | Inline placement/breaking wrappers and Player reach hooks |
| Player packet handler split at 26.3 | Fold clampVertical input and same-tick first-good Y baselines |
| Beacon colours float[] → packed int at 1.21; BeaconBeamOwner at 1.21.5 | Typed section constructors and colour arithmetic, no reflection |
| NeighborUpdater Orientation at 1.21.2 | Separate exact callback signatures |
| Particle spawn owner moves at 1.21.9 | Fold before the native client range gate |
| Particle packet fields and source-entity sound signatures | Direct native constructors/methods for each release |
| Sky/fog, sound tick, subtitles and GUI extraction | Selected exact methods/descriptors; strict injection checks |
| Added 26.3 plant height guards | Explicit hooks on 26.3; older code uses folded level access |
| Entity interpolation before 1.20.2 / through 1.21.4 / through 26.2 / 26.3 | Fold the old living target, then legacy steps; rechoose handler targets, then unwrap linear/stepped paths and join queued packets in one frame |
| Explosion class split at 1.21.2; vector origins at 1.21.9 | Periodic exposure boxes with scalar or vector knockback origin hooks |
| Entity reach in 1.20.4 / 1.20.5 / 1.21.11 | Inline packet check, canInteractWithEntity, then isWithinEntityInteractionRange plus AttackRange |
| Relative entity interaction packets before 26.1 | Fold the client hit before subtracting the target position; newer Player.interactOn receives an absolute point |
| Block-hit fractional validation from 1.18.2 | Fold the compared click location where the check exists; earlier placement distance already uses the periodic Entity distance path |
| AABB entity reach from 1.19.4 through 1.20.4 | Wrap the inline box distance; earlier releases use the shared Entity distance hooks |
| Subtitle audibility gate from 1.20.3 | Fold this gate where present; earlier subtitles receive events already gated by the periodic sound engine |
| Ground surface helper extracted at 1.21.9 | Bounded ring scan in the older createPath body or newer findSurfacePosition helper |
| Attachment base at 1.21; ItemFrame box overrides | Normalize box/anchor after native recalculation on the class that owns it |
| Decoration pop box at 1.21.10; section support scan at 26.3 | Read collision/support rows through Level and preserve native overlap/border rules |
| Sapling bone-meal height guard at 26.1 | Remove this guard where present; TreeFeature height hooks apply on every node |

All four attributes, inverse lighting defaults, GUI labels, snapshots and client reset semantics
match 0.676.7. Movement statistics are registered on every node. The server/client initializer
and regression checks are run through Fabric in the validation matrix.

## Dependency and validation setup

All 35 formal releases are enumerated explicitly; snapshots, pre-releases and release candidates
are excluded. The inventory follows Mojang's release metadata and the
[Fabric game-version catalog](https://meta.fabricmc.net/v2/versions/game).

Fabric API is pinned per node. Its umbrella ID is read from the actual bundle, since older
releases use `fabric`. Only directly used modules are compile dependencies; every nested module
is supplied to Loom as a runtime dependency. This avoids runtime remapping of the raw bundle and
lets normal development launches load the complete API. Validation checks module presence and
extracts the native libraries required by the renderer data tests.

## Resolved port gaps and remaining limits

The former missing vanilla renderer nodes, per-layer replay, unsorted repeated alpha, absent
preload controls, 1.20.4 reach gap and relaxed injection checks have implementation coverage
throughout the matrix. `PARITY.md` distinguishes source/injection/data validation from play testing.

These are shared limits of the reference behaviour, not completed integrations:

- Ordinary entities/block entities and clouds intentionally have one nearest image; only terrain,
  bounded beacon beams and weather geometry repeat through the visible periods.
- Voxy/far-LOD/replacement renderers bypass the vanilla extraction path. No bridge is supplied.
- Quad-centre sorting does not split intersecting polygons or solve cyclic overlap.
- Automated checks do not run a game world or verify driver-specific visuals/audio. Interactive
  regression scenarios and the tested scope are recorded separately in `PARITY.md`.

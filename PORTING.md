# Multi-version implementation

`main` remains the single-version Minecraft 26.3 branch. `multiversion` contains the adaptation
of `main` commit `1d2128d` (0.67.0, including 0.6.7) to the 20 nodes in `settings.gradle.kts`.
The feature and validation audit is in [PARITY.md](PARITY.md).

## Distribution model

Every node has a full renderer and a matching no-render JAR. The old broad groups were removed:
even releases in one visual era change method descriptors, mapping IDs, buffer types and uniform
layouts. Exact artifacts avoid declaring compatibility that the compiled bytecode does not have.

`./gradlew build` builds all nodes and collects 40 deliverables in `build/distributions/`.
`SC_VERSIONS` selects a smaller configured set; 26.3 is always added as the active source node.
To build only one selected node, use its qualified task, for example `:1.21.8:build`.
The former `SC_ALL` switch and `build/grouped` directory are no longer used.

| Releases | Output Java | Mapping/runtime |
|---|---|---|
| 1.20.4 | 17 | Mojang development names; intermediary release JAR/refmap |
| 1.20.5–1.21.11 | 21 | Mojang development names; intermediary release JAR/refmap |
| 26.1–26.3 | 25 | Official unobfuscated classes |

All source edits belong in `src/main/java`. Stonecutter conditionals describe structural API
differences; exact string replacements handle a few renames. Replacements are bidirectional,
so use complete class names rather than broad package prefixes. Client Mixin selection in
`build.gradle.kts` controls both compilation and the generated config; the checked-in empty
client list is a template. All generated configs require their injections (`defaultRequire: 1`).

## Rendering generations

### Classic OpenGL: 1.20.4–1.21.4

`ClassicTerrainYLoopMixin` keeps native layer/shader setup and submits each visible opaque copy
within that pass. `LegacySectionBufferMixin` marks alpha VBOs; `LegacyVertexBufferMixin` captures
quad centres before the CPU vertex data is released. `ClassicTerrainBackend` creates an ordered
alpha stream with independent indices for every displayed copy and restores each source VAO's
element-buffer binding afterward. It never replays a whole pass for every world period.

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

- `SectionOcclusionGraphLoopMixin` completes the visible source list by checking every eligible
  periodic image. This handles far-side source sections without relying on vanilla's vertical BFS.
- `PeriodicTerrainPlan` computes visible copies and merges independent per-copy quad orders
  globally. 16-bit and 32-bit index streams can coexist without changing source mesh data.
- `QuadCenters` adapts packed floats, Vector3f arrays and CompactVectorArray. The latter's
  `getZ()` returns Y in 1.21.9–26.1.2; the adapter uses the correct vector getter instead.
- Render snapshots use canonical boundary neighbours. Before 1.21.6 the snapshot is an entire
  column (`RenderChunk`); afterward it is a 3×3×3 section region. Edge block changes invalidate
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
| 1.20.4 channel networking → 1.20.5 custom payload codecs | Shared four-field payload, version-specific registration/transport |
| SavedData.Factory → SavedDataType → namespaced storage | One codec, native factory and storage calls per release |
| Inline 1.20.4 reach → canInteractWithBlock → isWithinBlockInteractionRange | Inline placement/breaking wrappers and Player reach hooks |
| Player packet handler split at 26.3 | Fold clampVertical input and same-tick first-good Y baselines |
| Beacon colours float[] → packed int at 1.21; BeaconBeamOwner at 1.21.5 | Typed section constructors and colour arithmetic, no reflection |
| NeighborUpdater Orientation at 1.21.2 | Separate exact callback signatures |
| Particle spawn owner moves at 1.21.9 | Fold before the native client range gate |
| Particle packet fields and source-entity sound signatures | Direct native constructors/methods for each release |
| Sky/fog, sound tick, subtitles and GUI extraction | Selected exact methods/descriptors; strict injection checks |
| Added 26.3 plant height guards | Explicit hooks on 26.3; older code uses folded level access |

All four attributes, inverse lighting defaults, GUI labels, snapshots and client reset semantics
match 0.67.0. Movement statistics are registered on every node. The server/client initializer
and regression checks are run through Fabric in the validation matrix.

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

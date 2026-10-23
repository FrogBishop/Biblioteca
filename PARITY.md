# Main / multiversion parity audit

Reference: **`main` commit `844eb61`, version 0.676.7**, including its 0.67.67 seam fixes.
Implementation and validation are on `multiversion`; the single-version branch is preserved.

## Scope and evidence

Every row below applies to all 35 declared nodes unless a version boundary is stated. The
reference common/client Mixin lists and helper implementations were checked feature by feature
against the selected source/config for each node. A differently named adapter or an older vanilla
path replaces a missing target; required hooks are not hidden behind `require = 0`.

**Source audit** checks the native caller, descriptor and algorithm for each era. **Strict loading**
uses the real Fabric transformer plus the complete matching Fabric API bundle, and loads all
registered target methods and constructors. **API linkage checks** resolve common bytecode references against the actual client/server classes, including methods stripped from old dedicated servers. **Data checks** bootstrap registries and the mod's
common/client initializers, then exercise saved state, packets and renderer data. **Artifact checks**
inspect the full and no-render release JARs, including remapping metadata.

These checks do not launch a world, open a graphics device, play audio or verify rendered pixels.
The user's earlier successful interactive 26.3 test was on `main`; it is not counted as an
interactive test of this branch or of any older port.

`SeamChecks` constructs a minimal in-memory level and runs transformed entity/search methods
against it at heights 16 and 384 (16/256 with floor zero for 1.16.5). It tests ring distances and non-looping behavior, typed and
untyped queries, result limits, existing output lists, nested and shifted queries, repeated
crossings, client-side native interpolation and queued 26.3 stepped paths. Spawn offsets above both top
rows, ground-surface scans, path-node positions and item-frame anchors are also exercised.
This fixture provides targeted behavior coverage; it does not simulate an entire running world.

## Feature-by-feature comparison

| Reference feature | Multiversion implementation and audit |
|---|---|
| Four attributes and operator commands | `Biblioteca`, `LoopingRegistry`: all four keys and false defaults; old integer permissions before 1.21.11, permission objects afterward. Initializers and stat registration run in the matrix. |
| World save/load | `LoopingWorldData`, `LoopingDimensionManager`: shared codec with 1.16 constructor/load, function loaders, Factory/NBT or SavedDataType storage. Data checks verify field names, defaults, roundtrip, mutation and snapshots. |
| Joining-client sync and disconnect reset | `LoopingStatePayload`, `BibliotecaClient`, `LoopingDimensionCache`: legacy channel through 1.20.4, typed codecs from 1.20.5. Four-set wire roundtrip, whole-snapshot replacement, revision invalidation and clear are exercised. |
| Four-column creation GUI and legacy options pane | `LoopingConfigScreen`, `LegacyCreateWorldScreenMixin`, `CreateWorldScreenMoreTabMixin`, `GridLayoutTabAccessor`: old PoseStack widgets, GuiGraphics and newer extraction API; both language files match inverse flag semantics. Pending choices, defensive copies and consume lifecycle are exercised. |
| Canonical block/fluid/block-entity reads and writes | `LevelBlockAccessMixin`: main's folding helpers and target checks. `ClientLevelDirtyMixin` folds prediction keys and invalidates the opposite boundary mesh. |
| Entity position/interpolation | `EntityYLoopMixin` keeps previous rows near committed positions, including repeated unwrapped writes. Legacy step, stored-target and 26.3 path adapters preserve short movement; queued stepped packets join the preceding path's frame. Transformed entity regressions cover these cases. |
| Guest and vehicle movement validation | `ServerMovementYLoopMixin`: folds vertical clamp input in handleMovePlayer (older) / handlePlayerPositionChange (26.3), plus vehicle packets and same-tick first-good baselines. Passenger player packets do not duplicate vehicle crossings. |
| Crossing statistic | `SeamStats`: native CUSTOM_STAT registration and packet-driven award path in every version. Actual common initialization verifies registration. |
| Block collisions and entity queries | `BlockCollisionsYLoopMixin`, `EntityGetterYLoopMixin`: old collision streams and modern lists preserve predicates, canonical/nearest-position handling and periodic query bounds; common targets load strictly on every node. |
| Placement and breaking reach | `ServerGamePacketListenerMixin`, `ServerPlayerGameModeMixin`, `PlayerReachMixin` / `LegacyBlockReachMixin`. 1.20.4's separate placement and breaking inline checks use `LegacyInlineBlockReachMixin` / `LegacyReachMixin`; no inactive modern reach hook substitutes for them. |
| Build-limit notification | `ServerPlayerBuildLimitMixin` is a cancellable injection on 26.1+. Earlier versions use canonical packet positions before their inline height checks. The obsolete overwrite was removed. |
| Neighbour updates and pistons | Native NeighborUpdater signatures before/after Orientation (1.21.2), main piston logic and moving-piston positions. Inclusive maximum is explicitly minY + height - 1, independent of old exclusive getMaxBuildHeight. |
| Plants and dripstone | Door, double-plant, vine, chorus and dripstone hooks match main. `DripstoneYLoopMixin` is main's Speleothem hook under a stable adapter name (PointedDripstoneBlock before 26.2). Explicit bamboo/grass/growing-head guards appear only in 26.3; older paths already call folded level access. |
| Block light and sky-light override | `BlockLightEngineYLoopMixin`, `LightEngineYLoopMixin`, `LightEngineAccessor`, `LoopingLight`: propagation and outside-row block-light queries wrap; no_sky_light=false preserves the native channel. |
| Beacon activation and scan | Typed legacy BeamSection before 1.21.5, BeamOwner.Section afterward; float colours before 1.21, integer colours later. Same bounded scan and obstruction rules as main; no runtime method-name reflection remains. |
| Bounded periodic beacon geometry | `LegacyBeaconBeamYLoopMixin` before 1.21.9; `BeaconBeamRenderYLoopMixin` afterward. All draw scanned lengths and cap inclusive copies at nine, replacing old 1024/2048 stretches. |
| Terrain source discovery and frustum | Universal `LegacyTerrainVisibilityMixin` / `SectionOcclusionGraphLoopMixin` scan real source rows/columns and visible period offsets. `FrustumInvoker`, `FrustumYLoopMixin`, `SeamCull` handle the boolean/int return boundary at 1.21.2. |
| Boundary mesh neighbours | Padded-array `LegacyRegionSnapshotMixin` before 1.18; whole-column `LegacyRenderChunkYLoopMixin` through 1.21.5; `RenderRegionCacheYLoopMixin` afterward. Native snapshots see the wrapped state for fluid shapes, hidden faces and AO probes. |
| Periodic opaque terrain | Fixed-function and shader native-pass VBO draws, 1.21.5 GPU material lists, 1.21.6–26.2 native draw groups and main's 26.3 extraction. Per-copy bounds and vertical range are independently checked; source vertices are shared. |
| Translucent terrain without OIT | `PeriodicTerrainPlan`, `TranslucentQuadOrder`, `QuadCenters`; main's `LoopingTranslucency` on 26.3. Independent copy orders globally merge far-to-near. Exhaustive reference tests verify indices, interleaving, copy visibility, ties, movement and mesh replacement. |
| Native draw addressing | `GpuTerrainDraws` keeps vertex/index identities, first index, base vertex and uniform callbacks. The data tests inspect actual version-specific draw records. Classic VBO centroid offsets and format transitions are tested. |
| OIT choice | No forced OIT Mixin on any node. 26.3 preserves Improved Transparency OFF/ON and wireframe behaviour; in-memory device checks exercise both direct and indirect ordered commands and fence/buffer lifecycle. Earlier releases use sorting. |
| Ordinary entities and block entities | Legacy render offsets, then EntityRenderState extraction (1.21.9+). Range gates fold before render/extraction; the submit pose folds once and leaves beacon states at their source row. |
| Clouds, lower sky and void fog | One nearest cloud layer at the native entry point; active ClientLevelData horizon suppresses the lower disc; the fog override checks no_void_fog explicitly. All version selectors load strictly. |
| Preload control | `LoopingClientSettings`, `VideoSettingsScreenMixin`, `OptionsSubScreenAccessor`: available in every node, including old options-screen packages and ownership. |
| Rain and snow geometry | Shared LoopingWeather slices; ClassicWeather before 1.21.2, native column collection/state extraction afterward. Source biome/light/surface sampling and complete, non-overlapping vertical coverage are checked. |
| Rain impact sounds, splashes and smoke | `RainImpactsYLoopMixin` selects the generator's owner in each era and delegates to LoopingRainImpacts. Preserves native density, minimal-particle sound behaviour and sheltered-sound timing. |
| Positional, moving and already-playing sounds | `ClientLevelSoundYLoopMixin`, `SoundEngineYLoopMixin`, `LoopingSounds`: delay/range position, moving-source position and listener-crossing channel update, with relative audio bypass. |
| Subtitle range and direction | Both subtitle Mixins handle the old single-location and newer candidate-list/extraction paths. Strict operation counts include compiler-generated distance comparator methods. |
| Server effects and client particles | `ServerLevelEffectsYLoopMixin`, `SeamEffects`, `ParticleYLoopMixin`: native positional/entity sounds, global-event rules, targeted particle range and version-correct packet constructors; client insertion folds before range rejection. |
| Exact-version full/no-render release | Two artifacts per node. Metadata never broadens the Minecraft range; Java targets, AW namespace, intermediary refmaps, registered classes and retained no-render UI/audio hooks are audited. |
| Strictness, complete API runtime and cleanup | All generated configs defaultRequire=1; complete Fabric API modules and side-specific common API references are checked; old whole-pass replay and unused adapters removed. The main cleanup changes and manual inverse-setting migration are documented. |
| Entity distances and searches (0.67.67) | Periodic distances and untyped/typed/collecting queries, plus hasEntities from 1.21.9. Tests cover opposite-edge results, rejected middle rows, limits, existing output, nested queries, distant query frames and non-looping distances. |
| Explosions, sight and melee (0.67.67) | Exposure samples and scalar/vector knockback origins use nearby copies. Sight hooks adapt direct, supplier and scalar eye-height APIs; mob hitboxes and modern AttackRange use periodic reach. Native targets load strictly. |
| Entity interaction, picking and names (0.67.67) | Inline 1.20.4 reach, older/newer Player reach, ProjectileUtil boxes and render-dispatcher distance. Relative click packets and absolute locations have separate adapters. Locator distance/pitch applies from 1.21.6. |
| Pathfinding (0.67.67 / 0.676.7) | Snapshot block/fluid reads fold; target sets, nodes and look control choose the mob's copy; ground surface scans walk the ring with bounded loops. Native surface and node methods are tested; running mob/world scenarios remain manual. |
| Spawning and decorations (0.67.67 / 0.676.7) | Spawn descriptors fold the position and align the temporary box. Hanging/ItemFrame hooks fold native boxes and saved anchors together. Newer placement scans read Level. Tests cover both top-row spawn offsets and frame anchors. |
| Trees, falling blocks and boundary faces (0.67.67) | Tree and falling-block bounds are removed in looping levels; the sapling guard exists from 26.1. Boundary mesh invalidation was already implemented. Strict loading covers every applicable guard. |

## Per-version automated validation

Validation performed locally using JDK 25 with output targets 17/21/25 and matching actual
Minecraft/Fabric classpaths. The table records distinct transformed target classes (client /
server), not counts of individual injections. Both sides also passed core and seam behavior checks; clients
passed terrain/weather and applicable native draw checks. Full/no-render columns refer to builds
and package audits; the runtime check uses the complete mod with environment-specific loading.

| Minecraft | Output Java | Terrain path | Full / no-render artifacts | Strict targets client / server | Data checks |
|---|---|---|---|---|---|
| 1.16.5 | 17 | OpenGL / fixed function | pass / pass | 47 / 29 | pass |
| 1.17 | 17 | OpenGL / shaders | pass / pass | 48 / 30 | pass |
| 1.17.1 | 17 | OpenGL / shaders | pass / pass | 48 / 30 | pass |
| 1.18 | 17 | OpenGL / shaders | pass / pass | 48 / 30 | pass |
| 1.18.1 | 17 | OpenGL / shaders | pass / pass | 48 / 30 | pass |
| 1.18.2 | 17 | OpenGL / shaders | pass / pass | 48 / 30 | pass |
| 1.19 | 17 | OpenGL / shaders | pass / pass | 49 / 31 | pass |
| 1.19.1 | 17 | OpenGL / shaders | pass / pass | 49 / 31 | pass |
| 1.19.2 | 17 | OpenGL / shaders | pass / pass | 49 / 31 | pass |
| 1.19.3 | 17 | OpenGL / shaders | pass / pass | 49 / 31 | pass |
| 1.19.4 | 17 | OpenGL / shaders | pass / pass | 50 / 31 | pass |
| 1.20 | 17 | OpenGL / shaders | pass / pass | 50 / 31 | pass |
| 1.20.1 | 17 | OpenGL / shaders | pass / pass | 50 / 31 | pass |
| 1.20.2 | 17 | OpenGL / shaders | pass / pass | 52 / 32 | pass |
| 1.20.3 | 17 | OpenGL / shaders | pass / pass | 53 / 32 | pass |
| 1.20.4 | 17 | OpenGL / shaders | pass / pass | 53 / 32 | pass |
| 1.20.5 | 21 | OpenGL / shaders | pass / pass | 54 / 33 | pass |
| 1.20.6 | 21 | OpenGL / shaders | pass / pass | 54 / 33 | pass |
| 1.21 | 21 | OpenGL / shaders | pass / pass | 55 / 33 | pass |
| 1.21.1 | 21 | OpenGL / shaders | pass / pass | 55 / 33 | pass |
| 1.21.2 | 21 | OpenGL / shaders | pass / pass | 58 / 33 | pass |
| 1.21.3 | 21 | OpenGL / shaders | pass / pass | 58 / 33 | pass |
| 1.21.4 | 21 | OpenGL / shaders | pass / pass | 58 / 33 | pass |
| 1.21.5 | 21 | GPU layer | pass / pass | 57 / 33 | pass |
| 1.21.6 | 21 | GPU lists | pass / pass | 57 / 33 | pass |
| 1.21.7 | 21 | GPU lists | pass / pass | 57 / 33 | pass |
| 1.21.8 | 21 | GPU lists | pass / pass | 57 / 33 | pass |
| 1.21.9 | 21 | GPU lists | pass / pass | 58 / 34 | pass |
| 1.21.10 | 21 | GPU lists | pass / pass | 58 / 34 | pass |
| 1.21.11 | 21 | GPU / absolute uniforms | pass / pass | 59 / 35 | pass |
| 26.1 | 25 | GPU lists / Uber | pass / pass | 60 / 37 | pass |
| 26.1.1 | 25 | GPU lists / Uber | pass / pass | 60 / 37 | pass |
| 26.1.2 | 25 | GPU lists / Uber | pass / pass | 60 / 37 | pass |
| 26.2 | 25 | GPU lists / Uber | pass / pass | 60 / 37 | pass |
| 26.3 | 25 | RenderPearl / optional OIT | pass / pass | 63 / 40 | pass |

Reproduction commands:

```text
gradlew build writeValidationConfig --console=plain --max-workers=2
python tools/verify_matrix.py --checks --workers 2
python tools/verify_artifacts.py
```

Machine-readable evidence: `build/validation/results.json`, `build/validation/artifacts.json`
and each version/environment's `loader.log`. CI uploads these with the exact release JARs.
All **70 client/server runs** passed core, seam and API-linkage checks, and all **70 artifacts** passed package audits. Each client checks **755,117 ordered quad instances** against an independent exhaustive reference.
The 26.3 backend additionally checks **327 frames / 251,284 quad instances** with in-memory
buffers, direct/indirect command comparison, OIT selection and fence/reset/recreation checks.

## Manual scenarios and shared limits

No interactive visual/audio/gameplay run was performed for the new matrix during this adaptation.
Use the native 256-row world on 1.16.5; for later renderer generations, use a normal-height and a height-16 custom dimension and check:

- A multiplayer guest walking, flying and riding both ways through the boundary, including
  collisions, repeated packets and crossing statistics.
- Placement/breaking/prediction, water surfaces and neighbouring faces, block light, redstone,
  pistons, growing plants and obstructed/unobstructed beacon beams at both edges.
- Water, stained glass and slime viewed from above/below, multiple source sections and periods,
  camera motion, world changes and render-distance changes; compare OIT OFF/ON on 26.3.
- Rain/snow and their source biomes, splashes/smoke, sheltered rain audio, distant moving/static
  sounds, thunder, particles and subtitle arrows before/during/after a crossing.
- New-world choices, saved settings, toggling flags, reconnecting and switching dimensions.
- Opposite-edge entity targeting, nearest players, despawning, melee and interaction, explosion
  damage/knockback, line of sight and mobs following targets in both directions through the seam.
- Remote players/mobs crossing repeatedly and receiving several buffered movement packets;
  name tags and locator-bar distances/arrows where the latter exists.
- Spawn eggs on the top row and the row beneath it, tall paintings and item frames spanning
  the seam, top-row sapling growth and falling blocks settling after a crossing.

The following reference limitations are retained explicitly: ordinary entities/block entities
and clouds have one nearest image; Voxy/far-LOD/replacement renderers have no bridge; conventional
quad sorting does not resolve intersecting polygons/cyclic overlap. They are not presented as
completed compatibility integrations or visual test results.

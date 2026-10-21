# Changelog

## 0.67.0

### Multiversion adaptation

- Adapted the 0.6.7/0.67.0 reference implementation to all 20 declared Minecraft releases,
  from 1.20.4 through 26.3. Each node now has its own full and no-render artifacts.
- Added native terrain submission adapters for classic OpenGL, GPU draw lists, shared Uber
  buffers and RenderPearl. Copies share vertex meshes; non-OIT alpha ordering is global across
  source sections and visible periods. Native pass replay per periodic layer was removed.
- Added weather geometry/impacts, positional audio/subtitles, entity and block-entity visibility,
  bounded beacon beams, boundary snapshot neighbours and the preload option on every node.
- Added 1.20.4's inline block reach checks and version-specific movement validation. Removed
  reflective beacon colour calls and retained each release's native constructor/API.
- Worked around incorrect compressed-centroid Z access in 1.21.9–26.1.2 and invalidated the
  opposite boundary's mesh when an edge block changes.
- Restored strict required injections. Added per-version Fabric loading, codec/network/render
  data regressions and distribution/refmap audits. See PARITY.md for validation scope.

### Fixed

- Rain and snow are visible across the boundary where the world's top and bottom meet. Weather
  columns now repeat by world height within the normal weather radius, using each source layer's
  biome and lighting and stopping at its terrain surface instead of extending into the ground.
- Rain impacts can be heard across the boundary, including the sheltered rain sound. Splash
  particles and smoke where rain hits lava, magma or lit campfires use the same nearby surface.
- Local and entity-bound sounds use periodic distance and direction, including thunder, ambient
  block sounds, distance-delayed playback and sounds already playing when the listener crosses
  the boundary. Sound subtitles use the matching audible range and direction arrows.
- Entity-bound sound broadcasts, global event directions and particle effects sent to an individual
  player now account for the boundary on the server too.

### Changed

- Version is now `0.67.0`.
- Removed the forced OIT override in looping dimensions. The vanilla **Improved Transparency**
  setting now selects the render path, including on GPUs where OIT is available.
- Added conventional translucent terrain rendering when OIT is off: each visible periodic copy
  has its own sorted indices, and quad orders are merged across sections and copies from far to
  near. Vertex meshes stay shared; both direct and indirect draws are supported. Turn Improved
  Transparency off to test this path on an OIT-capable GPU.
- Renamed the lighting attributes, commands and saved-data keys to `no_void_fog` and
  `no_sky_light`, and inverted their meaning. Both default to `false`, keeping vanilla void
  fog and sky light in looping dimensions; `true` disables the corresponding effect.
- Updated the create-world labels and buttons: **No void fog** switches between **Keep fog**
  and **Disable fog**, and **No sky light** between **Keep skylight** and **Disable skylight**.
  Chinese labels and the player guide follow the same meaning.
- Recommend enabling OIT on newer supported GPUs for better performance. The Modrinth
  description now focuses on player setup and settings; the GitHub README documents the
  toolchain, architecture, rendering contracts and development checks.
- Removed redundant draw-group accessors, unused helpers and raw collection casts. Corrected
  outdated comments about coordinate folding, frustum results, beacon copies and render data.
- Track the version and removed-Mixin list as inputs when generating the no-render manifest,
  so incremental builds refresh the variant metadata when either changes.

### Manual migration

Old `void_fog` / `sky_light` saved settings are not converted automatically. The new attributes
start at `false` when their keys are absent. To preserve an existing world's appearance, stand
in each affected looping dimension and run the new command with the **opposite** boolean:

| Previous setting | New command for the same effect |
|---|---|
| `/biblioteca voidfog false` (old default: fog hidden) | `/biblioteca no_void_fog true` |
| `/biblioteca voidfog true` (fog shown) | `/biblioteca no_void_fog false` |
| `/biblioteca skylight false` (old default: sky light removed) | `/biblioteca no_sky_light true` |
| `/biblioteca skylight true` (sky light kept) | `/biblioteca no_sky_light false` |

The commands save the new settings with the world. To keep the old defaults, set both new
attributes to `true` in every looping dimension. Simply renaming the old saved-data keys would
reverse the effects, because the stored dimension lists now identify where effects are disabled.

## 0.6.7

### Fixed

- Guests can cross the Y seam. The server now folds the requested Y of a movement or vehicle
  packet onto the ring copy nearest the player before running the "moved too quickly" check.
- Other players' particles are visible across the seam. Client-side particle spawns are moved
  to the ring layer nearest the camera, and server-side effect broadcasts are rebuilt per
  listener at the copy each listener can see.
- `Seam crossings` now increments. The previous fast path skipped the fold whenever both the
  current and the requested row were inside the world, which is exactly how a client-folded
  crossing arrives.

### Added

- **Void fog** and **Sky light** as per-dimension attributes, with create-world columns,
  `/biblioteca voidfog` and `/biblioteca skylight` commands, saved data and client sync.
- **Seam crossings** statistic, counted from the movement packet that actually changes the ring
  copy (walking, flying or riding), not from a tick loop.

### Changed

- Version numbering is now `0.6.7`.
- Y-coordinate folding lives in one helper (`SeamPositions`) instead of being repeated in every
  mixin.
- Removed the per-tick wrapping mixins for minecarts, falling blocks and primed TNT: all
  position writes go through `Entity.setPosRaw`, which the entity mixin already folds.
- Replaced the remaining `@Overwrite` handlers with cancellable injections.
- Updated README, player guide and performance notes; removed unused API.

## Before 0.6.7

Development builds were numbered `0.0.x` and were not formally released. They established the
ring, the mirrored renderer, block and entity folding, the beacon and lighting behaviour, the
`-norender` server jar, and the create-world configuration screen.

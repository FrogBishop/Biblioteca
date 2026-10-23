# Changelog

## 0.676.7

### Fixed

- Players and mobs crossing the boundary no longer flicker. A stepped movement path can hold rows
  on both sides of the seam; it is unwrapped one step at a time, and a committed row that is a
  whole period away from the row still on screen moves that row with it instead of letting the
  renderer interpolate through the world.
- Mobs walk to a target across the boundary. A path target was folded into the stored period,
  which put it a whole world height away from the mob and outside the search area, so the mob
  only acquired its target and stood still. Targets are now moved to the copy nearest the mob,
  and the ground-surface search in front of a destination walks the ring instead of scanning
  the whole world column below it.
- A spawn egg used on a block one row below the top row no longer places the mob inside that
  block. The surface search reads rows outside the world through the chunk while the entity's
  temporary row has already been folded, so the two frames disagree and the estimate moves the
  mob one row too low. The search now reads through the level and measures the box in the spawn
  position's frame.
- Tall paintings can be placed at the top of the world. The support and collision checks read
  rows outside the world through the chunk and the section-based block scan, which answer air
  there, so a decoration whose rows are the top row and the rows below the world failed its
  placement test. Those checks now read through the level, which folds, and a painting can span
  the seam at any height.

### Changed

- Version is now `0.676.7`.

### Notes

- These are follow-ups to the seam work in `0.67.67`. Each one was a place where vanilla compared
  a folded row with an unfolded one: a stepped movement path, a path target, a decoration's
  support scan, and the surface search under a spawn position.

## 0.67.67

### Fixed

- Entity searches return what is in the box on the ring. A search box within reach of a seam is
  widened by one world height and its results are filtered by the ring position of each entity, so
  AI goals, explosions, effects and command selectors see the entities beside them rather than
  only the ones beside them in absolute space.
- Explosions across the boundary damage and throw entities. The visibility samples and the
  knockback direction use the copy of the entity nearest the blast, instead of a body a whole world
  height away.
- Mobs are no longer despawned when their player crosses the boundary. Distances between entities
  are measured around the ring, which also corrects nearest-player selection, AI reach checks,
  melee range and leashes.
- Another player's name tag stays visible across the boundary. Entity render distance is measured
  around the ring, which also corrects entity shadows at the seam.
- Other players and mobs crossing the boundary move smoothly instead of snapping. Movement and
  teleport packets choose the target copy nearest the entity, the previous position is folded with
  it, and the "too far to interpolate" test uses the ring distance.
- The locator bar draws each waypoint at its ring distance and pitch, so dot size and up/down
  arrows are correct across the boundary.
- Mobs can pathfind across the boundary. The pathfinding snapshot now answers rows outside the
  stored period from the rows the ring puts there instead of air, path nodes and look targets are
  placed on the copy nearest the mob, and ground navigation folds its destination before the
  surface search.
- Mobs can see across the boundary: line of sight measures eye rows around the ring.
- A spawn egg used on the top face of the top row places the mob on the ring row above instead of
  inside the block. Entity spawning now folds the spawn position before the surface search.
- Villagers and other entities can be attacked and interacted with across the boundary. Interaction
  and attack reach, the client's crosshair pick, and the click location sent with an interaction
  are all ring-aware.
- Paintings and item frames can be placed across the boundary, including decorations that span it.
  A hanging entity's position, bounding box and saved attachment are now folded together, and block
  items receive the folded block position rather than an out-of-range copy.
- Saplings on the top row grow. The bone meal build-height check and the tree height check no
  longer treat the ring as having a ceiling; the random-tick light check already reads the open
  sky above the top row and needed no change.
- Faces exposed by breaking a block on either seam row are rebuilt immediately. Changing a block in
  the bottom section also dirties the top section, and the other way round, because each compiles
  against one row of the other.
- A falling block that crosses the boundary is placed as a block instead of being dropped as an
  item. The out-of-world fallback no longer reads the bottom row as "below the world".

### Changed

- Version is now `0.67.67`.
- Documented the ring-aware distance and search rules in the README, the performance notes and the
  player description, and cleaned up the new hooks: sorted imports, removed a search-widening hook
  that `Level.getEntities` now covers, and corrected comments that described the earlier behaviour.

### Notes

- The fixes are additional Mixins beside the existing ones; each hook documents the vanilla
  assumption it removes. `SeamPositions` gained nearest-copy helpers for points, boxes and
  distances, and `Level.getEntities` is now the single place where a search area is widened, so
  new code can keep using ordinary vanilla queries and still see the ring.
- Entity searches are only widened within 16 rows of a seam, and distance queries only fold when
  the two rows are closer around the ring, so code away from the boundary runs unchanged.

## 0.67.0

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

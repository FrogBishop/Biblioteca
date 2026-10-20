# Changelog

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

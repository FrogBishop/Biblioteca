# Performance notes

Biblioteca keeps one authoritative period on the server. The client mirrors the compiled
section vertex meshes it already has; it does not create chunks, vertex meshes or entities for the
copies. Sorted transparency adds per-copy quad orders and GPU index ranges, so its memory cost
grows with visible translucent geometry.

On Minecraft 26.3 with newer GPUs that support OIT, we recommend enabling **Improved Transparency** for better
performance. This avoids the mod's per-copy sorting and index uploads; the option remains under
the player's control.

## Cost model

- **Terrain:** each visible section copy gets a native model offset or section uniform. Opaque terrain and OIT use
  one draw per section layer per copy. Conventional transparency merges all visible quad orders
  and can split a section into several draws. The compiled vertex mesh is shared by every copy.
- **Layer count:** a copy is submitted while its section centre is within the render distance.
  With a height of 16 blocks, render distance 12 (192 blocks) gives up to 25 terrain copies,
  and render distance 32 (512 blocks) gives up to 65. A normal 384-block dimension at those
  distances gives at most two and three copies respectively.
- **Entities and block entities:** one copy, at the periodic layer nearest the camera.
- **Beacon beams:** at most nine copies in total per beacon, selected from the vertical view range.
- **Clouds:** one folded layer.
- **Rain and snow:** weather columns split at world boundaries, within the vanilla weather radius.
  The list of visible periods is shared by all columns in a frame; each segment uses vanilla weather
  geometry and is clipped against its terrain surface.
- **Rain impacts and audio:** rain uses the vanilla particle-attempt budget and sound timing.
  Moving sounds use their usual position updates. Listener movement triggers an extra channel
  update only when the nearest periodic copy changes; relative interface audio bypasses folding.
- **Source discovery:** every loaded source section is tested against the period offsets that
  can reach the render distance; sections that are already visible are skipped. The scan
  reuses its buffers and does not allocate an AABB per candidate.

## Server cost

The server keeps one period and does not scan for seam events. Coordinate folding is a
`floorMod` for canonical block/light/entity coordinates and nearest-copy selection for movement
packets and effects. Entity positions are folded at their single commit point. The
`Seam crossings` statistic is awarded from the movement packet that changes the ring copy, so
there is no per-tick check for it and none for entities either.

## Known ceiling

Draw-command submission remains a ceiling, especially in short worlds. Height 16 at 32 chunks
is the stress case: roughly 65 copies of every visible section. Without OIT, sorting and index
uploads add work, and interleaving transparent faces can require more than one draw per copy.
Performance depends on visible geometry and the driver's draw submission path.

Terrain copies are not combined into instanced draws. The sorted transparency path groups
compatible draws, but each draw still refers to one displayed section. If profiling identifies
command submission or vertex work as the bottleneck, instancing opaque/OIT copies is a possible
future optimization.

Voxy and other far-LOD renderers do not use the vanilla `ViewArea` / `LevelRenderer`
extraction path. Biblioteca's seam discovery therefore does not see their LOD sections;
Voxy compatibility needs a separate renderer bridge, not another constant in the frustum
test.

## Transparency without OIT

- On 26.3 the vanilla Improved Transparency option controls the path. Earlier versions use sorted transparency. The mod never forces OIT.
- Each visible copy caches its own quad order. Camera movement updates distances, and a linear
  check reuses the order when it is still valid; a full sort runs only after an ordering change.
- The sorted streams are merged globally, including the original sections, so faces in adjacent
  sections and different periods can interleave. Consecutive faces from the same copy are one draw;
  26.3 groups consecutive draws with identical buffer bindings. Earlier GPU executors consume one ordered alpha list.
- Indices use the source mesh's 16-bit or 32-bit type. Vertices are shared. Classic OpenGL
  replaces its additional index store with `glBufferData`, restoring the source VAO's element
  binding after each draw. 1.21.5–26.2 use ordered command-encoder writes. 26.3 uses three rotating
  mapped buffers with GPU fences, keeping in-flight frames separate.
- Cached orders are discarded when copies leave the visible set or meshes change. GPU buffers
  are released on world/render-data reset. The 26.3 backend also releases its sorted storage when
  the ordered path stops being used, including when OIT is enabled.
- Both direct and indirect draw submission consume the same ordering. Devices without multi-draw
  indirect support still use vanilla's direct path.
- The algorithm sorts quad centers, as vanilla does. It does not split intersecting polygons and
  cannot solve cyclic overlap in the way a per-fragment OIT technique can.

## What is already optimized

- Reusable copy-index and visibility arrays in the 26.3 backend.
- Lazy native uniform creation, shared by draw runs at the same displayed section position.
- Allocation-free seam frustum tests (six doubles instead of a temporary AABB).
- A reused presence set for the far-seam scan.
- The cached per-level looping flag on entity, block-entity, beacon and cloud paths.
- Period-offset scanning covers every potentially visible layer, including short worlds.

## How to measure

Use Spark or JFR and compare the same world with the loop off and on:

- height 16 and 384, render distance 12 and 32;
- camera at the seam looking horizontally, up and down;
- no entities, then many entities/block entities near the seam;
- Improved Transparency on and off, with water/glass spanning the boundary and many repeated layers;
- record frame time, draw calls, section count, allocated bytes and server tick time.

Do not compare against a different render distance: the number of copies is part of the
workload.

## Version-specific costs

The classic renderer draws within the current vanilla material pass. The 1.21.5 adapter replaces
the native draw list before opening the GPU pass; 1.21.6–26.2 prepare one periodic list for each
native material group. No backend reruns an entire frame/pass for each world period.

The shared CPU planner reuses per-copy ordering caches but creates frame lists. This remains an
allocation cost to profile, especially in height-16 worlds. The 26.1+ adapters preserve shared-buffer
base vertices and the native upload lock. Periodic copies start fully visible so source chunk-fade
values do not tint the repeated layers toward the fog colour.

The exact-version artifacts do not imply identical frame times across rendering generations.
Compare each version against its own vanilla path and record GPU/driver, Java version and graphics
options alongside results. Automated data tests do not measure real GPU performance.

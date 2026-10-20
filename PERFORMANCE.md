# Performance notes

Biblioteca keeps one authoritative period on the server. The client mirrors the compiled
section meshes it already has; it does not create chunks, meshes or entities for the copies.
That keeps memory flat, but it does not make the extra layers free.

## Cost model

- **Terrain:** each visible section layer gets one `ChunkSectionInfo` and one draw command per
  periodic copy. The compiled mesh is shared by every copy. A copy is first tested against the
  cull frustum, so the source-section union produced by the seam scan is not submitted wholesale
  at every offset.
- **Layer count:** a copy is submitted while its section centre is within the render distance.
  With a height of 16 blocks, render distance 12 (192 blocks) gives up to 25 terrain copies,
  and render distance 32 (512 blocks) gives up to 65. A normal 384-block dimension gives at
  most three.
- **Entities and block entities:** one copy, at the periodic layer nearest the camera.
- **Beacon beams:** at most `MAX_COPIES` (8) per direction, because a beam spans the period.
- **Clouds:** one layer, folded to the periodic copy nearest the camera; the cloud mesh is
  built once per frame, not once per copy.
- **Source discovery:** every loaded source section is tested against the period offsets that
  can reach the render distance; sections that are already visible are skipped. The scan
  reuses its buffers and does not allocate an AABB per candidate.

## Known ceiling

The remaining ceiling is **draw-command submission**, not mesh memory. Every visible copy is
another draw command (and another section-info entry). Height 16 at 32 chunks is the stress
case: roughly 65 copies of every visible section. Whether that is fast depends on the GPU,
the driver's multi-draw path, and how many sections are actually visible; it is not a fixed
budget.

The current renderer does **not** instance or merge copies. That is deliberate: correctness
and allocation behaviour were fixed first. If profiling shows command submission or GPU
vertex work dominates, batching several copies into one instanced draw is the next step.

The legacy ports are further from that goal than 26.3. 1.21.1 re-runs the classic
`renderSectionLayer` walk once per copy (one draw per visible section and layer), and 1.21.8
submits one `ChunkSectionsToRender` - one render pass - per non-empty copy. 26.3's
draw-command duplication is the cheapest shape of the three.

Voxy and other far-LOD renderers do not use the vanilla `ViewArea` / `LevelRenderer`
extraction path. Biblioteca's seam discovery therefore does not see their LOD sections;
Voxy compatibility needs a separate renderer bridge, not another constant in the frustum
test.

## What is already optimized

- Reusable copy-index and visibility arrays instead of a per-frame map of `Object[]` draw
  references.
- Lazy `ChunkSectionInfo` creation: a copy gets an entry only when it has a draw.
- Allocation-free seam frustum tests (six doubles instead of a temporary AABB).
- A reused presence set for the far-seam scan.
- The cached per-level looping flag on entity, block-entity, beacon and cloud paths.
- Cloud folding is one height rewrite per frame (`CloudFold`) on all three renderer ports,
  not a second cloud mesh.
- Exact period-offset scanning for short worlds. The old nearest-image gate could drop copies
  3, 4, ... of a height-16 world.
- Per-copy frustum filtering on 1.21.1 and 1.21.8: the visible-section list is filtered once per
  frame into one reusable list per offset, so a section seen through a row overhead is not drawn
  at the feet (and vice versa), and an empty row skips the layer call / render pass.
- The 1.21.8 copy lists are built by re-entering `prepareChunkRenders`, not by wrapping the
  `addMainPass` call: the vanilla call is in a synthetic lambda, where a wrap silently missed.
  Copies were therefore not submitted at all before that fix.

## How to measure

Use Spark or JFR and compare the same world with the loop off and on:

- height 16 and 384, render distance 12 and 32;
- camera at the seam looking horizontally, up and down;
- no entities, then many entities/block entities near the seam;
- record frame time, draw calls, section count, allocated bytes and server tick time.

Do not compare against a different render distance: the number of copies is part of the
workload.

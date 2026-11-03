# Development tasks

Scope: Fabric and NeoForge. Forge support is cancelled. Tasks below are worked on in order;
completed items must include regression evidence and any remaining version-specific limits.

## Completed in 1.0.4-dev.1 (2026-11-02)

1. [x] Preserve already living particles across Y-boundary crossings. Campfire smoke and Nether
   portal particles were reported to disappear/reappear when the player crosses. Cover previous
   positions, interpolation, both directions, repeated crossings and ordinary dimensions.
2. [x] Carry game events and vibrations across the boundary. Sculk sensors were reported unable
   to detect events across it. Cover listener discovery, distance/travel time, occlusion and
   duplicate delivery; include related listeners where available in each Minecraft version.
3. [x] Add usable in-game diagnostics for dimension bounds, sky mode, displayed periods and
   renderer settings, plus a Y-section/light comparison export where practical.
4. [x] Evaluate narrower periodic entity queries. Preserve tall entities, whole-period queries,
   predicates, result limits, uniqueness and nested-query behavior before adopting an optimization.
5. [x] Audit coordinate-bearing game data and add independent-observer regressions, including
   effects, position-bearing entity data and vibration targets.
6. [x] Add complete visual entity copies, including distant copies of the local player. Keep one
   authoritative entity, bound rendering cost, and verify selection, shadows and ordinary worlds.
7. [x] Restore multiple periodic cloud layers. Investigate the previous renderer/cache capacity
   limit first; verify more than three layers without resource exhaustion or stale geometry.

All 35 Fabric versions build and pass 70 client/server regression runs and 70 artifact audits.
All six NeoForge versions pass four packaged variant/side runs each (24 runs, 12 artifacts).
Native tests cover the APIs present in each release; versions before game events were introduced
do not claim a sensor test. The final complete/no-render output contains 82 JARs.

Fabric and NeoForge 26.3 additionally pass both isolated graphical scenarios, with all 26 final
screenshots inspected. The recorded sensor strengths are 10/10 across the two seam directions
and 0/0 with wool/looping disabled. Persistent particle identities retain increasing ages
29/59/89. Each light export compares 6,528 samples with no mismatches in the settled scene.

The height-16 graphical scene submits 16 cloud layers, 16 chest poses, 16 crystal copies,
4–5 visible cow states and eight distant first-person player copies. It checks independent
queued states, native shadow submissions and distance rejection, short guardian/crystal beams,
fast/fancy/OIT clouds, option restoration, repeated crossings and ordinary-world behavior.
Both loaders complete more than 1,300 observed cloud passes with eight distinct native cloud
buffers over the run; mesh preparation is shared instead of replayed for each layer.

The entity-query evaluation rejected two clipped storage ranges because they miss displaced
hitboxes. Early periodic intersection filtering reduces caller-predicate evaluations from 259
to 2 in the dense native fixture. This is a deterministic work-count benchmark, not a measured
TPS/FPS improvement or proof that the underlying storage scan is smaller.

`NETWORK_AUDIT.md` records the coordinate consumers and exact coverage. Remote multi-client
sockets, arbitrary third-party packets/renderers and graphical tests on all older versions
remain outside this validation. Full evidence and exact artifact hashes are recorded under
`build/periodic-followups/verification.json`; the readable result is `validation.md` beside it.

Follow-up: no-render builds omit all three rendering controls from Video Settings (seam preload,
entity copies and cloud layers). Their dedicated settings injection and accessor are packaged
only in the complete builds. All 41 no-render artifacts were inspected; that packaging correction
left the 41 complete JARs byte-identical to the graphical-validation baseline. NeoForge's 24 packaged checks pass
again after this packaging correction. Details are in `build/periodic-followups/no-render-video-settings/`.

Sodium follow-up: the new particle hook initially conflicted with Sodium 0.2's priority-1000
billboard overwrite on 1.16.5. Raising only this version's injection priority to 1100 fixes
startup and retains Sodium's fast vertex path. Loader 0.20.1 with Mixin 0.17.5/0.8.7 passes
actual Multidraw, Oneshot and particle-optimization-disabled runs, including living particles
through both boundaries. The enhanced observer still reproduces the old JAR's startup failure.
The no-Sodium 1.16.5 native client/server checks also pass. Evidence and 21 inspected screenshots
are recorded in `build/sodium-particle-1.16.5/report.json`; only the 1.16.5 complete JAR changed.

## Sodium integration (completed 2026-11-04)

Exact releases, loading coverage and graphical limits are recorded in `SODIUM_COMPATIBILITY.md`.
The old failure audit remains under `build/sodium-matrix/final/` as a negative control; it is
superseded by the integration report rather than rewritten as a passing historical run.

- [x] Add legacy and modern Sodium terrain paths, including the 1.21.1 backport and 26.2 signatures.
- [x] Retain native geometry, transparency indices, periodic visibility, block/light/biome snapshots
  and opposite-seam cache invalidation. Reuse cloud buffers across layers.
- [x] Cover optimized particles and the native fallback. Correct 1.16.5 block-entity double
  translation and newer first-person omitted-state handling.
- [x] Extend real graphical scenes to 14 Fabric versions and NeoForge 26.3, checking terrain
  replacement, both crossings, particles, entities, clouds and ordinary-world restoration.
- [x] Declare the optional NeoForge 26.3 Sodium minimum and retain no-render UI/packaging exclusion.

## Continuous movement (completed 2026-11-04)

- [x] Reproduce age-601 falling-block removal and retain that vanilla timeout in all dimensions
  at the user's request; fix physical displacement independently.
- [x] Preserve physical signed displacement; translate native movement queues and speed baselines
  in the same frame, including multiple moves within a tick.
- [x] Run real server sand/gravel/anvil, concrete/water, collision/placement and item/TNT/mob
  regressions across movement-storage version boundaries, with ordinary-world controls.

## Deferred research

- Shader/Iris parameter interfaces: deferred at the user's request.
- Optional periodic terrain generation: keep as a separate design study. Existing-world terrain
  and bedrock preservation remain the current behavior; a new generator needs its own specification.

## Reference and validation

The comparison of Seamless World Wrap and Circumnavigate informs the task list, not a replacement
of Biblioteca's tested implementation. New behavior is implemented against each target's native
Minecraft APIs. Tests and graphical evidence stay under ignored `build/` directories; portable
checks belong in `tools/`. Existing released JARs and release tags are preserved.

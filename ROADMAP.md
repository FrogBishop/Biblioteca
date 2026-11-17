# Development tasks

Scope: Fabric and NeoForge. Forge support is cancelled. Tasks below are worked on in order;
completed items must include regression evidence and any remaining version-specific limits.

## 1.0.4 release

The completed work below ships in 1.0.4: **36 Fabric and 23 NeoForge targets**, each with
complete/no-render variants, for **118 JARs**. Earlier milestone counts and development hashes
below describe those historical runs. See [CHANGELOG.md](CHANGELOG.md) for the player-facing
release notes and [RELEASE_1.0.4.md](RELEASE_1.0.4.md) for the final build evidence.

## NeoForge backport and artifact cleanup (2026-11-16)

- [x] Remove stale Biblioteca 1.0.2/1.0.3 release, development and test-cache JARs, preserving
  a path/hash inventory and historical logs.
- [x] Add 17 exact NeoForge release targets down to 1.20.1, with complete/no-render artifacts,
  historical networking, metadata, entrypoints, access and production mapping adaptations.
- [x] Validate all 23 NeoForge targets on client and server in both variants, and refresh
  the 36-target Fabric regression matrix after shared compatibility fixes.
- [x] Test every selected official NeoForge Sodium combination, including the Java 21 baseline
  for 1.21.2 and the existing 26.3 minimum-version control.
- [x] Finish six representative real-client runs, review all 36 screenshots, and freeze the
  final 118 artifacts with their hashes and exact native/Sodium/save evidence.

Current backport evidence is under `build/neoforge-backport/`; see
[NEOFORGE_COMPATIBILITY.md](NEOFORGE_COMPATIBILITY.md) for all 23 loader pins.

## Infinity Easter egg catalogue (completed 2026-11-16)

- [x] Add all 43 native Easter egg dimensions after the vanilla three, using the original strings
  without translation. Validate every hash and the complete hard-coded generator set.
- [x] Support alphabetical display, paging, range counts and full long-name tooltips, with settings
  for unvisited worlds applied, saved and synchronized without registering those worlds.
- [x] Verify both variants in Chinese, including a narrow window; review all 12 screenshots.
  Catalog evidence is in `build/infinite-easter-eggs/report.json`; current snapshot hashes are in
  `build/camera-seam/report.json` after the legacy-position follow-up.

## 20w14infinite Fabric port (completed 2026-11-16)

- [x] Add an exact snapshot node and full/no-render artifacts on Java 17+, with four bundled
  legacy API modules and correct normalized game-version metadata.
- [x] Adapt native book-dimension identities, NBT storage, commands and state synchronization;
  cover both dedicated servers and the snapshot's independent integrated-server load path.
- [x] Adapt the native renderer, collision, path, sound and light APIs and the older settings UI.
  Keep the vanilla falling-block timeout and hide video controls in the no-render variant.
- [x] Verify both snapshot variants with real physics, separate book dimensions, save/restart,
  invalid saved entries, joined-player state, particles, entity copies and cloud buffers.
- [x] Regress all 36 Fabric nodes, all six existing NeoForge nodes and representative Sodium
  combinations. That milestone produced 84 development JARs; logs/hashes are in `build/infinite-port/`.
- [x] Document the NeoForge limit: no loader exists for this snapshot, so there is no NeoForge
  snapshot artifact. See `INFINITE_COMPATIBILITY.md` for installation and validation scope.

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

## Long-term exploration: integrate X/Z looping (planned 2026-11-18)

Explore adding optional X and Z loops to Biblioteca's existing Y loop, with a unified coordinate
and image model. This is future work without a committed release or schedule. Use
[Seamless World Wrap](https://github.com/Amproids/seamless-world-wrap/tree/41534c0877a10bfc9a2ebd354df1095b4e5b161f)
as a reference for horizontal chunks and generation; evaluate selective reuse and preserve its
MIT copyright and license notices when incorporating code.

**Generation scope is X/Z only.** The Y loop remains a spatial/gameplay capability for modpacks
with their own terrain generation. Joining the world's top and bottom terrain naturally, changing
bedrock, or redesigning vertical generation is outside this work. Provide horizontal generation
adaptation for standalone play while allowing modpacks to supply their own generator.

- [ ] Start with a bounded 1.21.1 prototype on one loader and the vanilla renderer; assess the
  integration before expanding the supported matrix.
- [ ] Define per-dimension axis settings, periods and saved data. Unify canonical coordinates,
  observer-relative images and actual movement history, preserving existing Y-only worlds.
- [ ] Adapt horizontal chunk lookup, generation neighborhoods, loading/tracking and network
  updates. Keep one authoritative chunk/entity and coherent state for independent observers.
- [ ] Extend queries, collisions, ray hits, pathfinding, light and game events to combined axes.
  Preserve oversized/displaced hitboxes, result limits, logical-object uniqueness and native
  update behavior; avoid inflating small queries by whole periods on every axis.
- [ ] Extend image enumeration and rendering with a total copy budget, shared geometry and
  consistent visibility, transparency, shadows and first-person self-copy rules.
- [ ] Integrate X/Z periodic terrain sampling, required surface sampling, cross-seam structure
  references/placement and terrain adjustment. Keep arbitrary generator compatibility and
  tiny-world progression guarantees as separately scoped extensions.
- [ ] Validate face, edge and corner crossings, axis combinations, fast multi-period movement,
  passengers, independent observers and short-period rendering costs. After the prototype is
  stable, extend to Sodium, the other loader and additional versions, including both variants.

## Deferred research

- Shader/Iris parameter interfaces: deferred at the user's request.
- Additional generator integrations and special tiny-world gameplay: assess separately from the
  initial X/Z looping exploration above.

## Reference and validation

The comparison of Seamless World Wrap and Circumnavigate informs the task list, not a replacement
of Biblioteca's tested implementation. New behavior is implemented against each target's native
Minecraft APIs. Tests and graphical evidence stay under ignored `build/` directories; portable
checks belong in `tools/`. Existing released JARs and release tags are preserved.

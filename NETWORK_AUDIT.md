# Periodic coordinate audit

Development scope: Fabric and NeoForge, exact versions in the build matrix. Canonical world data
and the displayed image are separate coordinate frames. Fix a consumer when it needs a nearby
image; do not rewrite every position in an otherwise shared packet.

| Data path | Coordinate rule and implementation | Evidence |
|---|---|---|
| One-shot sounds, particles and level effects | `SeamEffects` chooses the recipient's nearby image and only shares a constructed packet among recipients in that same frame. | `EffectBroadcastChecks`: shared recipient-helper behavior, opposite-side recipients, same-side cache reuse, exclusions, ordinary delegation and native level-event packet serialization. |
| Entity movement/tracking | Canonical entity storage and previous positions remain the authority. Version-specific interpolation consumes continuous nearby targets. | `SeamChecks`, `main100/SeamClientChecks`: players/mobs, queued paths and both directions. |
| Game events | Registry discovery maps sections into the stored period, deduplicates registry identity per dispatch, and presents each listener with a nearby source. | `GameEventChecks`; real sensor/wool/loop-off probes on 26.3. |
| Vibration position sources | Resolve the native target in the particle's trajectory frame. Older renderSignal paths separately choose the current camera image. | `ParticleRenderChecks` and `GameEventChecks` on native version APIs. |
| Position-bearing crystal metadata | The beam target stays canonical. `CrystalBeamYLoopMixin` folds the value returned to the renderer beside the crystal. | Strict native call-site checks; the graphical probe inspects native beam offsets and unchanged metadata. |
| Entity-ID attachment targets | Guardian and fishing targets still reference the original entities. Their visual endpoint differences use the short periodic path; leash endpoints are folded during native extraction/rendering. | `AttachmentRenderChecks` captures native fish-line vertices; `SeamClientChecks` checks leash states; the graphical probe observes guardian beam vectors. |
| Interaction hits | Candidate discovery retains periodic hitboxes. Client picking clips candidate images along the actual ray and returns the hit in the ray's frame. | Native `ProjectileUtil` checks include extended reach, closest forward image and ordinary-world controls. |
| Bundle envelopes | The envelope retains its native dispatch behavior; the contained packets reach the same movement/effect/data consumers. There is no global in-place mutation of shared packets. | Static dispatch audit, plus native per-packet serialization checks. This is not a separate remote multi-client bundle stress test. |

Tests capture independent server-side recipients without real sockets. Actual graphics run in
isolated integrated clients. They do not establish remote cross-loader multiplayer or arbitrary
third-party entity/packet compatibility. Canonical block positions used for world reads, sleeping
locations and entity IDs are intentionally not converted wholesale into camera coordinates.

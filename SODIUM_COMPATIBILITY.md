# Sodium compatibility

Verified on 2026-11-04 for Biblioteca `1.0.4-dev.1`.

All **82 selected loader/version/variant combinations passed** packaged class transformation in the frozen baseline.
The full builds provide periodic terrain, particles, entity copies and cloud layers through the
adapters described below. Graphical coverage is reported separately from loading coverage.

Current development artifacts restore the vanilla falling-block timeout at the user's request.
They are recorded in `build/falling-timeout-restore/report.json`. Comparison of all 82 JARs against
those frozen baseline artifacts found only `FallingBlockEntityYLoopMixin.class` changed; all other
classes/resources, including Sodium adapters, are identical. The Sodium/graphics results below
retain their original hashes and were not rerun for this timeout-only edit.

## Integration

- Legacy and modern Sodium terrain paths reuse native geometry, buffers, shaders and transparency indices.
- Periodic visibility, seam block/light/biome snapshots and opposite-side cache invalidation are adapted.
- Cloud wrappers cover the installed 6/7/9/10-argument APIs, including Sodium 0.5.8 on 1.20.5.
- Optimized particle callbacks and the disabled-optimization fallback preserve live particle state.
- The 1.16.5 block-entity double offset and newer first-person omitted render-state null are fixed.
- Sodium is optional. No-render artifacts omit the adapters and their video controls.

**NeoForge 26.3 requires Sodium 0.9.3-beta.1 or newer when Sodium is installed.** The tested
NeoForge 26.3.0.98 / Sodium 0.9.2 combination fails upstream without Biblioteca. Both Biblioteca
variants now reject 0.9.2 with a dependency-version message; the tested beta passes. This minimum
is specific to 26.3. Other nodes use the releases in the table.

## Exact release matrix

These are class-transformation results for actual packaged JARs. Biblioteca injections remain
required. Sodium optional hooks retain their production `require=0` behavior; the global debug
expected-count check is disabled only for that compatibility harness. Downloaded official files
are checked against the cached release catalog SHA-512; exact tested SHA-256 values are in the report.

### Fabric

| Minecraft | Sodium release | Full | No-render |
| --- | --- | --- | --- |
| 1.16.5 | [mc1.16.5-0.2.0](https://modrinth.com/mod/sodium/version/YAGZ1cCS) | Pass | Pass |
| 1.17 | [mc1.17.1-0.3.4](https://modrinth.com/mod/sodium/version/Fz37KqRh) | Pass | Pass |
| 1.17.1 | [mc1.17.1-0.3.4](https://modrinth.com/mod/sodium/version/Fz37KqRh) | Pass | Pass |
| 1.18 | [mc1.18-0.4.0-alpha5](https://modrinth.com/mod/sodium/version/6YGRDUVT) | Pass | Pass |
| 1.18.1 | [mc1.18.1-0.4.0-alpha6](https://modrinth.com/mod/sodium/version/5JyduDNN) | Pass | Pass |
| 1.18.2 | [mc1.18.2-0.4.1](https://modrinth.com/mod/sodium/version/74Y5Z8fo) | Pass | Pass |
| 1.19 | [mc1.19.2-0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Pass | Pass |
| 1.19.1 | [mc1.19.2-0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Pass | Pass |
| 1.19.2 | [mc1.19.2-0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Pass | Pass |
| 1.19.3 | [mc1.19.3-0.4.9](https://modrinth.com/mod/sodium/version/idtcaIVT) | Pass | Pass |
| 1.19.4 | [mc1.19.4-0.4.10](https://modrinth.com/mod/sodium/version/b4hTi3mo) | Pass | Pass |
| 1.20 | [mc1.20-0.4.10](https://modrinth.com/mod/sodium/version/vgceLbdH) | Pass | Pass |
| 1.20.1 | [mc1.20.1-0.5.13-fabric](https://modrinth.com/mod/sodium/version/OihdIimA) | Pass | Pass |
| 1.20.2 | [mc1.20.2-0.5.5](https://modrinth.com/mod/sodium/version/pmgeU5yX) | Pass | Pass |
| 1.20.3 | [mc1.20.4-0.5.8](https://modrinth.com/mod/sodium/version/4GyXKCLd) | Pass | Pass |
| 1.20.4 | [mc1.20.4-0.5.8](https://modrinth.com/mod/sodium/version/4GyXKCLd) | Pass | Pass |
| 1.20.5 | [mc1.20.6-0.5.8](https://modrinth.com/mod/sodium/version/IZskON6d) | Pass | Pass |
| 1.20.6 | [mc1.20.6-0.5.11](https://modrinth.com/mod/sodium/version/OwLQelEI) | Pass | Pass |
| 1.21 | [mc1.21.1-0.6.13-fabric](https://modrinth.com/mod/sodium/version/u1OEbNKx) | Pass | Pass |
| 1.21.1 | [mc1.21.1-0.8.13-fabric](https://modrinth.com/mod/sodium/version/SMxNOGZ6) | Pass | Pass |
| 1.21.2 | [mc1.21.3-0.6.13-fabric](https://modrinth.com/mod/sodium/version/rLBgU2jc) | Pass | Pass |
| 1.21.3 | [mc1.21.3-0.6.13-fabric](https://modrinth.com/mod/sodium/version/rLBgU2jc) | Pass | Pass |
| 1.21.4 | [mc1.21.4-0.6.13-fabric](https://modrinth.com/mod/sodium/version/c3YkZvne) | Pass | Pass |
| 1.21.5 | [mc1.21.5-0.6.13-fabric](https://modrinth.com/mod/sodium/version/DA250htH) | Pass | Pass |
| 1.21.6 | [mc1.21.8-0.7.3-fabric](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.7 | [mc1.21.8-0.7.3-fabric](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.8 | [mc1.21.8-0.7.3-fabric](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.9 | [mc1.21.10-0.7.3-fabric](https://modrinth.com/mod/sodium/version/sFfidWgd) | Pass | Pass |
| 1.21.10 | [mc1.21.10-0.7.3-fabric](https://modrinth.com/mod/sodium/version/sFfidWgd) | Pass | Pass |
| 1.21.11 | [mc1.21.11-0.8.14-fabric](https://modrinth.com/mod/sodium/version/rkdTcxoT) | Pass | Pass |
| 26.1 | [mc26.1.1-0.8.9-fabric](https://modrinth.com/mod/sodium/version/uGvVQBnw) | Pass | Pass |
| 26.1.1 | [mc26.1.1-0.8.9-fabric](https://modrinth.com/mod/sodium/version/uGvVQBnw) | Pass | Pass |
| 26.1.2 | [mc26.1.2-0.9.2-fabric](https://modrinth.com/mod/sodium/version/tZQ3jqnf) | Pass | Pass |
| 26.2 | [mc26.2-0.9.2-fabric](https://modrinth.com/mod/sodium/version/xJZxADzI) | Pass | Pass |
| 26.3 | [mc26.3-0.9.2-fabric](https://modrinth.com/mod/sodium/version/bAZQdGpg) | Pass | Pass |

### NeoForge

| Minecraft | Sodium release | Full | No-render |
| --- | --- | --- | --- |
| 1.21.11 | [mc1.21.11-0.8.14-neoforge](https://modrinth.com/mod/sodium/version/sC6wj32u) | Pass | Pass |
| 26.1 | [mc26.1.1-0.8.9-neoforge](https://modrinth.com/mod/sodium/version/Fg5Mk6Y3) | Pass | Pass |
| 26.1.1 | [mc26.1.1-0.8.9-neoforge](https://modrinth.com/mod/sodium/version/Fg5Mk6Y3) | Pass | Pass |
| 26.1.2 | [mc26.1.2-0.9.2-neoforge](https://modrinth.com/mod/sodium/version/zg4YQ9EL) | Pass | Pass |
| 26.2 | [mc26.2-0.9.2-neoforge](https://modrinth.com/mod/sodium/version/DmnNKsfS) | Pass | Pass |
| 26.3 | [mc26.3-0.9.3-beta.1-neoforge](https://modrinth.com/mod/sodium/version/SS023RVV) | Pass | Pass |

## Real graphical and physics coverage

Actual graphical runs cover Fabric 1.16.5, 1.17.1, 1.19.4, 1.20.1, 1.20.4, 1.20.5, 1.21.1,
1.21.4, 1.21.5, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3, plus NeoForge 26.3. The 20 runs
include both 1.16.5 drawing backends and the 1.20.1 native-particle fallback. All **154 screenshots**
were inspected. Probe assertions separately check actual draw calls, source updates, seam crossings,
ordinary restoration, live particle ages and available native cloud-buffer identities.

The two 26.3 loaders also pass the complete visual/effects scenarios: repeated terrain platforms,
entity/block-entity copies, distant first-person copies, beam alignment, close shadows, cloud
options/OIT, sculk 10/10 across the seam and 0/0 with wool or looping disabled. Each light comparison
has 6,528 samples with zero mismatches. Cloud buffers are shared across layers.

The falling-block follow-up has nine real-server runs (eight versions plus the 26.3 16-row world),
144 cases against the current timeout-restored artifacts, with sand/gravel/anvil age-601 expiry
and exactly one matching item, multi-period motion, concrete/water, real landing, item/TNT/mob
history and ordinary-world expiry controls. The test chunk is forced and allowed to finish entity
tracking setup before queries run.

Older graphical runs retain their original frozen JAR hashes. The report records current hashes
separately and compares the unchanged renderer classes, excluding the subsequently added
1.20.5-only cloud wrapper and its selector. It never relabels an old binary as the current one.

## Limits and reproduction

This covers the selected releases, not every Sodium release or third-party renderer. Native Sodium
transparency sorting is retained; there is no per-copy global quad re-sort. Iris/shader interfaces
are deferred. No benchmark supports an FPS improvement claim. Other far-LOD renderers need their
own integration. See `PERFORMANCE.md` for the copy/discovery cost model.

Build/export normal runtime configurations, then use:

```powershell
python tools/verify_sodium_matrix.py --loaders fabric --label sodium-check --workers 2 `
  --lwjgl-override 1.20.1=3.3.1 --lwjgl-library-dir "C:/path/to/.minecraft/libraries/org/lwjgl"
python tools/verify_sodium_matrix.py --loaders neoforge --versions 1.21.11,26.1,26.1.1,26.1.2,26.2 --label sodium-neo
python tools/verify_sodium_matrix.py --loaders neoforge --versions 26.3 --include-prereleases --label sodium-neo263
```

The 1.20.1 LWJGL override corrects the development runtime to the game library expected by its
Sodium release. Omit it when the exported runtime already uses 3.3.1. Graphic probes accept an
isolated copied world and explicit extra-mod paths; user instances are not modified.

Evidence: `build/sodium-ports/report.json`, `build/falling-block-fix/report.json`,
`build/sodium-matrix/complete-{fabric,neoforge,neoforge-263}/results.json`, and the SHA256SUMS files.
The original failure audit under `build/sodium-matrix/final/` and the later 1.20.5 cloud failure
are retained as history, not overwritten with passing results.

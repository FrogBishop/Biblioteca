# Sodium compatibility audit

Audit date: 2026-11-02. Biblioteca artifact version: `1.0.4-dev.1`.

**Complete periodic rendering is verified only for Fabric 1.16.5 with Sodium 0.2.0+build.4.**
The remaining Sodium combinations are not fully supported. The native Minecraft regression
matrix is separate and must not be presented as a Sodium compatibility guarantee.

## Findings

- Fabric 1.17–1.21.5 complete builds fail required terrain injections into methods replaced
  by Sodium. Fabric 1.17–1.20 and 1.20.2–1.20.6 also fail the living-particle injection
  at priority 1000. The selected 1.20.1 + Sodium 0.5.13 build has only the terrain conflict.
  Raising a particle priority alone cannot implement the missing terrain renderer.
- Fabric 1.21.6–26.3 selected releases pass class transformation. Sodium uses its own
  terrain pipeline, which bypasses Biblioteca's vanilla terrain-copy submission.
  A real 26.3 + Sodium 0.9.2 run confirms missing terrain platforms at periodic heights.
- All 35 Fabric no-render builds pass transformation with the selected Sodium releases.
  This variant deliberately omits periodic terrain, entity and cloud rendering.
- NeoForge 1.21.11 through 26.2 pass both variants. On NeoForge 26.3.0.98, Sodium 0.9.2
  fails its own `LevelExtractorMixin` even with Biblioteca absent. Sodium 0.9.3-beta.1
  passes both Biblioteca variants; upstream documents this fix in
  [Sodium #3948](https://github.com/CaffeineMC/sodium/pull/3948).

## Exact release matrix

Versions were selected from the [official Sodium release catalog](https://modrinth.com/mod/sodium/versions).
The table reports **class transformation**, not successful world rendering. All required
Biblioteca injections remain required. Sodium's own optional `require=0` hooks retain
their normal production behavior; the global debug-only expected count is disabled.
Sodium 0.4.0 builds for 1.18/1.18.1 are alpha builds, as indicated in their names.

### Fabric

| Minecraft | Sodium release | Complete build | No-render build |
| --- | --- | --- | --- |
| 1.16.5 | [0.2.0](https://modrinth.com/mod/sodium/version/YAGZ1cCS) | Pass | Pass |
| 1.17 | [0.3.4](https://modrinth.com/mod/sodium/version/Fz37KqRh) | Fail: terrain + particle | Pass |
| 1.17.1 | [0.3.4](https://modrinth.com/mod/sodium/version/Fz37KqRh) | Fail: terrain + particle | Pass |
| 1.18 | [0.4.0-alpha5](https://modrinth.com/mod/sodium/version/6YGRDUVT) | Fail: terrain + particle | Pass |
| 1.18.1 | [0.4.0-alpha6](https://modrinth.com/mod/sodium/version/5JyduDNN) | Fail: terrain + particle | Pass |
| 1.18.2 | [0.4.1](https://modrinth.com/mod/sodium/version/74Y5Z8fo) | Fail: terrain + particle | Pass |
| 1.19 | [0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Fail: terrain + particle | Pass |
| 1.19.1 | [0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Fail: terrain + particle | Pass |
| 1.19.2 | [0.4.4](https://modrinth.com/mod/sodium/version/rAfhHfow) | Fail: terrain + particle | Pass |
| 1.19.3 | [0.4.9](https://modrinth.com/mod/sodium/version/idtcaIVT) | Fail: terrain + particle | Pass |
| 1.19.4 | [0.4.10](https://modrinth.com/mod/sodium/version/b4hTi3mo) | Fail: terrain + particle | Pass |
| 1.20 | [0.4.10](https://modrinth.com/mod/sodium/version/vgceLbdH) | Fail: terrain + particle | Pass |
| 1.20.1 | [0.5.13](https://modrinth.com/mod/sodium/version/OihdIimA) | Fail: terrain | Pass |
| 1.20.2 | [0.5.5](https://modrinth.com/mod/sodium/version/pmgeU5yX) | Fail: terrain + particle | Pass |
| 1.20.3 | [0.5.8](https://modrinth.com/mod/sodium/version/4GyXKCLd) | Fail: terrain + particle | Pass |
| 1.20.4 | [0.5.8](https://modrinth.com/mod/sodium/version/4GyXKCLd) | Fail: terrain + particle | Pass |
| 1.20.5 | [0.5.8](https://modrinth.com/mod/sodium/version/IZskON6d) | Fail: terrain + particle | Pass |
| 1.20.6 | [0.5.11](https://modrinth.com/mod/sodium/version/OwLQelEI) | Fail: terrain + particle | Pass |
| 1.21 | [0.6.13](https://modrinth.com/mod/sodium/version/u1OEbNKx) | Fail: terrain | Pass |
| 1.21.1 | [0.8.13](https://modrinth.com/mod/sodium/version/SMxNOGZ6) | Fail: terrain | Pass |
| 1.21.2 | [0.6.13](https://modrinth.com/mod/sodium/version/rLBgU2jc) | Fail: terrain | Pass |
| 1.21.3 | [0.6.13](https://modrinth.com/mod/sodium/version/rLBgU2jc) | Fail: terrain | Pass |
| 1.21.4 | [0.6.13](https://modrinth.com/mod/sodium/version/c3YkZvne) | Fail: terrain | Pass |
| 1.21.5 | [0.6.13](https://modrinth.com/mod/sodium/version/DA250htH) | Fail: terrain | Pass |
| 1.21.6 | [0.7.3](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.7 | [0.7.3](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.8 | [0.7.3](https://modrinth.com/mod/sodium/version/7pwil2dy) | Pass | Pass |
| 1.21.9 | [0.7.3](https://modrinth.com/mod/sodium/version/sFfidWgd) | Pass | Pass |
| 1.21.10 | [0.7.3](https://modrinth.com/mod/sodium/version/sFfidWgd) | Pass | Pass |
| 1.21.11 | [0.8.14](https://modrinth.com/mod/sodium/version/rkdTcxoT) | Pass | Pass |
| 26.1 | [0.8.9](https://modrinth.com/mod/sodium/version/uGvVQBnw) | Pass | Pass |
| 26.1.1 | [0.8.9](https://modrinth.com/mod/sodium/version/uGvVQBnw) | Pass | Pass |
| 26.1.2 | [0.9.2](https://modrinth.com/mod/sodium/version/tZQ3jqnf) | Pass | Pass |
| 26.2 | [0.9.2](https://modrinth.com/mod/sodium/version/xJZxADzI) | Pass | Pass |
| 26.3 | [0.9.2](https://modrinth.com/mod/sodium/version/bAZQdGpg) | Pass | Pass |

### NeoForge

| Minecraft | Sodium release | Complete build | No-render build |
| --- | --- | --- | --- |
| 1.21.11 | [0.8.14](https://modrinth.com/mod/sodium/version/sC6wj32u) | Pass | Pass |
| 26.1 | [0.8.9](https://modrinth.com/mod/sodium/version/Fg5Mk6Y3) | Pass | Pass |
| 26.1.1 | [0.8.9](https://modrinth.com/mod/sodium/version/Fg5Mk6Y3) | Pass | Pass |
| 26.1.2 | [0.9.2](https://modrinth.com/mod/sodium/version/zg4YQ9EL) | Pass | Pass |
| 26.2 | [0.9.2](https://modrinth.com/mod/sodium/version/DmnNKsfS) | Pass | Pass |
| 26.3 | [0.9.2](https://modrinth.com/mod/sodium/version/VHtnT9Q4) | Fail: Sodium/NeoForge ABI | Fail: Sodium/NeoForge ABI |

Additional NeoForge 26.3 check: **0.9.3-beta.1 passes both variants**.

The main matrix has 82 combinations: 57 pass and 25 fail. Of those failures, 23 are
Biblioteca complete-build terrain conflicts and two are Sodium 0.9.2's independent
NeoForge 26.3 incompatibility. The two beta checks and the Sodium-only control are
recorded separately, preserving the original failures.

## Real graphical evidence for Fabric 26.3

Sodium 0.9.2 was loaded in two isolated copied worlds, with the exact published JAR and
the current Biblioteca artifact. All 13 screenshots were inspected.

- Living campfire and portal particles retain their identities and ages 29 → 59 → 89
  through both boundary crossings. Sculk sensors return 10/10 across the seam and 0/0
  with wool or looping disabled. The light comparison has 6,528 samples and no mismatches.
- The short-world scene draws 16 cloud layers, 16 chest/crystal copies, multiple cows,
  eight distant first-person self copies and close-range shadows. Option restoration,
  both crossings and fast/fancy/OIT cloud paths pass the existing visual assertions.
- **Terrain copies are missing.** The entity/cloud assertions do not test terrain and
  their passing result is not full compatibility. Comparing the same scene with the
  native renderer shows one platform under several floating entity copies with Sodium.

Evidence under the ignored `build/` directory:

- `build/sodium-matrix/report.json`: aggregate results, hashes, limits and graphical records.
- `build/sodium-matrix/final/results.json`: all 82 original combinations and per-run logs.
- `build/sodium-matrix/neo263-control/`: Sodium 0.9.2 without Biblioteca.
- `build/sodium-matrix/neo263-beta/`: Sodium 0.9.3-beta.1 with both variants.
- `build/sodium-matrix/terrain-comparison.png`: matched native/Sodium screenshots.
- `build/validation/26.3/periodic-visuals/fabric/sodium-092/run-20261102-164630/`.
- `build/validation/26.3/periodic-effects/fabric/sodium-092/run-20261102-164942/`.

## Reproduce

Export the normal per-version runtime configurations and build the packaged artifacts.
The audit downloads only official Sodium files and verifies their published SHA-512.
Player instances and saves are not modified. Cached version metadata is reused unless
`--refresh-metadata` is requested.

```powershell
python tools/verify_sodium_matrix.py --label sodium-audit --workers 2 `
  --lwjgl-override 1.20.1=3.3.1 `
  --lwjgl-library-dir "C:/path/to/.minecraft/libraries/org/lwjgl"

python tools/verify_sodium_matrix.py --versions 26.3 --loaders neoforge `
  --include-prereleases --label sodium-neo263-beta

python tools/verify_sodium_matrix.py --versions 26.3 --loaders neoforge `
  --variants full --sodium-only --label sodium-neo263-control
```

The 1.20.1 override uses Minecraft's LWJGL 3.3.1 because Sodium 0.5.13 rejects the
Loom development runtime's LWJGL 3.3.2. This is a test-runtime adjustment, not a product
or player-instance modification. Omit it when the exported runtime already has 3.3.1.

`verify_periodic_effects.py` accepts `--extra-mod PATH --label NAME` for isolated extra-mod
graphical runs. Keep those labels separate from the native-renderer baseline.

The unresolved terrain integrations are tracked in [ROADMAP.md](ROADMAP.md).

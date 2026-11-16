# 20w14infinite compatibility

Development builds support the original **20w14infinite / 20w14∞ snapshot on Fabric**,
with a complete artifact and a no-render alternative. Use **Java 17 or newer** and
**Fabric Loader 0.19.5 or newer**. These are snapshot-specific builds, not relabelled 1.16.5 JARs.

NeoForge has no loader release for this snapshot. Its Minecraft version line starts at 1.20.1;
there is no NeoForge snapshot artifact to install. See the [official NeoForge guide](https://docs.neoforged.net/user/docs/).

## Installation

Install one of these development artifacts on the matching snapshot:

- `biblioteca-<mod-version>+20w14infinite.jar`: gameplay and the visual loop.
- `biblioteca-<mod-version>+20w14infinite-norender.jar`: the same gameplay, prediction,
  audio and dimension settings, without periodic rendering or its Video Settings controls.

Both variants have the same mod ID; install only one per instance. Servers and clients should
use matching snapshot builds. The original snapshot ran on older Java, but Biblioteca's shared
implementation requires Java 17+.

The required modules from official Fabric API `0.5.7+build.2-20w14infinite` are bundled unchanged:
`fabric-api-base`, `fabric-commands-v0`, `fabric-networking-v0`, and `fabric-resource-loader-v0`.
No separate Fabric API umbrella is needed. The old umbrella and several unrelated modules use an
obsolete Minecraft version predicate, which conflicts with modern Loader's normalized snapshot
version. This is an [upstream Fabric API issue](https://github.com/FabricMC/fabric-api/issues/3933).
Biblioteca requires the exact normalized game version `1.16-alpha.20.13.inf`.

The snapshot port uses the vanilla renderer. It does not declare Sodium support; no corresponding
official Sodium release is selected by the compatibility matrix.

## Easter egg dimension settings

The dimension attributes screen lists the three vanilla dimensions followed by all **43 native
Easter egg dimensions**, including ones that have not been visited. Names use the original book/
`/warp` strings from the [English Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_20w14%E2%88%9E#Easter_egg_dimensions)
without translation, including `red`, `green`, `blue`, and
`this_is_a_very_long_phrase_that_hopefully_is_not_in_any_dictionary`.

Easter eggs are ordered alphabetically by those names. The screen shows the visible range and
46-entry total, supports the mouse wheel, arrow buttons, Page Up/Page Down and Home/End, and shows
the full original name on hover when a narrow window needs to shorten it. Choices survive paging
and are applied by Done, then saved and synchronized like the vanilla dimension settings.

The catalog uses the exact `_generated:<hash>` keys used by native `/warp`. Opening or editing it
neither registers extra dimension types nor changes the last-page puzzle's remembered passphrase;
configuring a dimension does not cause it to be generated before it is visited.

## Snapshot behavior

Vanilla book-created dimensions retain their native `_generated:<number>` identifiers. Settings
are saved independently by that identifier and survive a server restart, including when a random
dimension is not yet instantiated during loading. The native dimension generator and existing
terrain are retained.

`/biblioteca looping true` configures the current dimension. The dimension argument in
`/biblioteca looping set <dimension-id> true` accepts native registered book dimensions.
Beacon, void-fog and sky-light settings use the same per-dimension policy as the other targets.

The snapshot's integrated server has its own world-loading implementation. Both dedicated and
integrated servers initialize the saved settings before synchronizing players. The old network
transport sends state on join and after configuration changes and clears client state on disconnect.

The complete build includes periodic terrain, entity copies, multiple cloud layers, particle
continuity and sound positioning. It adapts the snapshot's collision iterator, entity collision
boxes, path-node methods, float sound coordinates and native light storage. Features absent from
this Minecraft version, such as sculk vibration systems, are not claimed as available.
Falling blocks retain the native 600-tick expiry and item-drop behavior.

## Build and verification

```powershell
$env:SC_VERSIONS = '20w14infinite'
.\gradlew.bat :20w14infinite:build :20w14infinite:writeValidationConfig
python tools/verify_artifacts.py --versions 20w14infinite
python tools/verify_matrix.py --versions 20w14infinite --checks --workers 2
```

Stonecutter uses `1.16-alpha.20.13.inf` for API comparisons and `20w14infinite` for Minecraft
coordinates, task names and artifact names. Existing release nodes retain their own APIs.

`tools/verify_entity_physics.py --version 20w14infinite --eula-file path/to/eula.txt` starts a
disposable dedicated server and checks real falling blocks, seam landing, concrete/water and
shared movement histories. `tools/verify_infinite_state.py --eula-file path/to/eula.txt` creates
two native book dimensions and verifies commands, independent attributes, packet/NBT round trips
and an actual save/restart. It accepts `--variant norender`.

`tools/verify_infinite_scene.py --world-template path/to/disposable/world --assets-dir path/to/assets
--asset-index 20w14infinite-1.16` exercises native screens, joined-player synchronization, terrain
draws, particle vertices and ages, cloud buffers and entity copies. It also accepts
`--variant norender`. Loom's `:20w14infinite:downloadAssets` task prepares the matching asset index;
the isolated local test assets use the equivalent index name `20w14infinite`.

The probes freeze the actual tested JAR in their run directory. A legacy lifecycle module is
added only to the test instance; it is not an additional production requirement. Client graphical
checks wait for resource loading to complete. Cloud buffer identity is checked within a native
draw pass, allowing vanilla to rebuild the buffer between frames.

Validation logs, frozen artifacts and screenshots are under `build/validation/20w14infinite/`.
The final build report and hashes are under `build/infinite-port/`. Historical reports retain
their original hashes and are not relabelled as tests of newly built artifacts.

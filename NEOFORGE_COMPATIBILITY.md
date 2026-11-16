# NeoForge compatibility

Biblioteca `1.0.4` provides **23 exact Minecraft release targets**, from **1.20.1**
through **26.3**, with complete/no-render pairs. Combined with 36 Fabric targets, this produces
**118 release JARs**. The 20w14infinite snapshot remains a Fabric target.

## Official loader baselines

Each JAR requires its exact Minecraft release. Some short-lived Minecraft releases have only
an official beta NeoForge build; the Minecraft targets themselves are normal releases.

| Minecraft | NeoForge baseline | Game Java | Build backend |
|---|---|---|---|
| 1.20.1 | 1.20.1-47.1.106 | 17+ | ModDevGradle legacyForge 2.0.148 |
| 1.20.2 | 20.2.93 | 17+ | NeoGradle 7.0.116 |
| 1.20.3 | 20.3.8-beta | 17+ | NeoGradle 7.0.116 |
| 1.20.4 | 20.4.253 | 17+ | ModDevGradle 2.0.148 |
| 1.20.5 | 20.5.21-beta | 21+ | NeoGradle 7.0.116 |
| 1.20.6 | 20.6.144 | 21+ | ModDevGradle 2.0.148 |
| 1.21 | 21.0.167 | 21+ | ModDevGradle 2.0.148 |
| 1.21.1 | 21.1.266 | 21+ | ModDevGradle 2.0.148 |
| 1.21.2 | 21.2.1-beta | 21+ | ModDevGradle 2.0.148 |
| 1.21.3 | 21.3.100 | 21+ | ModDevGradle 2.0.148 |
| 1.21.4 | 21.4.161 | 21+ | ModDevGradle 2.0.148 |
| 1.21.5 | 21.5.101 | 21+ | ModDevGradle 2.0.148 |
| 1.21.6 | 21.6.20-beta | 21+ | ModDevGradle 2.0.148 |
| 1.21.7 | 21.7.25-beta | 21+ | ModDevGradle 2.0.148 |
| 1.21.8 | 21.8.57 | 21+ | ModDevGradle 2.0.148 |
| 1.21.9 | 21.9.16-beta | 21+ | ModDevGradle 2.0.148 |
| 1.21.10 | 21.10.64 | 21+ | ModDevGradle 2.0.148 |
| 1.21.11 | 21.11.48 | 21+ | ModDevGradle 2.0.148 |
| 26.1 | 26.1.0.19-beta | 25+ | ModDevGradle 2.0.148 |
| 26.1.1 | 26.1.1.15-beta | 25+ | ModDevGradle 2.0.148 |
| 26.1.2 | 26.1.2.122 | 25+ | ModDevGradle 2.0.148 |
| 26.2 | 26.2.0.93 | 25+ | ModDevGradle 2.0.148 |
| 26.3 | 26.3.0.98 | 25+ | ModDevGradle 2.0.148 |

Pins are stored in each target's `gradle.properties`, using the
[official NeoForge repository](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
and [official MDKs](https://github.com/NeoForgeMDKs). The original 1.20.1 NeoForge is published
under [`net.neoforged:forge`](https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml).

## Installation

Choose one matching JAR per instance:

- `biblioteca-<version>+<minecraft>-neoforge.jar`: gameplay, settings and periodic rendering.
- `biblioteca-<version>+<minecraft>-neoforge-norender.jar`: gameplay, prediction, sound, settings
  and synchronization, with periodic rendering and its three Video Settings controls omitted.

Clients and servers should use matching Minecraft builds. Required MixinExtras libraries are embedded
when the historical loader does not provide the required API; native Jar-in-Jar metadata selects the
library in both variants. Use the game Java target in the table.

## Loader adaptations

| Minecraft range | Native transport |
|---|---|
| 1.20.1 | Original NeoForge API namespace and SimpleChannel, with SRG production packaging |
| 1.20.2–1.20.3 | NeoForge SimpleChannel, with version negotiation and clientbound direction |
| 1.20.4 | Custom payload readers/writers and explicit main-thread dispatch |
| 1.20.5–1.21.6 | StreamCodec registration with a co-registered handler |
| 1.21.7+ | Separate physical-client payload registration |

Every adapter initializes common saved data, registers the statistic and commands, synchronizes
state on login and changes, and clears client state on disconnect. Old extended-reach hooks retain
native attributes, creative allowances and padding while measuring periodic targets.

The original 1.20.1 Mixin cannot inject into interface defaults. Concrete world, block-state, player,
pillager and piglin adapters cover collision, neighbor updates, reach and crossbow behavior. Both
production JARs are remapped to SRG. Validation inspects those release artifacts and uses ModDevGradle's
normal inverse remap for its Mojang-named development game; both hashes are recorded separately.

Other adaptations cover recompiled lambda names, gaze/explosion overloads, record-class access,
older terrain locals and an array-copy compatibility issue. Mixin hooks remain required. Each target
selects the actual implementation instead of making missing hooks optional.

## Building

The shared preprocessing build requires **JDK 25**. Install target toolchains 17, 21 and 25 as needed.
The helper chooses the appropriate Gradle wrapper and exits completed build daemons:

```text
python tools/build_neoforge_matrix.py --versions 1.20.1,1.20.4,1.21.8
python tools/build_neoforge_matrix.py --versions 1.20.2,1.20.3,1.20.5 --workers 2
```

The existing PowerShell entrypoint also accepts every configured target:

```powershell
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 1.20.1
```

1.20.2, 1.20.3 and 1.20.5 use the separate Gradle 8.14.5 wrapper on JDK 21. Shared source export still
runs on JDK 25. Their access-transformer build tool uses JDK 21 to avoid a Windows Java 17 ZipFS
writability-detection issue; the game and mod bytecode targets stay unchanged. Other projects use
the main Gradle 9.8 wrapper. Vanilla asset caches are shared between the historical build backends.

`build` produces both variants, loads each on both physical sides through actual FML, runs the
version-specific regression suite, and collects successful artifacts into `build/distributions`.
1.20.1 development remaps stay in its validation directory and are not collected as release JARs.

## Evidence

The final artifact hashes, per-version native checks, real save/client runs and exact tested Sodium
combinations are recorded in `build/neoforge-backport/report.json`, `validation.md` and `SHA256SUMS.txt`.
Graphical coverage is tracked separately from class transformation. Live runs freeze their artifacts
and use disposable copied worlds; installed instances and user saves are not edited.

The old 1.0.2/1.0.3 JAR cleanup inventory is `build/cleanup-20261116/removed-artifacts.json`. Historical
logs retain their original versions, with removed binaries explicitly listed. Native falling-block
expiry and ender-pearl physics remain as previously specified.

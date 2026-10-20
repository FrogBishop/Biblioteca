# Biblioteca

**A periodic Y axis for Minecraft dimensions.**

> *"La biblioteca es ilimitada y periódica."* — Jorge Luis Borges, *Ficciones* (1944)

Biblioteca is a Fabric mod for **Minecraft 26.3** that turns the Y axis of a dimension into a
ring: the lowest row and the highest row become neighbours. There is no floor to fall out of
and no ceiling to hit; moving past either edge simply continues on the other side, without a
teleport or a command.

The name comes from Borges' *The Library of Babel* — a library that is unlimited and periodic.
Biblioteca makes the vertical axis of a world the same kind of thing.

The mod adds no blocks, items, biomes or world types. It is a per-dimension, opt-in attribute
that also works with existing worlds.

## Features

- **A true vertical ring.** Block access, collisions, entity movement, neighbour updates,
  pistons, fluids, plant growth, light and effects all fold across the seam.
- **Per dimension, opt-in.** The Overworld, The Nether, The End or any modded dimension can
  loop independently. Everything is off by default.
- **A visual ring.** The client mirrors visible terrain above and below, draws entities and
  block entities at the nearest layer, repeats beacon beams and folds the cloud layer.
- **Correct translucency.** Looping dimensions use Minecraft's order-independent transparency
  path, so mirrored water, glass and slime blend correctly.
- **Server ready.** A `-norender` jar ships next to the normal jar for dedicated servers.
- **A statistic.** Every seam crossing is counted in the vanilla statistics screen.

## Requirements

- Minecraft **26.3** (Fabric)
- Fabric Loader **0.19.5** or newer
- Fabric API for 26.3
- Java **25** or newer

Install on both the client and the server for the full experience. Dedicated servers may use
the `-norender` jar, but clients still need the normal jar to see the loop.

## Installation

1. Install Fabric Loader for Minecraft 26.3.
2. Put Fabric API and `biblioteca-<version>.jar` in the `mods` folder.
3. Do not install both jars in the same instance: they share the same mod ID.

## Getting started

For a new singleplayer world, open **Create New World → More → Dimension Attributes** and pick
the dimensions and attributes you want. For an existing world or a server, use:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca beacon true
/biblioteca voidfog true
/biblioteca skylight true
```

`/biblioteca beacon`, `/biblioteca voidfog` and `/biblioteca skylight` act on the
dimension you are standing in. See the player guide for the full command table, the four
attributes and what each one changes.

## Building from source

```bash
./gradlew build          # normal jar and -norender jar in build/libs
./gradlew runClient      # development client
./gradlew runServer      # development dedicated server
./gradlew deployMod      # copy the normal jar into the configured Minecraft instance
```

The deploy task defaults to the 26.3 Fabric instance on Windows and can be pointed elsewhere
with `-PmodsDir=<path>`. The project targets Java 25; Loom 1.18 and Gradle 9.7 are used.

## Documentation

- [modrinth.md](modrinth.md) — player guide: commands, attributes, gameplay effects, rendering,
  storage, limitations and FAQ.
- [PERFORMANCE.md](PERFORMANCE.md) — render-cost model, the current draw-command ceiling and
  how to measure it.
- [CHANGELOG.md](CHANGELOG.md) — release history.

## Credits and license

- Author: **FrogBishop**
- Inspired by Jorge Luis Borges' *The Library of Babel*
- Bug fixes and rendering work: DeepSeek-v4.1-flash
- License: **GPLv3**
- Source: [github.com/FrogBishop/Biblioteca](https://github.com/FrogBishop/Biblioteca)

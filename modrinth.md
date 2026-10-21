# Biblioteca

**Turn a dimension's vertical space into a loop.**

> *"La biblioteca es ilimitada y periódica."* — Jorge Luis Borges, *Ficciones* (1944)

Travel past the top of the world and continue from the bottom, or fall through the bottom and
arrive at the top. Biblioteca makes the two sides neighbours, with continuous movement and a
repeating view of the world above and below you.

The place where the top and bottom meet is the **boundary**. In a normal Overworld, it joins
`y = 320` to `y = -64`.

Looping is optional for each dimension and works with existing worlds. The mod keeps normal
world generation and adds no blocks, items or world types. Existing terrain, including bedrock,
stays in place, so solid blocks can still obstruct a passage through the boundary.

## What you can do

- Enable looping independently for the Overworld, Nether, End or a modded dimension.
- Build and interact across the boundary, with block and entity collisions in the right places.
- Use fluids, redstone, pistons and supported plant growth across the top and bottom.
- See repeated terrain, nearby entities and block entities, periodic beacon beams and clouds.
- See rain and snow across the boundary, with rain splashes, hot-surface smoke and impact sounds.
- Hear positional sounds and see effects from the other side, with matching sound subtitles.
- Track your crossings with **Seam crossings** in the vanilla statistics screen.

## Requirements and installation

- **Minecraft 26.3**, using **Fabric Loader 0.19.5 or newer**.
- **Fabric API** for Minecraft 26.3.
- **Java 25 or newer**.

Install Fabric API and the normal Biblioteca JAR in the client's `mods` folder. Multiplayer
servers also need Biblioteca and Fabric API; use matching Biblioteca versions on both sides.

Dedicated servers can use the `-norender` JAR. Clients need the normal JAR to see the looping
world. Install only one Biblioteca JAR in each instance: both variants use the same mod ID.

## Enable looping

For a new singleplayer world, open **Create New World → More → Dimension Attributes**. Choose
the settings for the Overworld, Nether and End, press **Done**, and create the world.

For an existing world, a server or a modded dimension, use commands. Cheats or operator
permission level 2 are required:

```text
/biblioteca looping set minecraft:overworld true
```

Replace `minecraft:overworld` with the dimension ID you want, such as `minecraft:the_nether`,
`minecraft:the_end` or an ID added by another mod. To turn looping off again:

```text
/biblioteca looping set minecraft:overworld false
```

## Dimension settings

All four settings default to `false`. The three additional attributes affect dimensions where
Y-axis looping is enabled.

| Setting | When enabled (`true`) |
|---|---|
| **Y-axis loops** | Connect the world's top and bottom. |
| **Beacon ignores sky** | Allow beacons to use beams that scan around the loop. Enable this to use beacons in a looping dimension. |
| **No void fog** | Hide the vanilla darkening near the bottom of the world. |
| **No sky light** | Remove sky light; the dimension then relies on block light sources. |

For **No void fog** and **No sky light**, `false` keeps the original effect and `true` disables
it. The world-creation buttons show **Keep fog / Disable fog** and **Keep skylight / Disable
skylight** to make the result clear. Keeping sky light does not add it to a dimension that
normally has none.

| Command | Action |
|---|---|
| `/biblioteca looping list` | List the dimensions with looping enabled. |
| `/biblioteca looping set <dimension> <bool>` | Enable or disable looping for that dimension. |
| `/biblioteca beacon <bool>` | Change Beacon ignores sky in the current dimension. |
| `/biblioteca no_void_fog <bool>` | Change No void fog in the current dimension. |
| `/biblioteca no_sky_light <bool>` | Change No sky light in the current dimension. |

Use `true` or `false` for `<bool>`. Run `beacon`, `no_void_fog` or `no_sky_light` without a value
to see its current setting. These three commands act on the dimension you are standing in.

For example, to use beacons and hide void fog while keeping sky light:

```text
/biblioteca beacon true
/biblioteca no_void_fog true
/biblioteca no_sky_light false
```

Settings are saved with the world and synchronized to connected players.

## Transparency and performance

Biblioteca supports water, stained glass and other translucent terrain both with and without
**Improved Transparency**, Minecraft's OIT option. The mod does not force that option on.

**On newer GPUs that support OIT, we recommend enabling Improved Transparency for better
performance.** Find it under **Options → Video Settings**.

If OIT is unavailable, or you turn the option off, Biblioteca uses sorted transparency for the
repeated terrain. You can switch it off on an OIT-capable GPU to compare the two paths. Sorting
can cost more CPU time, and intersecting translucent faces can still show artifacts that OIT
handles more accurately.

A high render distance shows more repeated terrain and increases rendering work. Very short
dimensions can be more demanding because many copies fit in view. Reduce render distance if
needed.

**Seam preload (sections)**, also in Video Settings, ranges from **0 to 8**. Higher values prepare
more terrain across the boundary ahead of time and can reduce loading hitches, at the cost of
extra work. The default is `0`; each step adds 16 blocks of preload slack.

## Existing worlds and upgrading

You can enable or disable looping in an existing world. It uses the dimension's existing height;
it does not generate extra vertical terrain or duplicate your blocks and entities. Ordinary
entities and block entities are shown at the copy nearest you.

In **0.67.0**, the old `void_fog` and `sky_light` settings were renamed and inverted. To preserve
their previous effects, use the opposite boolean with `no_void_fog` and `no_sky_light`. For
example, keeping the old defaults requires setting both new attributes to `true` in each looping
dimension. See the [manual migration instructions](https://github.com/FrogBishop/Biblioteca/blob/main/CHANGELOG.md#manual-migration)
for the complete command table.

## Compatibility and feedback

Biblioteca changes Minecraft's world and renderer internals. Mods that replace the same systems
may need compatibility work. Shader packs have not been comprehensively tested. The no-render
variant is intended for servers and does not provide the visual loop on a client.

For a bug report, include your Minecraft/mod versions, other installed mods, the log and steps
to reproduce it. For a rendering problem, also include your GPU, render distance, dimension
height and whether Improved Transparency is enabled.

[Source and developer documentation](https://github.com/FrogBishop/Biblioteca) ·
[Report an issue](https://github.com/FrogBishop/Biblioteca/issues) ·
[Release history](https://github.com/FrogBishop/Biblioteca/blob/main/CHANGELOG.md)

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra. Licensed under
[GPLv3](https://github.com/FrogBishop/Biblioteca/blob/main/LICENSE).

# Biblioteca

**Turn a dimension's vertical space into a loop.**

**Both the complete build and the no-render alternative are available in this CurseForge project.**

> *"La biblioteca es ilimitada y periódica."* — Jorge Luis Borges, *Ficciones* (1944)

Travel past the top of the world and continue from the bottom, or fall through the bottom and
arrive at the top. Biblioteca makes the two sides neighbours, with continuous movement and a
repeating view of the world above and below you.

The place where the top and bottom meet is the **boundary**. Its position and the length of
the vertical loop follow the dimension's own world height.

Looping is optional for each dimension and works with existing worlds. The mod keeps normal
world generation and adds no blocks, items or world types. Existing terrain, including bedrock,
stays in place, so solid blocks can still obstruct a passage through the boundary.

## What you can do

- Enable looping independently for the Overworld, Nether, End or a modded dimension.
- Build and interact across the boundary, with block and entity collisions in the right places.
- Use fluids, redstone, pistons and supported plant growth across the top and bottom.
- Decorate and spawn across the boundary: paintings and item frames hang across the seam at any
  height, and a spawn egg puts the mob on the block you clicked instead of inside it.
- See repeated terrain, nearby entities and block entities, periodic beacon beams and clouds.
- Fight mobs and trade with villagers on the other side: they see you, path to you, hit you and
  cross the boundary without a visible jump.
- Lead leashed mobs across the boundary and fight with arrows, snowballs, potions and other
  projectiles. Ranged mobs aim and projectiles hit across the top/bottom seam.
- Choose how sky light behaves: disable it, make it fully periodic, or keep vanilla sky exposure
  while light still travels across the boundary.
- See rain and snow across the boundary, with rain splashes, hot-surface smoke and impact sounds.
- Hear positional sounds and see effects from the other side, with matching sound subtitles.
- Track your crossings with **Seam crossings** in the vanilla statistics screen.

## Downloads and installation

**The complete and no-render editions are both hosted in this same CurseForge project.**
The complete build is the main file. Open the matching release's **Additional Files** section
for the no-render alternative; there is no separate CurseForge project to install.

| Download | What it includes |
|---|---|
| **Complete build** — main file | All gameplay features and the repeating world view; works on clients and dedicated servers. |
| **No-render alternative** — Additional File | The same core gameplay, world settings, networking and sounds, with periodic visual rendering omitted. Useful for dedicated servers. |

Choose the file whose **Minecraft version and mod loader** match your instance exactly.
The file's version labels and dependencies show its supported game version, loader and Java
requirements. Some builds for older Minecraft releases require a newer Java runtime than the
unmodified game.

- **Fabric builds** require Fabric Loader and Fabric API for the selected Minecraft version.
- **NeoForge builds** require the corresponding NeoForge installation and do not use Fabric API.

Place the selected JAR in the instance's `mods` folder. **Install only one Biblioteca variant
per instance**: the complete and no-render builds use the same mod ID and must not be installed
together.

**Multiplayer requires Biblioteca on both the server and clients.** A server may use no-render
while clients use the complete build to see the repeating world. Match the base Biblioteca
release, Minecraft version and loader on both sides. In singleplayer, one installation also
covers the integrated server.

## Enable looping

For a new singleplayer world, open **Create New World → More → Dimension Attributes**. Older
Minecraft versions put the button in **More World Options**. Choose
the settings for each dimension, press **Done**, and create the world. The list includes custom
dimensions from your selected world preset and enabled data packs. After changing data packs,
wait for validation to finish; the page uses the updated dimension list. Scroll to see more
dimensions; older layouts also provide up/down buttons. The sky-setting tooltip includes the dimension ID.

For an existing world or a server, use commands. Cheats or operator
permission level 2 are required:

```text
/biblioteca looping set minecraft:overworld true
```

Replace `minecraft:overworld` with the dimension ID you want, such as `minecraft:the_nether`,
`minecraft:the_end` or an ID added by a data pack or another mod. Press **Tab** after
`/biblioteca looping set `, or after part of an ID, to choose from the loaded dimensions.
To turn looping off again:

```text
/biblioteca looping set minecraft:overworld false
```

## Dimension settings

**Y-axis loops**, **Beacon ignores sky** and **No void fog** start off; **Sky light** defaults to
**Periodic**. The beacon, fog and sky-light settings take effect when looping is enabled for that
dimension.

| Setting | Behavior |
|---|---|
| **Y-axis loops** | Connect the world's top and bottom. |
| **Beacon ignores sky** | Allow beacons to use beams that scan around the loop. Enable this to use beacons in a looping dimension. |
| **No void fog** | Hide the vanilla darkening near the bottom of the world. |
| **Sky light** | Choose **Disabled**, **Periodic**, or **Vanilla sky**. Click to cycle modes; hover for details. |

For **No void fog**, `false` keeps fog and `true` disables it. Sky light has three modes:

| Mode | Effect |
|---|---|
| `disabled` — Disabled | Remove sky light; block light sources still work. |
| `looping` — Periodic | Direct sky requires a column transparent through the whole vertical loop. |
| `hybrid` — Vanilla sky | Direct sky uses vanilla exposure, checking from the cell up to the world's top (using that dimension's actual height). |

Both lit modes spread light across the top/bottom boundary with normal attenuation and occlusion.
Water and terrain samples outside the stored height read the corresponding row across the seam.
A complete bedrock floor blocks all direct sky in `looping` mode; choose `hybrid` to keep normal
daylight above the floor while retaining periodic propagation. Existing chunks rebuild their sky
light when modes change. Dimensions without native sky light do not gain it, and non-looping
dimensions retain vanilla lighting.

| Command | Action |
|---|---|
| `/biblioteca looping list` | List the dimensions with looping enabled. |
| `/biblioteca looping set <dimension> <bool>` | Enable or disable looping for that dimension. |
| `/biblioteca beacon <bool>` | Change Beacon ignores sky in the current dimension. |
| `/biblioteca no_void_fog <bool>` | Change No void fog in the current dimension. |
| `/biblioteca sky_light <disabled\|looping\|hybrid>` | Select the sky-light mode in the current dimension; Tab completes all three choices. |

Use `true` or `false` for `<bool>`. Run `beacon`, `no_void_fog` or `sky_light` without a value
to see its current setting. These three commands act on the dimension you are standing in.
`sky_light` replaces the old `no_sky_light` command and takes mode names instead of boolean arguments.

For example, to use beacons and hide void fog with vanilla sky exposure and periodic light:

```text
/biblioteca beacon true
/biblioteca no_void_fog true
/biblioteca sky_light hybrid
```

Settings are saved with the world and synchronized to connected players.

## Transparency and performance

Biblioteca supports water, stained glass and other translucent terrain. When Minecraft and
your GPU provide the **Improved Transparency** option, Biblioteca follows that setting.
**We recommend enabling it on supported GPUs for better performance.** Look for it in
**Options → Video Settings** when available.

When that option is unavailable or disabled, Biblioteca uses sorted transparency. Sorting can
cost more CPU time, and intersecting translucent faces can still show artifacts. The setting
remains under your control.

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

Dimension settings are saved with the world and retained when updating. Sky light can be
**Disabled**, **Periodic**, or **Vanilla sky** (`hybrid`); choose `hybrid` to retain ordinary
daylight above a bedrock floor while preserving propagation across the boundary.

For worlds using older configuration formats, see the
[manual migration guide](https://github.com/FrogBishop/Biblioteca/blob/multiversion/CHANGELOG.md#manual-migration).
The current `/biblioteca sky_light` command takes mode names rather than boolean arguments.

## Compatibility and feedback

Biblioteca changes Minecraft's world and renderer internals. Mods that replace the same systems
may need compatibility work. Voxy and other far-LOD terrain are not repeated by this vanilla-renderer integration. Shader packs have not been comprehensively tested. The no-render
variant is intended for servers and does not provide the visual loop on a client.

For a bug report, include your Minecraft/mod versions, other installed mods, the log and steps
to reproduce it. For a rendering problem, also include your GPU, render distance, dimension
height and whether Improved Transparency is enabled.

[Source and developer documentation](https://github.com/FrogBishop/Biblioteca/tree/multiversion) ·
[Report an issue](https://github.com/FrogBishop/Biblioteca/issues) ·
[Release history](https://github.com/FrogBishop/Biblioteca/blob/multiversion/CHANGELOG.md)

Author: **FrogBishop**. Inspired by Jorge Luis Borges' *The Library of Babel*.
Development assistance: DeepSeek and GPT. Licensed under
[GPLv3](https://github.com/FrogBishop/Biblioteca/blob/multiversion/LICENSE).

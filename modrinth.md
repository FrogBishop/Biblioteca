# Biblioteca
**A periodic Y axis for Minecraft dimensions.**

> *"La biblioteca es ilimitada y periódica."* — Jorge Luis Borges, *Ficciones* (1944)

Biblioteca is a Fabric mod for **Minecraft 26.3** that turns the Y axis of a dimension into a ring. The lowest layer and the highest layer become neighbours: there is no floor to fall out of and no ceiling to hit. It is a **per-dimension attribute**, opt-in, and it works with existing worlds.

The name comes from Borges' *The Library of Babel* — a library that is unlimited and periodic. Biblioteca makes the vertical axis of a world the same kind of thing.

The mod adds no blocks, items, biomes, or world types. It changes how the existing vertical space behaves.

## Highlights

- **A true vertical ring.** The world height becomes cyclic. Moving past the bottom continues at the top and vice versa; there is no teleport and no command involved.
- **Per dimension, opt-in.** The Overworld, The Nether, The End, or any modded dimension can loop independently. Everything is off by default.
- **Gameplay-aware seam.** Block access, collisions, entity movement, neighbour updates, pistons, fluids, plant growth, light and effects all fold across the seam.
- **A visual ring.** The client mirrors visible terrain above and below, draws entities and block entities at the nearest layer, repeats beacon beams and folds the cloud layer.
- **Correct translucency.** Looping dimensions use Minecraft's order-independent transparency path, so mirrored water, glass and slime blend correctly.
- **Server ready.** A `-norender` jar ships next to the normal jar for dedicated servers.
- **No new content.** Vanilla blocks, items and world generation; the loop is a coordinate and rendering behaviour.

## Requirements

- **Minecraft 26.3** (Fabric)
- **Fabric Loader 0.19.5** or newer
- **Fabric API** for 26.3
- **Java 25** or newer
- Install on both the client and the server for the full experience.

Tested with Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3 on Minecraft 26.3.

## Installation

1. Install Fabric Loader for Minecraft 26.3.
2. Put Fabric API in the `mods` folder.
3. Put `biblioteca-<version>.jar` in `mods` on the client and on the server.
4. On a dedicated server you can use `biblioteca-<version>-norender.jar` instead; it contains the server logic without the client renderer hooks. Clients still need the normal jar to see the loop.
5. Do not install both jars in the same instance — they share the same mod ID.

## Getting started

### New singleplayer world

Create New World → **More** tab → **Dimension Attributes…**

The screen has two columns:

- **Y-axis loops** — turn the ring on or off per dimension.
- **Beacon ignores sky** — let beacon beams skip the vanilla sky check.

Choose the settings for the Overworld, The Nether and The End, press **Done**, and create the world. The choices are applied the first time the new world loads.

The create-world screen is part of singleplayer world creation. Dedicated servers and existing worlds use the commands below.

### Existing worlds and servers

Everything is available through `/biblioteca`:

```text
/biblioteca looping list
/biblioteca looping set minecraft:overworld true
/biblioteca looping set minecraft:the_nether true
/biblioteca looping set minecraft:the_end false
/biblioteca beacon true
```

`/biblioteca beacon` acts on the dimension you are currently in. `/biblioteca looping set` takes any dimension ID, including modded ones.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/biblioteca looping list` | 2 (GAMEMASTERS) | List the dimensions whose Y axis loops. |
| `/biblioteca looping set <dimension> <enabled>` | 2 (GAMEMASTERS) | Enable or disable the loop for a dimension. |
| `/biblioteca beacon` | 2 (GAMEMASTERS) | Show whether beacons ignore the sky in the current dimension. |
| `/biblioteca beacon <enabled>` | 2 (GAMEMASTERS) | Set that attribute for the current dimension. |

Examples:

```text
/biblioteca looping set minecraft:overworld true
/biblioteca looping set mymod:skylands true
/biblioteca beacon false
```

## Per-dimension attributes

| Attribute | Data key | Default | Effect |
|---|---|---|---|
| **Y-axis loops** | `looping` | Off | Wraps `minY` and `maxY`; the dimension becomes a ring. |
| **Beacon ignores sky** | `beacon_ignores_sky` | Off | Beacon beams skip the vanilla sky check and scan around the ring. |

Both attributes are stored with the world and synced to clients. Changing one takes effect immediately; no restart is needed.

In a looping dimension, vanilla's "the beacon must see the sky" rule can never be satisfied, because the dimension has no sky. Without **Beacon ignores sky**, beacons stay dark. Enable it for every looping dimension where you want beacons to work.

## What the loop affects

### Building and interaction

- Placing or breaking a block above `maxY` or below `minY` folds to the opposite edge of the ring.
- The "build limit exceeded" message is suppressed in looping dimensions, so the placement is not rejected at the seam.
- Interaction range uses the shortest distance around the ring; you can reach blocks across the seam.
- Block entities, fluid states, block states and loaded checks fold the Y coordinate, so machines and containers behave normally at the edges.

### Collisions and entities

- Block collisions at the seam treat the row past `minY` as the top row and the row past `maxY` as the bottom row. The top of the world and the bottom of the world are the same plane.
- Entity collisions are measured around the ring, so two entities on opposite sides of the seam collide as if adjacent.
- Entity positions are folded when committed. Minecarts, falling blocks and primed TNT wrap as well, even though they override the normal entity tick.
- Camera interpolation stays continuous across the seam; the player is not teleported or snapped.

### Redstone, pistons and neighbours

- Neighbour updates are delivered at the folded row, so a piston at the top of the world can be powered by a block at the bottom and retract again.
- Pistons can push and pull across the seam. Moving-piston block entities are placed at the folded position instead of being dropped outside the world.
- Doors, observers, rails, redstone wire and other neighbour-driven blocks work through the seam.

### Fluids

- Water and lava flow, connect and spread across the seam.
- Waterlogged blocks and fluid mixing work at the edges.
- Placing or removing fluid at the build limit folds to the other side.

### Plants and growth

- Bamboo, chorus flowers, vines, double plants, grass bonemeal, dripstone, kelp and sugar cane can grow past the build height and continue at the other edge.
- The growth checks that normally stop at `minY`/`maxY` are answered with "no edge" in looping dimensions.

### Lighting

- **Sky light is disabled** in looping dimensions. A ring has no sky, so the sky-light channel is always zero.
- **Block light wraps around the seam**, so torches, lamps and other light sources illuminate the other side.
- **Void fog is disabled** in looping dimensions.
- The world is lit by block light; bring light sources when you explore.

### Beacons

- Without **Beacon ignores sky**, beacons stay dark and provide no beam or effects, because the sky rule cannot pass.
- With it, the beam scans the column around the ring, stops at the first light-damping block, and is drawn once per period.
- The beacon pyramid and its effects work normally.

### Sound, particles and level events

- Block break and place sounds, particles and level events are sent to each listener at the copy of the position nearest to them.
- An effect on the far side of the seam is therefore heard and seen in the right place, instead of being culled by distance.

### Rendering

- Visible sections are drawn again above and below at whole world-height offsets, so the world repeats visibly in both directions.
- Entities and block entities are drawn once, at the periodic layer nearest the camera; they are not duplicated.
- Beacon beams repeat once per period because a beam spans the whole ring.
- Clouds are folded to the nearest periodic layer.

## Rendering notes

- A mirrored section is one more set of draw commands pointing at the **same compiled mesh**. The extra cost is draw calls, not extra mesh memory.
- Copies are only submitted within the render distance and the camera frustum. The **Seam preload** setting keeps a configurable amount of extra slack warm.
- Vanilla sorts a section's translucent quads once, for one viewpoint. A mirrored copy is the same mesh drawn at another height, so that baked order is wrong for the copy; re-sorting the shared mesh for one copy breaks the others. Looping dimensions therefore use **order-independent transparency (OIT)**, which resolves blending per fragment and is correct at every height. The vanilla "Improved Transparency" option may still read as off; Biblioteca forces the render path for the dimension.
- If Minecraft's GPU warning list flags the GPU, the mod does not force OIT. Translucency at the seam may then not blend correctly.
- OIT is not forced in the wireframe debug view, matching vanilla's own rule.

## Client settings

The only client-side setting is the seam preload.

`config/biblioteca-client.json`:

```json
{
  "seamPreloadSections": 0
}
```

- Range: **0–8**; each step is 16 blocks of slack around the far side of the seam.
- `0` means "only preload what is already in view"; higher values compile mirrored sections further ahead of time and reduce hitches when crossing the seam, at the cost of more compile work.
- The same value is available in **Options → Video Settings → Seam preload (sections)**.

## Server deployments

The build ships two jars:

| Jar | Use |
|---|---|
| `biblioteca-<version>.jar` | Client and server, with the full mirrored rendering. |
| `biblioteca-<version>-norender.jar` | Dedicated servers. Same mod logic without the client renderer hooks. |

The `-norender` jar is cut from the normal jar, so the two cannot drift apart. It keeps server logic, networking, coordinate folding, block prediction and the GUI; it removes the mirrored terrain, frustum/BFS workarounds, cloud and void-fog hooks, and the OIT override. It appears in the mod list with a `-norender` version suffix.

Clients still need the normal jar to see the ring. Do not install both jars in one instance.

## Storage and synchronisation

- World data: `saves/<world>/data/biblioteca/looping.dat` in singleplayer, or `<world>/data/biblioteca/looping.dat` on a server.
- The file stores the set of looping dimensions and the set of dimensions where beacons ignore the sky.
- On join, and after every change, the server sends the attribute sets to clients. The client clears its copy on disconnect.
- Attributes persist with the world; they are not stored in the client options file.

## Known limitations

- **No sky light.** Looping dimensions are lit by block light only. This is intentional (a ring has no sky), but caves and large builds will be darker than in a normal world.
- **Beacons need the attribute.** Without **Beacon ignores sky**, beacons stay dark in a looping dimension.
- **The mirror is visual.** The server stores one period. Terrain is drawn repeatedly, but there are no duplicate chunks, blocks or entities; entities and block entities are drawn at the nearest layer only.
- **OIT is forced.** On a GPU flagged by Minecraft's warning list, the mod leaves the transparency path alone and seam translucency may look wrong.
- **Mixin-based.** Biblioteca patches vanilla level, entity, collision, lighting and renderer code. Mods that rewrite the same internals can conflict. Shader packs are not officially tested.
- **Create-world GUI is singleplayer only.** Existing worlds and dedicated servers use the commands.
- **The `-norender` jar has no visual loop.** It is intended for servers, not for clients that want to see the ring.
- **Render distance matters.** More visible copies mean more draw calls; the seam preload setting trades compile work for fewer hitches.
- **The loop period is the dimension's height.** The mod does not generate extra terrain above or below the world; it wraps the height the dimension already has.
- **0.0.x.** The mod is young; internals and behaviour may change between releases.

## FAQ

**Is this a new dimension or world type?**
No. It is a per-dimension attribute. The dimension keeps its normal generation, height and biomes; only the Y axis becomes periodic.

**Does it generate infinite vertical terrain?**
No. It wraps the dimension's existing height. The server still stores one period; the client draws periodic copies of it.

**Can I use it on an existing world?**
Yes. Enable it with `/biblioteca looping set <dimension> true`. The setting is saved with the world.

**Why is everything so dark?**
Sky light is zero in looping dimensions by design. Use torches, lanterns, glowstone or other block light. Block light wraps around the seam.

**Why are my beacons dark?**
Enable **Beacon ignores sky** for that dimension: `/biblioteca beacon true` while standing in it, or the toggle in the create-world screen.

**Do I need the mod on both the client and the server?**
Yes for the full experience. The server needs it for the world logic and coordinate folding; the client needs it to render the ring. Dedicated servers can use the `-norender` jar.

**Does it duplicate entities or blocks?**
No. The server has one period. The client copies terrain draw calls, not entities; entities and block entities are drawn at the nearest layer.

**Can I build above the build limit?**
Yes. Placement above `maxY` folds to the bottom of the ring, and the build-limit message is suppressed in looping dimensions.

**Does it work with shaders?**
Shader packs are not officially tested. Biblioteca changes vanilla rendering internals; if a shader pack conflicts, please open an issue with the log and the pack name.

**Can I turn it off again?**
Yes. `/biblioteca looping set <dimension> false`, or the create-world toggle for a new world. The change is saved and broadcast immediately.

**Does it work in the Nether and the End?**
Yes. Any dimension ID can be enabled. The Nether and the End have their own skyboxes; the loop still applies to blocks, entities, light and collisions.

**What is the "Seam preload" slider?**
It controls how many extra sections (16 blocks each, 0–8) around the far side of the seam are compiled ahead of time. Higher values reduce hitches at the cost of more work.

## Technical notes

Biblioteca folds Y coordinates with `floorMod(y - minY, height) + minY` at the boundaries where vanilla assumes a floor and a ceiling: level block access, collisions, entity positions, neighbour updates, light nodes, packet coordinates and several block-specific growth checks.

The server owns one period of the world. The client mirrors visible sections at whole-height offsets and shares the compiled mesh between copies. Attributes are stored as `SavedData` in the overworld and synchronized through a custom payload. Beacon beams scan one full period and are drawn once per period.

## Credits and license

- Author: **FrogBishop**
- Inspired by Jorge Luis Borges' *The Library of Babel*
- Bug fixes and rendering work: DeepSeek-v4.1-flash
- License: **GPLv3**
- Source: [github.com/FrogBishop/Biblioteca](https://github.com/FrogBishop/Biblioteca)
- Issues: [github.com/FrogBishop/Biblioteca/issues](https://github.com/FrogBishop/Biblioteca/issues)

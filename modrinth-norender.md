# Biblioteca No Render

**Version 1.0.2 · Fabric 1.16.5–26.3 · NeoForge 26.1–26.3**

This is the **no-render alternative** to Biblioteca. It keeps periodic vertical gameplay,
dimension settings, saved-world data, networking, sounds and configuration controls while
omitting Biblioteca's periodic visual rendering.

Passing the top of a looping dimension brings you back through its bottom, and falling through
the bottom returns you to its top. Existing terrain and saved dimension settings are preserved.

It is useful for dedicated servers. Clients that want to see the repeating world should use
the [complete Biblioteca build](https://modrinth.com/mod/biblioteca), which also works on servers.

**Multiplayer requires Biblioteca on both the server and clients.** A server can use this
no-render build while its clients use the complete build. Match the base Biblioteca release,
Minecraft version and loader on both sides. The no-render edition still includes the client
logic needed for synchronized dimension settings and periodic gameplay.

Choose a download matching your exact Minecraft version and loader. Fabric builds also require
Fabric API; NeoForge builds require the corresponding NeoForge installation.

**Install only one Biblioteca variant per instance.** The complete and no-render builds use
the same mod ID and must not be installed together. Match the Biblioteca release and Minecraft
version between clients and servers.

For settings, commands and the complete feature description, see the
[Biblioteca documentation](https://github.com/FrogBishop/Biblioteca/tree/multiversion).

Author: **FrogBishop**. Licensed under
[GPLv3](https://github.com/FrogBishop/Biblioteca/blob/multiversion/LICENSE).
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra.

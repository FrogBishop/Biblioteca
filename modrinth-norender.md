# Biblioteca No Render

**Version 1.0.4 · Fabric: 20w14infinite and 1.16.5–26.3 · NeoForge: 1.20.1–26.3**

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
Fabric API on normal Minecraft releases. **20w14infinite bundles its four required API modules
and does not need a separate Fabric API installation.** NeoForge builds require the corresponding
NeoForge installation; there is no NeoForge build for the snapshot.

Use Java **17** for the snapshot and 1.16.5–1.20.4, **21** for 1.20.5–1.21.11, and **25** for 26.1+.
The [NeoForge guide](https://github.com/FrogBishop/Biblioteca/blob/1.0.4/NEOFORGE_COMPATIBILITY.md)
lists the tested loader versions. NeoForge 26.3 requires Sodium 0.9.3-beta.1 or newer if Sodium is
installed; use Java 21 for the historical NeoForge 1.21.2/Sodium combination.

Version **1.0.4** includes periodic game events and vibrations, movement-history and legacy camera
fixes, diagnostics, older NeoForge targets, and all 43 snapshot Easter egg dimensions in the settings
screen. Falling blocks retain their vanilla timeout. **Seam preload**, **Periodic entity copies**
and **Multiple cloud layers** are absent from this edition's Video Settings because their rendering
features are omitted.

**Fabric and NeoForge are the supported loaders.** The planned Forge port has been cancelled.
NeoForge support will continue to be maintained and expanded to additional Minecraft versions.

**Install only one Biblioteca variant per instance.** The complete and no-render builds use
the same mod ID and must not be installed together. Match the Biblioteca release and Minecraft
version between clients and servers.

For settings, commands and the complete feature description, see the
[Biblioteca documentation](https://github.com/FrogBishop/Biblioteca/tree/multiversion).

Author: **FrogBishop**. Licensed under
[GPLv3](https://github.com/FrogBishop/Biblioteca/blob/multiversion/LICENSE).
Bug fixes and rendering work: DeepSeek-v4.1-flash, GPT-6-Astra.

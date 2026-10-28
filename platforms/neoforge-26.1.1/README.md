# Biblioteca for NeoForge 26.1.1

This is the exact Minecraft **26.1.1** target for Biblioteca **1.0.2**, verified with
**NeoForge 26.1.1.15-beta** and Java 25. It produces separate complete and no-render JARs.

Run from the repository root:

```powershell
.\tools\neoforge-pilot.ps1 verify -MinecraftVersion 26.1.1
.\tools\neoforge-pilot.ps1 status -MinecraftVersion 26.1.1
```

Deliverables:

- `build/distributions/biblioteca-1.0.2+26.1.1-neoforge.jar`
- `build/distributions/biblioteca-1.0.2+26.1.1-neoforge-norender.jar`

Both variants passed real FML client/server checks (complete: 58 server / 80 client target
classes; no-render: 58 / 67). A new-world 1 GB save/stop/reopen test with 49 forced chunks,
three 8x8 excavation/roof scenarios and all four saved attributes also passed.

Use the JAR for this exact Minecraft version and one variant per instance. Build steps,
architecture, full validation scope and limitations are in the
[26.1 family guide](../neoforge-26.1/README.md). This target's evidence is under
`build/neoforge-26.1.1/`.

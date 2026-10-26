# NeoForge 26.3 pilot build

This is an independent Gradle build root for the first NeoForge target. It is not included in
the existing Fabric version matrix. At this checkpoint it prepares the real NeoForge game
environment; it does not yet contain Biblioteca's gameplay integration or produce a mod JAR.

Run from the repository root with JDK 25:

```powershell
.\tools\neoforge-pilot.ps1 prepare
```

The pinned baseline is NeoForge `26.3.0.98` and ModDevGradle `2.0.148`, taken from the official
26.3 MDK and NeoForge source properties. Successful preparation writes
`platforms/neoforge-26.3/build/pilot-environment.txt`.

The runner saves its state and logs before/after Gradle, so an interrupted chat can resume without
guessing whether the preparation succeeded. `.\tools\neoforge-pilot.ps1 status` reads that state.
The underlying Gradle command is `gradlew.bat -p platforms/neoforge-26.3 preparePilot`.

Read [the recovery checkpoint](../../NEOFORGE_PILOT.md) before resuming. The next stage is to
add the small platform boundary and compile the shared 26.3 gameplay code against this patched
environment. A successful dependency preparation is not a successful mod port.

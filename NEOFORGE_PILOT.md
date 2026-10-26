# NeoForge 26.3 pilot / recovery checkpoint

Status: **ready for GitHub Actions validation; local Maven access remains blocked**.

- Baseline: `multiversion` commit `1962bc5` (Biblioteca 1.0.0).
- Scope: one NeoForge target, Minecraft 26.3. Existing Fabric targets remain the reference.
- Run the commands below from the repository root.
- The user has authorized committing/pushing the pilot and downloading its Actions artifacts.
  Publishing a release or installing into a player's instance is outside this step.
- Temporary downloads and full logs: `build/neoforge-pilot/`.

## Resume here

1. Read this file and inspect `git status --short --branch` before changing anything.
2. Check `build/neoforge-pilot/` for dependency metadata and the latest build/verification logs.
3. Finish the earliest incomplete stage below; do not repeat already verified stages without a reason.
4. Update this checkpoint after each meaningful result, including failed commands and their cause.

## Stages

- [x] Confirm official 26.3 source/MDK and pin NeoForge `26.3.0.98`, ModDevGradle `2.0.148`.
- [x] Load ModDevGradle and configure the isolated NeoForge preparation task.
- [ ] Resolve and prepare the actual pinned NeoForge artifacts.
- [ ] Add an isolated NeoForge build target and small platform boundary, sharing gameplay code.
- [ ] Compile and package the NeoForge target with its own metadata/access rules.
- [ ] Run actual NeoForge client/server class loading and focused gameplay regressions.
- [ ] Verify the existing Fabric 26.3 target after shared-code changes.
- [ ] Record proven scope, limitations, artifact paths and the next expansion step.

## Current state

### Retry on 2026-10-26

The user requested a fresh Maven retry after quota reset. The actual `preparePilot` command
was rerun and failed after about 93 seconds at the same `neoform-runtime:2.0.31` dependency.
The direct Gradle request timed out; the reported DNS answers appeared unrelated to the repository.

Read-only diagnostics found an enabled local Windows proxy. An explicitly proxied
Java request also failed. HTTPS DNS probes through that proxy returned 502, and direct Java
probes to the public resolvers timed out. No system proxy or DNS setting was changed.

The current evidence still indicates a network-access problem, not a missing artifact version.
The retry log is `build/neoforge-pilot/prepare-2026-10-26-retry.log`; diagnostic logs remain in
the same directory. Dependency preparation and gameplay integration are still incomplete.

The starting worktree was clean. The existing Fabric production source and root build are unchanged.
An independent build lives in `platforms/neoforge-26.3`; its JAR task is disabled until there is a
real integration, so an empty bootstrap cannot be mistaken for a working mod.

Verified official inputs:

- `https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/main/gradle.properties`
  specifies Minecraft 26.3 and NeoForge 26.3.0.98.
- `https://raw.githubusercontent.com/NeoForged/NeoForge/26.3.x/gradle.properties`
  specifies ModDevGradle 2.0.148, Java 25, FML 12.0.12 and MixinExtras 0.5.5.
- The Gradle Plugin Portal metadata independently confirms ModDevGradle 2.0.148.

Network probes: Python's configured proxy returned HTTP tunnel 502 for NeoForge Maven. A direct
Java request timed out, including a retry with IPv4 preferred. GitHub and the Gradle Plugin Portal
were reachable. These are network errors, not evidence that the pinned versions are missing.

Latest actual result: `preparePilot` reached `:createMinecraftArtifacts`, then failed resolving
`net.neoforged:neoform-runtime:2.0.31` from the official Maven. Gradle reported a connection timeout.
The full run took about two minutes and its log is `build/neoforge-pilot/prepare.log`.
There is no prepared NeoForge game and no working NeoForge Biblioteca JAR yet.

The Gradle error showed suspicious DNS results for `maven.neoforged.net`; attempts to cross-check
with public HTTPS DNS endpoints also failed. Do not silently replace official artifacts with an
unverified mirror or assume the dependency version is missing. Restore working access to the
official repository before the next stage.

Next command, from the repository root with JDK 25 and working Maven access:

```powershell
.\tools\neoforge-pilot.ps1 prepare
```

The runner records `running` before starting, and `prepared` or `failed` afterward, in
`build/neoforge-pilot/state.json`. Inspect it with `.\tools\neoforge-pilot.ps1 status`.
If quota or the app interrupts a turn, a remaining `running` state is not proof of success.
Continue to shared-code changes only after the patched artifacts are available for compilation
and inspection. This preparation project deliberately has no gameplay entrypoint and disables JAR
output until the integration stage.

Local verification completed:

- `gradlew.bat -p platforms/neoforge-26.3 help --offline` succeeds (`configuration.log`).
- `tools/neoforge-pilot.ps1 prepare -Offline` fails as expected for the missing NeoForm dependency
  and records a nonzero failure state (`prepare-offline.log`). The failure-recording path was checked.
- Existing Fabric source, settings and build files have no diff from the baseline.

An isolated Actions workflow, `.github/workflows/neoforge-pilot.yml`, is prepared. Changes
to the pilot build trigger it after commit/push, allowing official Maven access to be tested on
GitHub's runner. It uploads only preparation logs and the environment marker, never Minecraft
binaries or an empty mod. Its first pushed run will establish whether the pinned environment
can be prepared on GitHub's runner. Download the resulting evidence before advancing stages.

The agent cannot guarantee or control account quota availability. Recovery relies on saved files,
small changes and explicit validation logs rather than on finishing one uninterrupted chat turn.
Keep incomplete work visible as incomplete; do not claim a successful port from compilation alone.

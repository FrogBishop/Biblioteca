# Publishing Biblioteca

`tools/publish.py` prepares exact-version releases and calls **Mod Publish Plugin 2.2.1**
in the independent `publishing/` Gradle project. It uses the repository's Gradle wrapper
without configuring the game builds unless `--build` is requested. Requirements are Java 25
and Python 3.11 or newer; building the NeoForge 1.21.11 target also needs a Java 21 toolchain.
No additional Python packages are needed.

Each Minecraft/loader pair is handled according to the destination:

| Destination | Complete build | No-render build |
|---|---|---|
| CurseForge | Primary file | Additional File of that release |
| Modrinth main project | One executable JAR per version | Never attached to the main release |
| Optional separate Modrinth no-render project | Not uploaded here | One executable JAR per version |

Modrinth [Content Rules §5.7–5.8](https://modrinth.com/legal/rules/) reserve additional files
for designated supplementary content and distinguish functionality across projects. This
publisher uses a separate project for the no-render alternative rather than grouping the
two executable mods in one version or silently mixing variants in the main project.
Without a separate project ID, Modrinth publishes only the complete build and reports that
no-render was not selected. The complete build also works on dedicated servers.
Both variants retain their original filenames.
Fabric releases require Fabric API. NeoForge releases do not declare a Fabric API dependency.
Minecraft compatibility is one exact version per release. Both variants support clients and
servers, although clients need the complete variant for the periodic terrain view.

## Credentials outside the checkout

The default credential file is **`../biblioteca-publish.local.json`**, in the repository's
parent directory. Copy the blank format from `publishing/credentials.example.json` there and
fill in the required project IDs and tokens. The no-render project field is optional:

```json
{
  "MODRINTH_PROJECT_ID": "",
  "MODRINTH_NORENDER_PROJECT_ID": "",
  "CURSEFORGE_PROJECT_ID": "",
  "MODRINTH_TOKEN": "",
  "CURSEFORGE_TOKEN": ""
}
```

Use Modrinth's stable eight-character project ID and CurseForge's numeric project ID, as
strings. Use a Modrinth personal access token and a CurseForge author/upload API token.
`MODRINTH_NORENDER_PROJECT_ID` is optional. Set it only after creating a separate Modrinth
project for the no-render edition; it must differ from `MODRINTH_PROJECT_ID`. The same Modrinth
token is used and must have access to both projects. Existing files with only the original
four fields remain valid. The publisher does not create projects.

Account passwords and browser cookies are not used. The same named environment variables
override file values, so GitHub Actions Secrets can supply tokens in an external workflow.

`--credentials-file PATH` can select another file, but the resolved path must stay outside
the repository, including symlink targets. Dry runs never open this file or pass tokens to
Gradle; they use placeholder project IDs unless the project-ID environment variables are set.
The `.gitignore` also excludes accidentally copied `*publish.local.json` files.

For Modrinth, grant **Create versions, Read versions, Write versions, Read projects and
Write projects**. The project permissions are needed to synchronize the project page from
`modrinth.md`. See the [plugin's token documentation](https://modmuss50.github.io/mod-publish-plugin/platforms/modrinth/#token-options)
and [CurseForge's upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api).

Tokens are passed only to the publishing process. Build and validation subprocesses do not
receive them. Publishing uses a temporary Gradle project cache outside the checkout, disables
configuration/build caching, and uses a single-use daemon. This matters because the plugin
declares its access token as a task input. The wrapper redacts token values from captured
process output. Do not add tokens to Gradle command-line properties or enable diagnostic
environment dumps in your own wrappers.

## Preview and publish

All commands below run from the repository root. The default is a plugin **dry run**:

```powershell
# Preview both loaders' existing 26.3 JAR pairs on both platforms.
python tools/publish.py --versions 26.3

# Build and run existing regression checks before previewing selected NeoForge targets.
python tools/publish.py --versions 1.21.11,26.1,26.1.1,26.1.2,26.2,26.3 --loaders neoforge --build

# Build, validate and preview every configured Fabric and NeoForge target.
python tools/publish.py --build

# Actually publish the already built and inspected files, then synchronize Modrinth's page.
python tools/publish.py --publish
```

`--versions`, `--loaders` and `--platforms` accept comma-separated lists or `all`.
`--platforms modrinth` or `--platforms curseforge` limits the destination. `all` expands the
version nodes in `stonecutter.properties.toml` and the actual NeoForge project directories;
it does not pretend that older Minecraft versions already have NeoForge targets.

`--modrinth-norender-project-id ID` can override the optional no-render project ID, including
for a dry run without opening the credential file. Modrinth no-render releases have a distinct
version number ending in `-norender` and are routed only to that separate project. The generated
manifest lists every platform publication and its exact files/project. Manifest schema 1 from
the earlier two-JAR Modrinth configuration is rejected; generate a new preview before publishing.

The default mod version comes from `stonecutter.properties.toml`, shared with both loaders.
`--mod-version` selects existing artifacts from another release; it cannot be combined with
`--build` when it differs from the configured version. A missing current-version artifact is
an error, even if older JARs happen to be present in `build/distributions`.

The matching section of `CHANGELOG.md` supplies the version changelog. `--changelog FILE`
uses a separate Markdown file, and `--release-type release|beta|alpha` selects its channel.
`--plan-only` prepares snapshots and a manifest without starting the plugin.

Without `--build`, the publisher validates artifact metadata, exact Minecraft/loader/version
requirements and file hashes. It does not claim to rerun gameplay regressions on existing
files. With `--build`, the selected Fabric targets run the matrix checks and artifact audit;
each selected NeoForge target runs its packaged client/server checks through its `build` task.
The publisher copies each JAR into a release snapshot and checks its hash again in Gradle
before any upload, preventing later builds from silently changing the selected files.

## Project-page content

The complete contents of **`modrinth.md`** become the Modrinth project's description. This is
separate from the version changelog. Description synchronization runs after file publication,
so its failure does not discard successful file-upload receipts or require another JAR upload.
When the separate no-render project is configured, its description is synchronized from
`modrinth-norender.md`, which describes that variant's actual behavior. The main project always
uses `modrinth.md`.
To preview or update only the description:

```powershell
python tools/publish.py --description-only
python tools/publish.py --description-only --platforms modrinth --publish
```

**CurseForge limitation:** its documented author upload API and the plugin do not provide a
project-description update operation. Every run therefore also writes `curseforge-description.md`,
an exact copy of `curseforge.md`, for the author dashboard's Markdown editor. That source avoids
release/version-number lists and explains that both complete and no-render downloads are in
the same CurseForge project. The result explicitly
marks this as manual; it does not report the CurseForge project page as synchronized. See the
[official API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api)
and [project description editor](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

## Results and interrupted uploads

Run output stays under `build/publishing/runs/`: the selected JAR snapshots, manifest,
changelog, description copies, sanitized log and plugin responses. The shared ledger is
`build/publishing/state.json`. Preserve it when continuing a release from this checkout.
These generated files are ignored by Git and contain no credentials.

Confirmed successful platform/version uploads are skipped on later runs. A version already
recorded with different files or release metadata is rejected. Modrinth is additionally checked
against its remote version list and SHA-512 file hashes, including the primary-file selection.
CurseForge resuming relies on the local ledger; its author API does not expose the same listing
operation. Before first publishing an already manually released CurseForge version, check it
in the author dashboard. Moving to another machine or an ephemeral CI runner requires preserving
the ledger; it is not a distributed release database.

Upload POST retries are disabled because a timed-out request may already have created a file.
Each item is marked `inflight` before the request. A dependent receipt task records success only
after the plugin's asynchronous worker finishes. If the process stops between those steps, the
next run recovers a completed plugin receipt when available. Otherwise it stops on the unconfirmed
item and preserves successful items from either platform.

After inspecting the website, use one of these explicit local reconciliation commands:

```powershell
# Only when neither the main nor the additional file was uploaded:
python tools/publish.py --mark-not-uploaded "curseforge:PROJECT_ID:1.0.3+26.3"

# Only after confirming BOTH the main file and its no-render additional file exist:
python tools/publish.py --record-upload "curseforge:PROJECT_ID:1.0.3+26.3" FILE_ID
```

If CurseForge contains only the primary file, first attach the missing no-render file in the
dashboard before recording success. Reconciliation changes only the local ledger; it does not
delete or replace published files. A missing Modrinth project-description update can always be
retried separately with `--description-only`.

For implementation verification, run `python -m unittest discover -s tools/publishing-tests -v`.
After a dry run has cached the plugin, `python tools/publishing-tests/plugin_integration.py`
also exercises the real plugin against a loopback HTTP fixture using synthetic credentials.
It checks upload metadata, additional files, confirmed receipts, resume behavior and an
additional-file failure. Gradle runs offline and public HTTPS requests are blocked in that test.

# NeoForge 1.21.10

This exact-version target uses official NeoForge **21.10.64** and Java **21+**
complete/no-render artifacts. See the [shared guide](../../NEOFORGE_COMPATIBILITY.md) for installation,
API adaptations, build toolchains and validation scope.

From the repository root:

```text
python tools/build_neoforge_matrix.py --versions 1.21.10
```

Both variants are checked on client and server before collection into `build/distributions`.
Per-target evidence is under `build/neoforge-1.21.10/validation`; the final inventory is
`build/neoforge-backport/report.json`.

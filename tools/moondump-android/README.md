# MoonDump Android (experimental)

This is an isolated Android ARM64 extractor prototype. It is deliberately placed on the `moondump/android-build` branch of TOTK-recomp; the recompilation project's `main` remains unchanged.

- Source archive: `source.tar.xz`. This contains Java Android sources and the Hactool patch script, with no keys or game content.
- Workflow: `../../.github/workflows/build-moondump-android.yml`.
- The workflow fetches ISC-licensed SciresM/hactool, applies a resume-by-completed-file patch and compiles an Android APK.
- This is an **alpha**, not yet validated on a phone. In particular, title keys from NSP tickets aren't auto-imported, updates haven't been validated and output can need many GB of temporary NCAs.
- Direct file writes require explicit Android all-files access.

Source original: https://github.com/SciresM/hactool

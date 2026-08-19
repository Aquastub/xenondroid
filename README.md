# XenonDroidCC

An Android app that compiles C/C++ (from any folder you point it at)
using a `powerpc-xenon-elf` cross-compiler that runs *on the phone*,
built by GitHub Actions so you never need a PC.

## Honest status of this scaffold

This is a real, structurally complete starting point — not a working
product yet. Three pieces, in order of how solid they are:

1. **The Android app (solid).** Folder pickers via Storage Access
   Framework, staging sources into app-private storage, running the
   compiler as a subprocess, streaming build log to screen, writing
   the result back to your chosen output folder. This part follows
   normal Android patterns and should work close to as-is.

2. **The GitHub Actions workflow (the hard, unproven part).**
   `.github/workflows/build-toolchain-and-apk.yml` attempts to cross-
   build binutils+gcc with **host = Android/ARM64** and
   **target = powerpc-xenon-elf** (a "Canadian cross" build — the
   compiler itself runs on ARM, but it emits PowerPC code). This is
   the single hardest part of the whole project. The xenon-toolchain
   project was never designed for an Android host, so the configure
   flags in the workflow are a starting guess, not a verified recipe.
   **Expect the `Cross-build gcc stage` job to fail the first several
   times** — that's normal for this kind of cross-cross build, not a
   sign the idea is broken. Iterate on the configure/CC flags based
   on the actual error output.

3. **ELF → XEX conversion (not implemented yet).** `MainActivity.kt`
   compiles down to a plain `.elf` and has a `TODO` where the
   `elf2xex`/`imagexex` step from xenon-toolchain needs to run to
   produce something XenDroid can actually load. That tool would also
   need the same Android-hosted cross-build treatment as gcc.

## How to actually use this

1. Push this repo to GitHub.
2. Run the "Build XenonDroidCC (toolchain + APK)" workflow
   (Actions tab → Run workflow).
3. Watch the `build-toolchain` job. When (not if, the first time)
   it fails, read the error, adjust the configure flags in the
   workflow file, commit, and re-run. This loop is the real work.
4. Once the toolchain job produces real binaries, `build-apk` will
   package them into the APK automatically and upload it as a
   downloadable artifact — that's the "no PC needed" payoff.
5. Install the APK on your phone, pick a source folder and output
   folder, hit Compile.
6. Wire up ELF→XEX conversion (step 3 above) before expecting
   anything to actually load in XenDroid.

## Where this fits with RADSLA / TRTF

Same as the libxenon `xenon-hello` scaffold from before: this doesn't
convert Clickteam Fusion projects. It's the delivery mechanism for
hand-ported C/C++ game logic — write it in the folder this app points
at, compile it on-device, test the `.xex` in XenDroid.

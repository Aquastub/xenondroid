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
   the single hardest part of the whole project.

   There is no separate `Free60Project/xenon-toolchain` repo — an
   earlier version of this workflow tried to clone one and failed
   with a git credential-prompt error, because `git clone` on a
   nonexistent/private https URL tries to prompt for a username, and
   a CI runner has no terminal to answer it. The real toolchain build
   script lives inside `libxenon` itself, at
   `libxenon/toolchain/build-xenon-toolchain`. The workflow now
   clones only `libxenon` and patches that script (via `sed`) to add
   `--host=aarch64-linux-android` and an NDK `CC` to its configure
   calls, instead of hand-writing configure invocations against
   source paths that didn't exist.

   That patch is still a first guess, not a verified recipe — the
   script downloads old versions (e.g. gcc 4.6.1-era) that predate
   modern Android NDK conventions, so **expect the toolchain job to
   fail again, differently**. Read the actual build log it uploads,
   adjust the `sed` pattern or add flags the log asks for, and
   re-run. Iterating on real error output is the only way to move
   this forward — I can't run or test the workflow from here.

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

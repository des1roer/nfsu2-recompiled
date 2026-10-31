# Handling a new/different SPEED2.EXE build vs. upstream

## The problem

The lift pipeline (pcrecomp) translates one specific `SPEED2.EXE` byte-for-byte:
every hook site, HUD offset and widescreen patch in `src/runtime/widescreen.c`
is a hardcoded virtual address. Two copies of "the same" game version can
still differ in code layout — different compiler/linker settings, a
different crack/SecuROM-strip history, etc. — which shifts every address
after the divergence point. A build that isn't byte-identical to the
documented reference exe will silently hook the wrong instructions, or
crash, unless its own address table is used.

Upstream (`codepdbh/nfsu2-recompiled`) ships one documented reference build
(README "Tested Game Build") plus one already-supported alternate,
`f4bb0d99` (the R36S port, PR #683). This fork added a third, `90d73842`,
for the maintainer's own exe (shifted ~+0x10 from the documented build in
one region).

## The mechanism: `NFSU2_EXE_BUILD`

One environment variable selects the build at **two separate stages** that
must agree:

1. **Lift time** (`scripts/run_lift.py`): reads `NFSU2_EXE_BUILD`, loads
   `config/hooks-<build>.txt` (or `config/hooks.txt` for the empty/default
   case) as the set of hook-site addresses passed to the `Lifter`.
2. **Compile time** (`CMakeLists.txt` and `android/app/src/main/cpp/CMakeLists.txt`):
   the same variable (as a CMake cache string, seeded from the env var)
   selects a `-DNFSU2_EXE_<BUILD>` compile define. `src/runtime/widescreen.c`
   picks its `k_sites[]` address table and the few build-specific constants
   (e.g. `HUD_SCALE_X`) with `#if defined(...)` on that same define.

Both stages fall back to the documented build when the variable is empty.
An unrecognized value is a hard `FATAL_ERROR` at configure time (CMake),
pointing at what to add.

## Adding support for a new exe build

1. Pick a short tag for it — the convention is the first 8 hex chars of a
   hash identifying the binary (`f4bb0d99`, `90d73842`), not anything
   descriptive.
2. Diff it against the documented build to find the address deltas:
   `pefile` for the PE/section layout, `capstone` to disassemble the
   regions `widescreen.c` hooks and locate the equivalent instructions in
   the new binary (this is how the ~+0x10 shift for `90d73842` was found —
   see the session that produced `df12e39`). A whole-build hash match isn't
   expected; only the specific hook-site regions need to line up.
3. Generate `config/hooks-<tag>.txt` with that build's own addresses in the
   same order as the documented `config/hooks.txt`.
4. Add an `elif(NFSU2_EXE_BUILD STREQUAL "<tag>")` branch (both
   `CMakeLists.txt` and the Android one) defining `NFSU2_EXE_<TAG>`, and a
   matching `#elif defined(NFSU2_EXE_<TAG>)` arm in `widescreen.c`'s
   `k_sites[]` (and `HUD_SCALE_X` etc. if those differ for the new build
   too).
5. Update the CACHE STRING help text listing valid values, so the
   `FATAL_ERROR` message stays accurate.

## Rule for anything going to upstream

This fork's own `main` defaults `NFSU2_EXE_BUILD` to `90d73842` for local
convenience (see `CMakeLists.txt`/`android/app/src/main/cpp/CMakeLists.txt` —
`NFSU2_EXE_BUILD_INIT` falls back to `"90d73842"` when the env var isn't
set). **Never carry that default into a PR to `codepdbh/nfsu2-recompiled`.**
Upstream's default must stay the documented build (empty string) for every
user who isn't running this maintainer's own exe. The cherry-picked PR
(codepdbh/nfsu2-recompiled#2119) only *adds* the `90d73842` branch as an
opt-in option and leaves the default alone — that's the pattern to repeat:
new build support is additive, the default never moves.

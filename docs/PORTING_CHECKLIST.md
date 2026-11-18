# Porting checklist (repeatable)

The procedure used for 1.21.1 → 26.1.2, written so it can be repeated for the next Minecraft version or for a
pack update. Details, commands and the reasons behind each step: `LEARNING_LOG.md`. Commands run in the JDK dev
container unless marked HOST.

## 0. Before you start

- [ ] Read the target pack's versions, not the newest releases: `mods/` + the pack manifest → `docs/PORT_BASELINE.md`
      (MC, NeoForge, Java, AE2 and every AE2 addon, JEI).
- [ ] Fetch the AE2 sources at the pack's exact tag (`v<version>`) next to the repo, plus the old tag, for
      "Rosetta stone" diffs (how did AE2 itself port the same call?).
- [ ] Prove the OLD branch builds and its tests pass (baseline numbers to compare against).
- [ ] New branch `<mc-version>-neoforge` from the old one.

## 1. Build tooling, one change per step, green after each

- [ ] Change the build plugin / Gradle wrapper / Java toolchain **separately**, still on the old MC version;
      compare test counts and jar contents with the baseline after each step.
- [ ] Then bump MC/NeoForge/AE2/JEI + `mods.toml` ranges in one coordinated edit. Goal: Gradle configures and
      resolves dependencies (`dependencies --configuration compileClasspath`). A failing `compileJava` is expected.
- [ ] Check: is the new MC obfuscated (mappings needed)? Which maven hosts the AE2 version? Does NeoForge bundle
      MixinExtras? Which Mixin `compatibilityLevel` do the pack's own mods use?

## 2. Compile fixes

- [ ] Make javac list ALL errors (`-Xmaxerrs`), group them by cause (missing symbol, changed signature, …).
- [ ] Fix one group per commit. For each replacement, find it in the new sources (MC sources jar from the build,
      AE2 tag, NeoForge jar via `javap`), never guess. Note behaviour changes for the in-game checklist.
- [ ] Get the tests compiling and green; adapt tests, never delete them. Tests that need Minecraft go to the FML
      task.

## 3. Mixins (javac does not check them)

- [ ] Inventory: every Mixin's target class still exists? (`@Pseudo` targets of optional mods are skipped.)
- [ ] The Mixin apply audit test (`test` task, FML): every non-optional Mixin applies on the server side.
- [ ] Method level: handler parameters must match the new target signature (`@Inject` needs the exact
      parameters); `@At` targets inside methods must still exist (`javap -c` on the target).
- [ ] Watch for: anonymous classes (`Outer$1`), methods moved to a superclass, generic signatures, renamed
      render/input hooks, save hooks (`ValueInput`/`ValueOutput`).

## 4. Runtime: the TEST instance loop (HOST, PowerShell)

- [ ] `scripts/make-test-instance.ps1` (copy of the pack instance with its own uuid) and
      `scripts/make-minimal-instance.ps1` (AE2 + GuideME + this mod only).
- [ ] `scripts/run-test.ps1 -Deploy`: backs up worlds, deploys the jar, launches, joins the world, checks the log.
      Rollback: `scripts/deploy-test.ps1 -Rollback`.
- [ ] After each session: which of our Mixins were applied (`logs/debug.log`: "Mixing … from
      appliedenhancements.mixins.json"), errors naming the mod, crash reports.
- [ ] Work through `docs/INGAME_CHECKLIST.md`. GUI bugs to expect after a GUI API change: draw order (things
      behind other things), coordinates (offsets applied twice or not at all), clipping (`enableScissor`), HUD
      messages hidden behind screens.
- [ ] Save/load hooks: prove both halves (data on disk via `find-nbt-key.py`, read back without a cancel/recover
      log line).
- [ ] Dedicated server: the pack's official server files in a container, our jar added; join and use the
      features that touch client-only classes; then grep the server log for `NoClassDefFoundError`.
- [ ] Without the optional mods (minimal instance).
- [ ] Other addons: shared AE2 methods and hook kinds (`docs/ADDON_COMPATIBILITY.md`).

## 5. When FTB Evolution 2 updates (same Minecraft version)

- [ ] Read the new `mods/` versions; diff against `docs/PORT_BASELINE.md`. Anything AE2-related changed?
- [ ] AE2 changed → update `ae2_version` in `gradle.properties`, fetch the new AE2 tag, diff the classes our
      Mixins target (`git diff vOLD vNEW -- <files>`), run `./gradlew build` (includes the Mixin audit test).
- [ ] JEI changed → re-check the JEI internals we use (risk list in `docs/PORT_SUMMARY.md`).
- [ ] ExtendedAE / AdvancedAE / ae2wtlib changed → they are not compile dependencies: start the TEST instance
      and run the parts of the in-game checklist that use them.
- [ ] Recreate the TEST instance from the updated pack (`make-test-instance.ps1`; delete the old one in Prism
      first), deploy, run the checklist's startup section, then the areas touched by the update.
- [ ] Bump the mod version and write release notes (behaviour changes from `docs/PORT_SUMMARY.md`).

## 6. Wrap-up

- [ ] Summary "compiles" vs "verified in game", risk list (`docs/PORT_SUMMARY.md`).
- [ ] Clean up temporary worktrees, Docker volumes and test servers.

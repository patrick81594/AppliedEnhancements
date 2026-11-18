# Port summary: Applied Enhancements 1.21.1 → 26.1.2

Branch `26.1.2-neoforge` (fork of `AyaYumi/AppliedEnhancements`, based on `1.21.1-neoforge` 275d8aa,
"Release 1.1.6"). State of 2026-11-17. Target environment: FTB Evolution 2 1.9.0.

For the "how" of every step see `LEARNING_LOG.md`; for the reusable procedure see `docs/PORTING_CHECKLIST.md`.

## Versions

| | 1.21.1 (before) | 26.1.2 (after) |
|---|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.220 | 26.1.2 / 26.1.2.126 |
| Java | 21 | 25 (Mixin compatibility level JAVA_25) |
| Build plugin | NeoGradle `userdev` 7.0.170 | ModDevGradle `net.neoforged.moddev` 2.0.148 |
| Gradle | 8.14.2 | 9.2.1 |
| Mappings | Parchment 1.21.1 | none (26.x is not obfuscated) |
| AE2 | 19.2.18 (modmaven) | 26.1.13-beta (Maven Central) |
| JEI | 19.27.0.336 | 29.53.0.115 |
| MixinExtras | 0.5.0 (own dependency) | 0.5.5 (bundled by NeoForge; `testRuntimeOnly` for tests) |
| ExtendedAE / ae2wtlib (optional) | 1.21 builds | 26.1-1.0.4 / 26.1.2-beta (`mods.toml` ranges) |

## What changed

**Build tooling (Phase 2):** NeoGradle → ModDevGradle on 1.21.1 first, then Gradle 9.2.1, then Java 25, then
the version bump; every step proven green before the next. Two test tasks: `plainTest` (plain JUnit) and `test`
(FML/Minecraft bootstrapped, MDG `unitTest`), plus a Mixin apply audit test.

**Compile fixes (Phase 3), grouped by cause:** `ResourceLocation` → `Identifier` (also in the public API),
`ClickType` → `ContainerInput`, `Util` moved, AE2 `Icon`/`AEKeyRendering` moved, the GUI redesign
(`GuiGraphics` → `GuiGraphicsExtractor`, 2D pose, `render` → `extractRenderState`/`extractContents`, z-offsets →
draw order/`nextStratum`), input events (`MouseButtonEvent`, `KeyEvent`, `CharacterEvent`), NBT getters returning
`Optional` (+ UUID via `UUIDUtil.CODEC`), `ClientPacketDistributor`, action bar vs chat messages, an access
transformer for `imageWidth`/`imageHeight`, crafting remainders, key-mapping categories, `blit` with a render
pipeline, JEI's internal config accessor.

**Mixins (Phase 4):** W1 quick-move input/render hooks; W2–W4 CPU save/load on `ValueInput`/`ValueOutput`
(AE2 and AdvancedAE); W5/W6 storage-bus slot index and I/O-bus slot routing rewritten for NeoForge's transfer API
(`ResourceHandler`, `Transaction`).

**Bugs found only in game (fixed):**
1. Client refused to load: NeoForge 26 needs a client handler for every clientbound payload.
2. Duplicate badge drawn behind the pattern: MC 26 draws the slots after AE2's `drawFG`.
3. Quick-move selection one row too low: the toolbar offset was applied twice.
4. Feedback messages invisible: the HUD (action bar, chat) is drawn before the screen → now toasts.
5. AELIS craft tree clipped: MC 26 `enableScissor` applies the current pose.

**Behaviour changes a player will notice** (release notes):
- Feedback messages (copy, bookmarks, quick move, force-start errors) are toasts (top-right) instead of action
  bar / chat. New key `toast.appliedenhancements.item_menu.title`.
- Key-binding category key is now `key.category.appliedenhancements.category` (bindings themselves are kept).
- Public API: `ResourceLocation` parameters are now `Identifier`.

## Verification matrix

"Tests" = compiles + unit tests green. "In game" = checked by hand in FTB Evolution 2 1.9.0
(`docs/INGAME_CHECKLIST.md`, with dates and details).

| Area | Tests | In game |
|---|---|---|
| Startup, all Mixins apply (client + server) | ✔ audit test (server side) | ✔ TEST instance; 46+ Mixins seen applied |
| Key bindings, categories, rebinding survives restart | – | ✔ |
| ME terminal item menu (extract, amount field, copy, start craft, scrolling) | – | ✔ |
| JEI menu (list + bookmarks), cheat-give, bookmark missing | – | ✔ |
| Pattern access terminal: buttons, badges, header, quick move, toasts | – | ✔ (after fixes 2–4) |
| ExtendedAE + ae2wtlib terminals (pattern access, crafting) | – | ✔ |
| Craft amount + AELIS confirm screen, force start, craft tree, fullscreen | – | ✔ (after fix 5) |
| CPU save/reload, AE2 CPU (W2), incl. AELIS cycle state | FML tests | ✔ (save-file check) |
| CPU save/reload, AdvancedAE quantum computer (W3) | – | ✔ (save-file check) |
| W4 (AdvancedAE + Data Energistics virtual orders) | – | applies only (Data Energistics not in the pack) |
| Storage bus / export bus / interface (W5, W6), switches on and off | – | ✔ exact counts |
| Dedicated server (FTB server pack + client) | runServer starts | ✔ quick move, AELIS, force start; no client classes on the server |
| Without the optional mods (AE2 + GuideME only) | – | ✔ |
| Other AE2 addons | – | no hard Mixin conflicts (`docs/ADDON_COMPATIBILITY.md`) |

**Not verified:** the "force amount too large" toast (needs an enormous order); JEI present without
ExtendedAE/ae2wtlib; the AELIS progress title while calculating (see "Later" in the checklist); recursive/
catalyst crafts such as Mystical Agriculture essences; loading a world saved by the 1.21.1 build (save format
was kept compatible on purpose, but a 1.21.1 → 26.1 world upgrade was never run).

## Risk list

| # | Risk | Where | Impact | What to do |
|---|---|---|---|---|
| 1 | **JEI internals** (`mezz.jei.common.Internal`, `CommandUtil`, `GiveAmount`) are not JEI's public API; `getJeiClientConfigs()` was already renamed once. | `JeiItemContextMenuHandler` | Cheat-give through our menu breaks on a JEI update (compile error or crash when used). | Re-check on every JEI update; keep the menu working if JEI's internals are missing. |
| 2 | **Deprecated for removal (NeoForge/MC):** `AbstractContainerScreen.getGuiLeft()/getGuiTop()` (25×). | `WirelessExtendedPatternAccessDuplicateMixin` | Compile break when removed. | Switch to the `leftPos`/`topPos` fields (as the AE2 terminal Mixin does) in the next port. |
| 3 | **Deprecated for removal (JEI):** `IIngredientHelper.getResourceLocation` (2×), `IIngredientManager.createTypedIngredient` (1×). | JEI integration | Compile break on a JEI update. | Use the replacements JEI names in its javadoc. |
| 4 | **Deprecated (not for removal):** `ValueInput.lookup()` (2×, on purpose: AE2 26 uses it too). | W2, W3 | Low. | Watch AE2's replacement. |
| 5 | **Beta dependencies:** AE2 26.1.13-beta, ae2wtlib 26.1.2-beta, GuideME beta. Mixins target AE2 internals (`CraftingCpuLogic`, `CraftingSimulationState`, …); `defaultRequire = 1` crashes at startup if a target moves. | all AE2 Mixins | Startup crash after an AE2 update in the pack. | Run the Mixin audit test + a TEST-instance start on every AE2 update (see checklist). |
| 6 | **Anonymous class target** `HandlerStrategy$1` (numbered by position in the file). | W6 | Silently targets the wrong class if AE2 reorders the file. | The audit test catches a missing method; re-check after AE2 updates. |
| 7 | **AdvancedAE / ExtendedAE / ae2wtlib are not compile dependencies**; their Mixins are `@Pseudo` string targets, checked only at runtime. | W3, W4, ExtendedAE/ae2wtlib Mixins | A changed signature fails only in the pack. | TEST instance start + the checklist after addon updates. |
| 8 | **W4 untested** (needs Data Energistics). | `AdvancedAeOrderCompletionMixin` | Unknown behaviour with that mod. | Test if a pack with Data Energistics is targeted. |
| 9 | **Cosmetic overlap** with Recursive AE2 Pattern Provider on the confirm-screen title. | `CraftConfirmScreenMixin` | Title text may be overwritten while AELIS calculates. | See `docs/ADDON_COMPATIBILITY.md`; test task "AELIS progress title". |
| 10 | **AE2 Crafting Time** enables its AELIS integration only for our 1.21.1 build. | (their mod) | No TTC columns in the AELIS screen. | Tell the author after release. |
| 11 | **Pack distribution drift:** the CurseForge client and FTB's server files of the same release differed (Just Dire Things 1.6.10 vs 1.6.11). | servers | Join failures unrelated to this mod. | Diff `mods/` when a server join fails. |
| 12 | **World compatibility 1.21.1 → 26.1** not tested. | saves | Lost AELIS runtime state in CPUs at worst (jobs cancel safely). | Test with a copied 1.21.1 world before announcing upgrade support. |

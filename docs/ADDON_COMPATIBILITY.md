# Compatibility with other AE2 addons (FTB Evolution 2 1.9.0)

Checked on 2026-11-17 for the 26.1.2 port, against the mods of FTB Evolution 2 1.9.0 (AE2 26.1.13-beta).
In-game, all of these addons were loaded during the whole in-game checklist (`docs/INGAME_CHECKLIST.md`)
without errors. This page records the static check of where their Mixins touch the same AE2 code as ours.

## How the check works

1. **Shared classes:** a TEST-instance session's `logs/debug.log` records every applied Mixin as
   `Mixing <Mixin> from <config> into <target>`. Targets that our config shares with another config are the
   candidates. (Only classes that were loaded in that session show up, so play through the features first.)
2. **Shared methods:** for each shared AE2 class, the method names of that class (AE2 26 sources) that appear
   as constants in the other mod's Mixin class file, compared with our `method = "..."` values.
3. **Hook kinds:** `javap -v -p` on both Mixin classes shows each handler's annotation (`@Inject`,
   `@WrapOperation`, ...) and its `method`/`target`. `@Redirect` and `@Overwrite` are exclusive (only one mod can
   win, or Mixin fails); `@Inject`, `@ModifyArg`, `@ModifyVariable`, `@WrapOperation` and `@WrapMethod` stack.

Scripts (outside the repo, in `~/dev/ae-port/tools/`): `mixin-overlap.sh`, `mixin-method-overlap.py`,
`mixin-hook-kinds.sh`, `mixin-hook-kinds2.sh`.

## Result

Only two addons hook the same AE2 **methods** as this mod: **AE2 Crafting Time 1.3.1** and **Recursive AE2
Pattern Provider 1.1.2**. AdvancedAE, ExtendedAE, Transparent AE2, AE2 Import/Export Card, ME Requester and
Pattern Restocker share classes but hook other methods, or only add accessors.

**No hard conflicts:** neither side uses `@Redirect` or `@Overwrite` on a shared method.

| Shared AE2 method | This mod | Other mod | Assessment |
|---|---|---|---|
| `CraftConfirmTableRenderer` / `CraftingStatusTableRenderer` `getEntryDescription`, `getEntryTooltip` | `@Inject` at RETURN, adds lines to the returned list | Crafting Time `@WrapMethod` | stacks; both add content |
| `CPUSelectionList.getTooltip`, `drawBackgroundLayer` | `@WrapOperation` on `Tooltips.ofNumber/ofBytes` and `InfoBar.add` (only for "infinite" CPUs) | Crafting Time `@ModifyArg`/`@ModifyVariable`/`@WrapOperation` on other calls (CPU order, tooltip) | different call sites |
| `CraftingCPUScreen.postUpdate` | `@WrapOperation` on `new CraftingStatusEntry` | Crafting Time `@ModifyArg` on `new CraftingStatus` | different call sites |
| `CraftConfirmMenu.broadcastChanges`, `removed`; `CraftingPlanSummary.fromJob` (server) | `@Inject` | Crafting Time `@Inject` + `@WrapOperation` around `fromJob`; Recursive Pattern Provider `@Inject` | stacks |
| `MEStorageScreen.init` | `@Inject` | Crafting Time, Import/Export Card `@Inject` | stacks |
| `CraftConfirmScreen.updateBeforeRender`: title (`setTextContent`, argument 1) | `@ModifyArg` | Recursive Pattern Provider `@ModifyArg` on the same call | **possible overwrite**, see below |

### Known overlap: confirm-screen title while AELIS calculates

While an AELIS calculation is running in AE2's own confirm screen, our `CraftConfirmScreenMixin` replaces the
title with a progress line (it builds a new text instead of extending the incoming one). If Recursive AE2 Pattern
Provider also changes that title, the Mixin that runs second wins for those few seconds. Cosmetic and temporary;
not seen in game. Fix if it shows up: append to the incoming title instead of replacing it.

### AE2 Crafting Time's integration with this mod

AE2 Crafting Time contains an integration for the AELIS screen (`IntegrationCatalog` names
`AelisCraftConfirmScreen`, `AelisPlanRows$Row`, `AelisPlanLayout`), but enables it only for this mod's
`1.21.1-neoforge` build. On 26.1.2 it logs `integration=appliedenhancements ... state=skipped
reason=target_not_supported`. Effect: with the AELIS planner on, Crafting Time's TTC columns and throughput
figures appear only in AE2's own confirm screen, not in the AELIS screen. Not a bug in this mod; once the 26.1
build is released, AE2 Crafting Time's author can enable and update the integration (our class names are
unchanged, but the GUI API changed: `GuiGraphics` → `GuiGraphicsExtractor`).

## Not checked statically

Vanilla classes (`Screen`, `AbstractContainerScreen`, `ItemStack`), which dozens of mods hook. Our hooks there
were exercised in game without problems.

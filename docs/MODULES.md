# Feature modules

The mod ships as ONE jar, but new and optional features live in their own module: a package
`com.appliedenhancements.<module>`, its own Mixin config `appliedenhancements.<module>.mixins.json` (registered as an
extra `[[mixins]]` block in `META-INF/neoforge.mods.toml`), a master switch in the config file, lang keys and, where
useful, in-game rigs (`src/packtests`).

Rules:

* A Mixin-owned package (`com.appliedenhancements.<module>.mixin`) may contain ONLY Mixin and Accessor classes that are
  declared in that module's mixins.json (`MixinOwnedPackageGuardTest` enforces it). Helper classes go into other
  packages of the module (`<module>`, `<module>.client`, `<module>.network`, ...).
* The Mixin tests (`InjectCancellableGuardTest`, `MixinOwnedPackageGuardTest`, `MixinApplyAuditTest`) find every Mixin
  config through `com.appliedenhancements.test.MixinConfigs` (reads mods.toml); a new module needs no test change.
* Config keys and lang keys are never renamed when a feature moves into a module (players' config files and
  translations stay valid); `Config.java` stays the single config class.

| Module | Package | Mixin config | Master switch (default) | Status |
|---|---|---|---|---|
| matrixfix | `com.appliedenhancements.matrixfix` | `appliedenhancements.matrixfix.mixins.json` | `compat_fixes.fix_extendedae_matrix_crafter_cooldown` (true) | Done. ExtendedAE Assembler Matrix crafter cooldown fix. |
| outputpull | `com.appliedenhancements.outputpull` | `appliedenhancements.outputpull.mixins.json` | `pattern_provider.enable_output_pull` (false); `output_pull_expiry_seconds` (300) | Done (G1 AE2/ExtendedAE + G2 AdvancedAE providers, side panel UI). Pack-test rigs `pull_*`. |
| chance | `com.appliedenhancements.chance` | `appliedenhancements.chance.mixins.json` | `crafting.enable_chance_output_bonus` (false), `enable_chance_top_up` (false), `chance_top_up_craft_inputs` (true), `chance_top_up_idle_seconds` (30), `chance_top_up_max_factor` (10) | Done. Chance-output crafting for marked processing patterns (bonus counting, top-up waves, follow-up jobs, middle-click retry, Quantum CPU parity). Pack-test rigs `chance_*`. |
| diagnostics | `com.appliedenhancements.diag` | `appliedenhancements.diag.mixins.json` | `diagnostics.enabled` (false) | Done. Temporary `[ACE/diag]` logging (was `[AE-DIAG]`) for the stuck-crafts bug; remove once solved. |

## outputpull layout

| Package | Content |
|---|---|
| `outputpull` | `OutputPullState`, `ProviderOutputPull`, `ProviderOutputPullHost` (runtime state, face modes, per-provider host interface; the pack-test rigs use `ProviderOutputPullHost`) |
| `outputpull.mixin` | `PatternProviderLogicPullMixin` (AE2 + ExtendedAE), `AdvPatternProviderLogicPullMixin` (AdvancedAE, `@Pseudo`), `PatternProviderScreenPullPanelMixin` (client) |
| `outputpull.network` | `ProviderPullSideRequestPayload`, `ProviderPullSidesPayload`, `OutputPullNetwork` (registration entry point called from the shared `network.NetworkHandler`) |
| `outputpull.client` | side panel widgets, `ClientProviderPullState`, `OutputPullClientPayloads` (client-only payload handler) |
| `packtests.outputpull` (src/packtests) | `OutputPullTestPlots` (selector `ae2:appliedenhancements/pull_*`) |

Shared on purpose: `Config.java`, `network.NetworkHandler` (one line calling `OutputPullNetwork.register`), the lang
files, `META-INF/neoforge.mods.toml`.

## Log tags

Every log line of an extractable module carries a tag, `[ACE/<module>]` (the scheme of the future Applied Crafting
Extras mod). The message texts after the tag are unchanged, so grep tools only need the new tag.

| Module | Tag | SLF4J logger | Class |
|---|---|---|---|
| chance | `[ACE/chance] ` | `appliedcraftingextras-chance` | `chance.ChanceLog` (`info`, rate-limited `error`) |
| diagnostics | `[ACE/diag] ` | `appliedcraftingextras-diag` | `diag.DiagLog` (rate-limited per key, switch `diagnostics.enabled`) |
| outputpull | `[ACE/outputpull]` | (reserved) | the module writes no log lines today |
| matrixfix | - | - | no log lines |

Grep examples: `grep '\[ACE/chance\]' latest.log`, `grep '\[ACE/diag\]' latest.log`. The helper scripts in
`~/dev/ae-port/tools` (diag-*.sh, matrix-dump.sh, chance-lines.sh, retry-nojob.sh, ...) were updated.

## chance layout

| Package | Content |
|---|---|
| `chance` | runtime state and logic (`ChanceOutputMarker` = the data component + registration, `ChanceJobMarks`, `ChanceOutputBonus`, `ChanceTopUp*`, `ChanceYieldData`, `ChanceRetryLink`, `Ae2TopUpJob`, `AdvancedAeTopUp`, `AdvancedAeChanceOutputBonus`, `TopUpJob`, `JobTaskProgress`, `ChanceTopUpControl`, `ChanceLog`) |
| `chance.mixin` | `CraftingCpuLogicChanceMixin` (AE2 CPU), `AdvancedAeChanceMixin` (quantum CPU, `@Pseudo`), `PatternEncodingTermMenuChanceMixin` + `PatternEncodingTermScreenChanceMixin` (client), own accessors `ChanceJobAccessor`, `ChanceTimeTrackerAccessor`, `CraftingCPUMenuAccessor`, `CraftingStatusMenuAccessor` |
| `chance.network` | `ChanceRetryPayload`, `ChanceNetwork` (registration entry point called from `network.NetworkHandler`) |
| `chance.client` | `ChanceOutputToggleButton`, `ChanceRetryClientEvents` |
| tests | `chance.Chance*Test` (the marker test is an FML test, listed in `fmlTestClasses`) |

Shared on purpose: `Config.java`, lang files, `FeedbackToastPayload` (the module sends toasts through it), `mods.toml`,
one line in `AppliedEnhancements` (`ChanceOutputMarker.register`) and one in `NetworkHandler`.

## diagnostics layout

`diag.DiagLog`; `diag.mixin`: `AssemblerMatrix{Pattern,Cluster,Thread,Done,Buffer}DiagMixin` (ExtendedAE, `@Pseudo`),
`CraftingCpuLogicDiagMixin` (AE2 CPU observer), `AdvancedAeCpuDiagMixin` (quantum CPU observer, `@Pseudo`, reflection
only). All injectors are `require = 0` and return what the inner chain returned. With `diagnostics.enabled` off a
handler costs one config read. Differences to the old inline diagnostics: the three "inputs not extracted" lines
(cycle guard / AE2 / not trackable) are one line now, and the stall clock does not count a chance bonus as activity.

## Hook order on the CPU classes

The AELIS code and the chance code both hook AE2's `CraftingCpuLogic` and AdvancedAE's `AdvCraftingCPULogic`. Mixin
applies lower priorities first and MixinExtras wraps nest so that the LATER applied wrapper is the OUTER one.

| Target | AELIS mixin | Chance mixin | Diagnostics mixin |
|---|---|---|---|
| `CraftingCpuLogic` | `CraftingCpuLogicBatchMixin` priority 1100 | `CraftingCpuLogicChanceMixin` 1110 | `CraftingCpuLogicDiagMixin` 1120 |
| `AdvCraftingCPULogic` | `AdvancedAeCraftingCycleMixin` 900 | `AdvancedAeChanceMixin` 910 | `AdvancedAeCpuDiagMixin` 920 |

Why the numbers matter:

* `insert` (the only order-sensitive hook): the chance bonus compares the waiting-for count before and after the call, so it
  must wrap the AELIS retained-output wrapper, which itself removes retained cycle output from the waiting-for list. The
  verified (exported bytecode, `-Dmixin.debug.export=true`) chain of `CraftingCpuLogic.insert` is: diag -> chance -> AELIS
  retain -> AE2 body. The diag observer is outermost so it sees the final result.
* The small gaps (+10) keep the chance mixin right behind the AELIS mixin relative to third-party mixins, i.e. at the
  position the chance code had while it lived inside the AELIS mixin.
* `executeCrafting` -> `pushPattern`: three `@WrapOperation`s of the same INVOKE (AELIS dispatch, chance recording,
  diag logging); they are independent of each other.
* `finishJob`: AELIS and chance both use `@Inject(TAIL)`. Both end up inside the original method body (verified), so they
  only run when the job really finishes (the AELIS `@WrapMethod` may defer a finish; then neither clears anything).
* `writeToNBT` / `readFromNBT` (RETURN injects, separate NBT keys), `tickCraftingLogic` wrappers: independent.

State sharing: none. The chance mixins keep their own fields (marks, top-up state, retry link) and use their own
accessor interfaces; the AELIS mixins no longer carry chance fields. The chance accessors have different method names
(`appliedenhancements$chance...`) from the AELIS accessors so both can target the same AE2 classes.

## Isolation rule

`ModuleIsolationGuardTest` fails when code outside the module packages references `chance`, `diag`, `outputpull` or
`matrixfix` (only `AppliedEnhancements.java` and `network/NetworkHandler.java` are allowed entry points). Modules may use the
shared core, never the other way round; that is what keeps them movable into Applied Crafting Extras.

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
| diagnostics | `com.appliedenhancements.runtime.DiagLog` + `mixin.AssemblerMatrix*DiagMixin` | still in the main `appliedenhancements.mixins.json` | `diagnostics.enabled` (false) | Not split yet. Temporary `[AE-DIAG]` logging for the stuck-crafts bug; remove once solved. |

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

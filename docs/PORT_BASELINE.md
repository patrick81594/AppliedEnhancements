# Port baseline: FTB Evolution 2

The exact versions this port targets. These come from the pack we play,
not from the latest upstream releases. Redo this when the pack updates
and compare (see "How to refresh").

- **Read on:** 2026-11-12
- **Source:** Prism Launcher instance "FTB Evolution 2" (read-only)
- **Pack:** FTB Evolution 2 **1.9.0** (CurseForge project 1731757, file 9546350)

## Platform

| Component | Version |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.126 |
| LWJGL | 3.4.1 |
| Java | 25 (Prism runtime "java-runtime-epsilon") |
| Mods in pack | 433 |

## Mods that matter to Applied Enhancements

| Role | Mod ID | Jar | Version | On 1.21.1 we used |
|---|---|---|---|---|
| **Required** | `ae2` | appliedenergistics2-26.1.13-beta.jar | **26.1.13-beta** | 19.2.18 (range `[19.2.17,)`) |
| Optional | `ae2wtlib` | ae2wtlib-26.1.2-beta.jar | 26.1.2-beta | range `[19.5.1,)` |
| Optional | `extendedae` | ExtendedAE-26.1-1.0.4-neoforge.jar | 26.1-1.0.4-neoforge | 1.21-2.2.32-neoforge |
| Optional | `jei` | jei-26.1.2-neoforge-29.53.0.115.jar | 29.53.0.115 | 19.27.0.336 |
| Mixin target | `advanced_ae` | AdvancedAE-26.1.7.jar | 26.1.7 | |
| Mixin target | `configured` | configured-neoforge-26.1.2-2.7.5.jar | 2.7.5 | |
| Related addon | `merequester` | merequester-neoforge-26.1.2-1.5.1.jar | 26.1.2-1.5.1 | |
| Related addon | `apprep` (Applied Replicatics) | Applied-Replicatics-26.1-1.0.1.jar | 26.1-1.0.1 | |
| Library | `glodium` | Glodium-26.1-1.2-neoforge.jar | 26.1-1.2-neoforge | |
| Library | `guideme` | guideme-26.1.17-beta.jar | 26.1.17-beta | |

### Other AE2 addons in the pack (watch for conflicts)

| Mod ID | Version | Requires ae2 |
|---|---|---|
| `appflux` (AppliedFlux) | 26.1-1.0.4-neoforge | `[26.1.8-alpha,)` |
| `ae2craftingtime` | 1.3.1 | `[26.1.10-beta,27.0.0)` |
| `ae2importexportcard` | 26.1.2-2.3.1 | `[26.1.6-alpha,)` |
| `ae2netanalyser` | 26.1-1.0.0-neoforge | `[26.1.8-alpha,)` |
| `ae2_pattern_restocker` | 1.1.0 | `[26,)` |
| `applied_oritech` | 1.0.6+26.1.2 | `[26.1.13-beta,)` |
| `appliedcooking` | 7.2.0 | `[26,)` |
| `recursiveae2patternprovider` | 1.1.2 | `[26.1.0,)` |
| `transparentae2` | 26.1.2-1.1.1 | `[26.1.10-beta,)` |

### Not in the pack

EMI, ae2ct (AE2 Crafting Tree), the Useless mod, MEGA Cells. Our Mixins into
ae2ct and Useless are `@Pseudo`/optional, so they don't run in this pack.

## Notes for the port

- AE2 switched from 19.x versions to Minecraft-style 26.x. Every `ae2`
  range in `gradle.properties` / `neoforge.mods.toml` must change.
- AE2, AE2WTLib and GuideMe are **beta**: their APIs may change between
  pack updates.
- NeoForge compares versions the Maven way: `26.1-1.0.4` satisfies
  `[1.0.5,)` because 26 > 1. Check ranges against real version strings.

## Original 1.21.1 build (reference result)

Proven on 2026-11-12: the untouched `1.21.1-neoforge` branch (275d8aa)
builds and ALL tests pass. Any later failure is caused by the port, not
inherited.

- **Environment:** throwaway `eclipse-temurin:21-jdk-noble` container
  (Temurin 21.0.13+9), `--user 1000:1000`, its own Gradle cache volume
  `ae-port-gradle-cache-jdk21`, git worktree `~/dev/ae-port/baseline-1.21.1`.
- **Command:** `bash gradlew build --no-daemon --console=plain`
- **Result:** BUILD SUCCESSFUL in 3m 53s (34 tasks). **544 tests in 99
  suites: 0 failures, 0 errors, 0 skipped.** Jar: `appliedenhancements-1.1.6.jar`
  (1,027,617 bytes).
- **Warnings (pre-existing, not ours to fix now):**
  - `JeiPlanBookmarks.java:53`: `IIngredientManager.createTypedIngredient(V)`
    is deprecated for removal in JEI 19. Likely gone in JEI 29.
  - "Deprecated Gradle features were used … incompatible with Gradle 9.0":
    matters when we upgrade Gradle in Phase 2.
  - About 100 deprecation lines in the log come from NeoForm recompiling
    Minecraft/NeoForge sources (`build/neoForm/...`), not from our code.

## How to refresh (when the pack updates)

All read-only, on the HOST (PowerShell):
1. `mmc-pack.json` in the instance folder → Minecraft / NeoForge / LWJGL.
2. `instance.cfg` → `ManagedPackVersionName` = pack version.
3. In `minecraft/mods`, open each relevant jar as a zip (read-only) and read
   `META-INF/neoforge.mods.toml` (modId, version, dependency `versionRange`).
   If the version is `${file.jarVersion}`, use `Implementation-Version` from
   `META-INF/MANIFEST.MF` or the file name.
4. Update this file and diff it against the previous commit.

# Learning log: porting Applied Enhancements 1.21.1 → 26.1.2

One short entry per step: **what** we did, **why**, the **command** (and
where it runs), and the **lesson**. In Phase 6 this becomes a repeatable
checklist for the next Minecraft version.

Setup: Windows 11 host, WSL2 Ubuntu (repo lives on ext4 at
`~/dev/ae-port/AppliedEnhancements`), Docker Desktop, VS Code (WSL + Dev
Containers extensions). Target: FTB Evolution 2 (Minecraft 26.1.2,
NeoForge 26.1.2.126, Java 25).

---

## Phase 0: Orientation (2026-11-12)

- **What:** Read the `1.21.1-neoforge` branch (commit 275d8aa, "Release
  1.1.6") to see what we're porting.
- **Findings:** NeoGradle `net.neoforged.gradle.userdev` 7.0.170 (not
  ModDev), Gradle wrapper 8.14.2, Java 21, Parchment 1.21.1. 359 Java
  files, ~105 JUnit tests, 67 Mixins (about 45 target AE2 internals, the
  rest target ExtendedAE, AE2WTLib, AdvancedAE, ae2ct, vanilla, ...).
  Optional deps: ExtendedAE, AE2WTLib, JEI.
- **Lesson:** Before porting, take stock: build plugin, versions, mixin
  targets, optional integrations. Mixins into another mod's internals are
  the riskiest part of any port.

## Phase 1, Step 1: Host check: WSL2 and Git

- **What:** Checked that WSL2 is the default, Ubuntu runs on v2, and Git is
  installed. Set global Git identity and `core.autocrlf input`.
- **Where:** HOST, PowerShell (`wsl -l -v`, `git --version`,
  `git config --global ...`).
- **Lesson:** Use the GitHub noreply email for commits. `autocrlf input`
  means "commit LF, never convert on checkout", which is right for code
  that runs on Linux.

## Phase 1, Step 2: Docker

- **What:** Verified Docker Desktop (29.9.3) with `docker run --rm hello-world`.
- **Where:** HOST, PowerShell.
- **Terms:** an *image* is a read-only template; a *container* is a running
  instance of an image; a *volume* is storage managed by Docker that
  outlives containers.
- **Lesson:** `hello-world` proves the whole chain at once: client, engine,
  registry pull, and running a container.

## Phase 1, Step 3a: GitHub access check

- **What:** Called the GitHub API "who am I" (`get_me`) through the MCP server.
- **Why:** Test a new credential (the PAT in a Windows User env var) with a
  harmless read before any write.
- **Lesson:** The MCP server only sees env vars that existed when Claude
  Code started, so restart it after setting a new one.

## Phase 1, Step 3b: Fork

- **What:** Forked `AyaYumi/AppliedEnhancements` →
  `patrick81594/AppliedEnhancements`.
- **Lesson:** Check first that the name is free (404 = free). Forking is
  asynchronous, so verify by listing branches. Forks made through the API
  copy all branches by default.

## Phase 1, Step 3c: Inspect WSL

- **What:** `whoami`, `$HOME`, `git --version`, `git config --global --list`
  inside Ubuntu.
- **Where:** HOST, PowerShell → `wsl -d Ubuntu -- bash -lc "{ ...; } 2>&1"`.
- **Lesson:** WSL is a separate Linux system with its own `~/.gitconfig`.
  Windows Git settings don't carry over. From PowerShell, merge stderr
  inside bash (`{ ...; } 2>&1`) or normal output can go missing.

## Phase 1, Step 3d: WSL Git config

- **Command (WSL):**
  ```
  git config --global user.name "Scratn"
  git config --global user.email "5104646+patrick81594@users.noreply.github.com"
  git config --global core.autocrlf input
  ```
- **Lesson:** `--global` = `~/.gitconfig` for this OS user. Set it up once
  on each machine or OS you commit from.

## Phase 1, Step 3e: Clone

- **Command (WSL):**
  ```
  mkdir -p ~/dev/ae-port && cd ~/dev/ae-port
  git clone --branch 1.21.1-neoforge https://github.com/patrick81594/AppliedEnhancements.git
  ```
- **Why WSL (ext4) and not C:\\:** a Docker bind mount from the Windows
  filesystem is much slower and file watching is unreliable. Keep repos
  that containers build inside WSL.
- **Lesson:** `git status`, `git log --oneline -3` and `git branch -a`
  show where you are right after a clone.

## Phase 1, Step 3f: `upstream` remote

- **Command (WSL):**
  ```
  git remote add upstream https://github.com/AyaYumi/AppliedEnhancements.git
  git remote set-url --push upstream DISABLE
  git fetch upstream
  git remote -v
  ```
- **Lesson:** `origin` = your fork (fetch + push), `upstream` = the
  original (fetch only). `DISABLE` as the push URL guards against
  accidental pushes. Quoting gotcha: PowerShell rewrites `$(...)` and `$1`
  inside `bash -lc "..."`, so keep commands `$`-free or use a script
  file. An error like "not a git repository (... /mnt)" means the command
  ran in the wrong directory.

## Phase 1, Step 3g: Port branch

- **Command (WSL):** `git switch -c 26.1.2-neoforge 1.21.1-neoforge`, then
  `git branch -vv`.
- **Lesson:** Always name the starting point explicitly. A new local
  branch has no tracking branch until the first `git push -u`.

## Phase 1, Step 3h: Line endings / `.gitattributes`

- **What:** Checked before adding a file. The repo already has a good
  `.gitattributes` (`* text=auto`, explicit `eol=lf` per type,
  `*.bat eol=crlf`, binaries marked). All 384 text files are stored as LF.
- **Command (WSL):** `git ls-files --eol | cut -c1-8 | sort | uniq -c`,
  `cat -A .gitattributes` (CRLF would show as `^M$`).
- **Decision:** Left it unchanged, to stay close to upstream.
- **Lesson:** Check what already exists before adding config files.
  `git ls-files --eol` shows the line endings of every tracked file.

## Phase 1, Step 3i: This log

- **What:** Created `LEARNING_LOG.md` as the first commit on `26.1.2-neoforge`.
- **Lesson:** Log each step right after it finishes, while the details
  are fresh.

## Phase 1, Step 4a: Find Git Credential Manager (GCM)

- **What:** Checked that GCM 2.6.1 exists at
  `C:\Program Files\Git\mingw64\bin\git-credential-manager.exe`.
- **Where:** HOST, PowerShell (`Test-Path`, `--version`).
- **Lesson:** "Not on PATH" doesn't mean "not installed". Check the
  expected path directly.

## Phase 1, Step 4b: WSL Git → Windows GCM

- **Command (WSL, via a script file to dodge quoting):**
  ```
  git config --global credential.helper "/mnt/c/Program\ Files/Git/mingw64/bin/git-credential-manager.exe"
  ```
- **Why:** Log in once in the browser. The token is stored in Windows
  Credential Manager and never written as plain text in WSL.
- **Lesson:** Escape the space with `\` because Git runs the helper
  through a shell. WSL can run Windows `.exe` files directly ("interop").
  When quoting crosses two shells, put the commands in a script file and
  run `bash script.sh`.

## Phase 1, Step 4c: First push

- **Command (WSL, in YOUR OWN terminal):** `git push -u origin 26.1.2-neoforge`
- **What went wrong first:** from a non-interactive (automated) shell,
  GCM said "Cannot prompt because user interactivity has been disabled",
  then Git hung waiting for a username.
- **Lesson:** Any first-time login needs an interactive terminal.
  Automation can only reuse credentials that are already stored. `-u`
  links the local branch to `origin/<branch>`, so a plain `git push` /
  `git pull` works after that.

## Phase 1, Step 5a: Verify the JDK 25 image tag

- **Command (HOST, PowerShell):**
  ```
  docker manifest inspect eclipse-temurin:25-jdk-noble
  docker run --rm eclipse-temurin:25-jdk-noble java -version
  ```
- **Result:** the tag exists (amd64, arm64, ...). Temurin 25.0.5+8 LTS on
  Ubuntu 24.04.5 "noble". Digest
  `sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f`.
  Runs as root by default; the image already has a user `ubuntu`
  (/home/ubuntu).
- **Terms:** *tag* = a movable label in a registry. *Digest* = the
  unchangeable content hash of one exact image. *Variant* = the same JDK
  on a different base OS (`-noble`, `-alpine`, ...). *Layers* = the
  stacked filesystem pieces an image is built from (shared between
  images, hence "Already exists").
- **Lesson:** Check a tag with `manifest inspect` (no download) before
  you build on it. Pin the OS variant so the base doesn't change
  underneath you.

## Phase 1, Step 5b: Container user (UID/GID)

- **Command:** `wsl -d Ubuntu -- id` (HOST) and
  `docker run --rm eclipse-temurin:25-jdk-noble id ubuntu`.
- **Result:** WSL `harveyp` = 1000:1000, image `ubuntu` = 1000:1000. The
  image has no git and no sudo.
- **Decision:** use the existing `ubuntu` user as the container user
  (option A), and install sudo with no password.
- **Lesson:** File ownership is stored as numbers (UID/GID); names are
  only labels each system looks up. Matching UIDs = no root-owned files
  in the bind-mounted repo.

## Phase 1, Step 5c: Dockerfile

- **What:** `.devcontainer/Dockerfile`: `FROM eclipse-temurin:25-jdk-noble`,
  installs git + sudo (NOPASSWD rule in `/etc/sudoers.d/ubuntu`, mode
  0440), pre-creates `/home/ubuntu/.gradle` owned by ubuntu, `USER ubuntu`.
- **Lessons:**
  - One `RUN` with `&&` = one layer. Clean up (`rm -rf /var/lib/apt/lists/*`)
    in the SAME layer, or the files stay in the image anyway.
  - `--no-install-recommends` keeps the image small.
  - An empty named volume copies the image's content AND ownership at
    that path on first mount. If the folder doesn't exist in the image,
    it becomes root-owned, and a non-root Gradle fails.

## Phase 1, Step 5d: devcontainer.json + mount folders

- **What:** `.devcontainer/devcontainer.json`: build from the Dockerfile,
  `remoteUser: ubuntu`, mounts:
  - named volume `ae-port-gradle-cache` → `/home/ubuntu/.gradle`
  - bind `~/dev/ae-port/out` → `/workspaces/out`
  - bind read-only `~/dev/ae-port/ae2-src` → `/workspaces/ae2-src`
  - VS Code extensions: Java Pack and Gradle.
- **Command (WSL):** `mkdir -p ~/dev/ae-port/out ~/dev/ae-port/ae2-src`
- **Lessons:** Volume = managed by Docker (caches). Bind = a real host
  folder (files you look at). `--mount type=bind` fails if the source
  folder is missing, so create it first. The opened repo folder is mounted
  automatically, so it doesn't need an entry. devcontainer.json may have
  comments (JSONC).

## Phase 1, Step 5e: Build and smoke-test the image

- **Command (WSL):** `docker build -t ae-port-dev:test .devcontainer`, then
  `docker run --rm -v <repo>:/workspaces/... -v ae-port-gradle-cache:/home/ubuntu/.gradle ...`
- **Result:** uid 1000 (ubuntu), Java 25.0.5, git 2.43.0, `sudo -n` OK,
  `~/.gradle` volume owned by ubuntu, no root-owned files in the repo.
  `sh ./gradlew --version` → Gradle 8.14.2, Launcher JVM 25.0.5.
- **Problem found:** `./gradlew: Permission denied`. The repo stores
  gradlew as mode `100644` (not executable), and upstream has the same.
  The project's CI works around it with `bash gradlew`.
- **Lessons:**
  - Test the Dockerfile with plain `docker build`/`run` before going
    through the IDE, to find out which layer is broken.
  - `git ls-files -s <file>` shows the stored mode (100644 = file,
    100755 = executable). `sh script` runs a script without the exec bit.
  - Docker build layers are cached: an unchanged step shows `CACHED`.
  - `gradlew --version` working on Java 25 does NOT prove that Gradle 8.14
    can *build* on Java 25 (its notes only claim Java 24 support).

## Phase 1, Step 5e-fix: Make gradlew executable

- **Command (WSL):**
  ```
  git update-index --chmod=+x gradlew
  chmod +x gradlew
  git commit -m "build: make gradlew executable"
  ```
- **Result:** commit be72c57, `mode change 100644 => 100755`, 0 lines
  changed. `./gradlew --version` works in the container.
- **Lessons:** `update-index --chmod` changes the mode Git stores (works
  even from Windows). Also `chmod` on disk, or `git status` reports a
  mismatch when `core.fileMode=true`. One change per commit.
  The "restricted method java.lang.System::load" WARNINGs = Java 24+
  native-access rules (JEP 472) hitting an older Gradle. A first sign
  that Gradle 8.14 is older than Java 25.

## Phase 1, Step 5f: Open in VS Code: Reopen in Container

- **How (HOST):** Ubuntu terminal → `cd ~/dev/ae-port/AppliedEnhancements && code .`
  → bottom-left "WSL: Ubuntu" → menu → "Reopen in Container".
- **Snag:** the command was missing. Its `when` clause (in the extension's
  package.json) needs `workspaceFolderCount != 0`: a FOLDER must be
  open, not just a WSL window.
- **Verified from the host:** `docker ps --filter label=devcontainer.local_folder`,
  `docker inspect` (mounts), `docker exec -u ubuntu ...`:
  - repo bind (rw), `out` bind (rw; a test file arrives in WSL as
    harveyp), `ae2-src` bind READ-ONLY ("Read-only file system"),
    gradle volume (owned by ubuntu, the wrapper reused without a new download).
  - `${localEnv:HOME}` expanded correctly.
  - Image name ends in `-uid`: Dev Containers adds a small UID-sync layer
    (updateRemoteUserUID). No change here because the UIDs already match.
- **Lessons:** If a VS Code command is missing, read its `when` clause.
  Verify a container from outside with `docker inspect` / `docker exec`,
  and test bind mounts with a round-trip file.

## Phase 1, Step 5g: Commit + push the dev container

- **Command (WSL):** `git add .devcontainer/ LEARNING_LOG.md`,
  `git diff --cached --stat`, commit 022a5fd, `GIT_TERMINAL_PROMPT=0 git push`.
- **Result:** `3a86b3b..022a5fd` pushed from the automated shell. GCM
  reused the token from the interactive login.
- **Lessons:** Check what's staged (`git diff --cached --stat`) before
  every commit. `GIT_TERMINAL_PROMPT=0` makes Git fail right away instead
  of hanging on a prompt, which is ideal for scripts.

## Phase 1, Step 6: DEV_SETUP.md

- **What:** a guide for start / stop / rebuild / reset (4 levels), what
  runs where, and the runClient-in-container options (explained only).
- **Lessons:** Take command names in docs from the installed extension
  (`package.nls.json`), not from memory. Order reset steps by how much
  they lose. Mark anything not yet tested as "not tested" in the doc
  itself.

## Phase 1, Step 7a/7b: Baseline versions from the pack

- **What:** read the Minecraft / NeoForge / mod versions from the Prism
  instance (read-only) into `docs/PORT_BASELINE.md`. Pack 1.9.0, AE2
  26.1.13-beta.
- **Where:** HOST, PowerShell: `mmc-pack.json`, `instance.cfg`, and each
  jar opened as a zip with `[IO.Compression.ZipFile]::OpenRead` to read
  `META-INF/neoforge.mods.toml`.
- **Lessons:**
  - Target the pack's versions, not "latest".
  - The jar file name is a hint; `neoforge.mods.toml` is the source of
    truth (modId, version, dependency ranges).
  - `${file.jarVersion}` in a jar's mods.toml = the version was filled
    in at build time elsewhere; use `MANIFEST.MF` or the file name.
  - Script bug: guard against null before calling methods (`"$x".Trim()`).
  - Maven version ordering can satisfy ranges that look wrong.

## Phase 1, Step 7c: AE2 reference source (two versions)

- **Layout (decision B):** `~/dev/ae-port/ae2-src/<tag>/`, one folder per
  AE2 version, to diff Mixin targets old vs new. Mounted read-only in
  the container at `/workspaces/ae2-src`.
- **Command (WSL):**
  ```
  git ls-remote --tags https://github.com/AppliedEnergistics/Applied-Energistics-2 v26.1.13-beta v19.2.18
  git clone --depth 1 --branch v26.1.13-beta <url> v26.1.13-beta            # c7c1812
  git clone --depth 1 --branch neoforge/v19.2.18 <url> v19.2.18              # db17504
  ```
- **Snag:** 1.21.1-era AE2 tags are `neoforge/v19.x.y`; 26.x tags are
  `v26.x.y`. `ls-remote` matches the END of a name (so `v19.2.18` "found"
  `neoforge/v19.2.18`), but `clone --branch` needs the full name.
- **Lessons:** `--depth 1` = shallow clone, a single snapshot (~35 MB
  each). Cloning a tag = detached HEAD, which is fine for read-only
  reference code. Read the full ref names in the `ls-remote` output.
  PowerShell 5.1 shows Git's stderr progress as red "NativeCommandError";
  it's not a real error.

## Phase 1, Step 7d (prep): JDK 21 image + baseline worktree

- **Decisions:** baseline in a throwaway `eclipse-temurin:21-jdk-noble`
  container (option A, same Java as the original CI), code in a git
  worktree.
- **Command (WSL):**
  ```
  docker manifest inspect eclipse-temurin:21-jdk-noble
  docker run --rm eclipse-temurin:21-jdk-noble java -version    # 21.0.13+9 LTS
  git -C ~/dev/ae-port/AppliedEnhancements worktree add ~/dev/ae-port/baseline-1.21.1 1.21.1-neoforge
  git -C ~/dev/ae-port/AppliedEnhancements worktree list
  ```
- **Lessons:** a worktree = a second working folder on another branch,
  sharing one `.git`. It's removed later with `git worktree remove <path>`.
  A layer shared with the JDK 25 image showed "Already exists". The
  original branch still has gradlew as 644, so use `bash gradlew` there
  (like its CI).

## Phase 1, Step 7d: Baseline build of the ORIGINAL 1.21.1 branch

- **Command (WSL → throwaway JDK 21 container):**
  ```
  docker run --rm --user 1000:1000 -e HOME=/home/ubuntu -w /work \
    -v ~/dev/ae-port/baseline-1.21.1:/work \
    -v ae-port-gradle-cache-jdk21:/home/ubuntu/.gradle \
    eclipse-temurin:21-jdk-noble bash gradlew build --no-daemon --console=plain
  ```
- **Try 1 failed:** "Timeout waiting to lock journal cache … Owner PID".
  VS Code's Java extension had started a Gradle daemon in the dev
  container on the SAME cache volume. Gradle can't hand a lock across
  containers. → **Never share one Gradle home between containers that
  run at the same time.** (Decision: a separate volume.)
- **Try 2 failed:** "Could not create parent directory for lock file". The
  new volume was root-owned (the image has no `/home/ubuntu/.gradle`).
  Fix: `docker run --rm -v <vol>:/cache <img> chown 1000:1000 /cache`.
- **Try 3: BUILD SUCCESSFUL in 3m 53s. 544 tests, 0 failures/errors/skips.**
- **Lessons:** Read the innermost `>` line of a Gradle error: that's the
  real cause. Count tests from `build/test-results/test/*.xml`
  (`<testsuite tests= failures= errors= skipped=>`). A green baseline means
  later failures are ours. Warnings now = a to-do list for later
  (deprecated JEI API, Gradle 9 incompatibilities).

---

## Phase 2, Step 1: Read a working 26.1.2 build (AE2 v26.1.13-beta)

- **What:** read-only comparison of AE2's build files with ours.
- **Findings:**

  | | AE2 26.1.13 (works on 26.1.2) | Ours (1.21.1) |
  |---|---|---|
  | Gradle | 9.2.1 | 8.14.2 |
  | Plugin | ModDevGradle `net.neoforged.moddev` 2.0.141 | NeoGradle `net.neoforged.gradle.userdev` 7.0.170 |
  | Mappings | none configured | Parchment 1.21.1 / 2024.11.17 |
  | Java toolchain | 25 | 21 |
  | NeoForge | 26.1.2.21-beta | 21.1.220 |
  | JEI | `jei-26.1.2-neoforge-api:29.5.0.26` | `jei-1.21.1-neoforge:19.27.0.336` |

- **Lessons:** The best reference for a build upgrade is a big, working
  mod on the SAME target version. Separate what's generic (plugin, Gradle,
  Java, NeoForge) from what's project-specific (AE2's client sourceSet,
  buildSrc, spotless, JMH). Look in `buildSrc/` too, because settings can
  hide there (AE2 sets the Java toolchain in a buildSrc plugin).

## Phase 2, Step 2: NeoGradle → ModDevGradle (still 1.21.1)

- **Sources:** `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (official template) +
  the `neoforged/ModDevGradle` README + MDG source (`ModDevRunWorkflow`).
- **Changes:**
  - `gradle.properties`: `neogradle.subsystems.parchment.*` → `neoForge.parchment.*`
  - `build.gradle`: plugin `net.neoforged.moddev` 2.0.148;
    `neoForge { version = neo_version; runs {…}; mods {…}; addModdingDependenciesTo sourceSets.test }`;
    runs get a type (`client()`, `server()`, `type = "gameTestServer"`, `data()`),
    `argument(s)` → `programArgument(s)`, `modSource` → `mods { sourceSet }`;
    the `net.neoforged:neoforge` dependency is removed;
    `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`.
- **Build (JDK 21 container):**
  `jdk21-build.sh ~/dev/ae-port/AppliedEnhancements <log> clean build --no-daemon`
- **Try 1:** main compiled (MDG 2.0.148 works on Gradle 8.14.2), but
  `compileTestJava` failed: "package net.minecraft… does not exist". MDG
  adds Minecraft/NeoForge only to `main`; NeoGradle also added them to `test`.
- **Try 2 (`unitTest { enable() }`, FML in tests):** 518 tests / 31 failures:
  working dir `build/minecraft-junit` (tests that read source files), Mixin
  classes not loadable, config not loaded. → a different KIND of test.
- **Decision 3b:** plain JUnit now (`addModdingDependenciesTo sourceSets.test`),
  a separate FML test task in Phase 5. MDG's `unitTest` is hard-wired to
  the `test` task (verified in the MDG source), so that needs hand-made wiring.
- **Try 3: BUILD SUCCESSFUL. 544 tests / 99 suites / 0 failures = baseline.**
  The jar has the same size (1,027,617 bytes), the same 530 entries and an
  identical `neoforge.mods.toml`. The "Deprecated Gradle features … Gradle 9"
  warning is GONE: it came from NeoGradle.
- **Lessons:** Change ONE thing (the plugin) and prove it by comparing with the
  baseline: test counts per class AND jar contents. A plugin switch can move
  classpaths and working dirs without touching code. Read the plugin source
  before planning around a feature. Use `clean build` when comparing
  results, so stale files don't skew them.

## Phase 2, Step 3: Gradle wrapper 8.14.2 → 9.2.1 (still 1.21.1)

- **Command (JDK 21 container):**
  ```
  bash gradlew wrapper --gradle-version 9.2.1   # run 1: old Gradle writes the new version
  bash gradlew wrapper                          # run 2: Gradle 9.2.1 writes its own scripts + jar
  bash gradlew clean build --warning-mode all
  ```
- **Changed:** gradle-wrapper.properties (byte-identical to the official MDK's,
  same git blob 23449a2), gradle-wrapper.jar, gradlew, gradlew.bat. gradlew
  stays 100755.
- **Result:** BUILD SUCCESSFUL in 27s. 544 tests / 99 suites / 0 failures,
  jar entries identical to the baseline. `--warning-mode all` shows no Gradle
  deprecations (only our own JEI javac warning).
- **Lessons:** Run the wrapper task twice. `--warning-mode all` lists each
  deprecation, which is the checklist before a major Gradle upgrade.
  Adding `junit-platform-launcher` in advance avoided Gradle 9's "launcher
  not found" test error. gradle-wrapper.jar is executable code in the repo
  (a supply-chain risk); Gradle publishes checksums to verify it.

## Phase 2, Step 4.1: Java toolchain 21 → 25 (still 1.21.1)

- **Change:** `java.toolchain.languageVersion = JavaLanguageVersion.of(25)`.
- **Where:** from now on in the JDK 25 dev image (`ae-port-dev:test`) with the
  volume `ae-port-gradle-cache`, VS Code closed:
  `docker run --rm -v <repo>:/workspaces/AppliedEnhancements -v ae-port-gradle-cache:/home/ubuntu/.gradle -w /workspaces/AppliedEnhancements ae-port-dev:test ./gradlew clean build`
- **Result:** BUILD SUCCESSFUL in 2m17s (NFRT recompiled MC 1.21.1 with
  JDK 25 in ~66s). 544 tests / 99 suites / 0 failures; jar entries identical
  (530); class file major version 65 → **69**. The jar is 6 bytes smaller
  (javac 25 emits slightly different bytecode).
- **New warning:** `sun.misc.Unsafe::objectFieldOffset … will be removed`,
  from netty 4.1.97 in the test JVM: a library warning on Java 24+, not our code.
- **Lessons:** Isolate the Java jump from the game jump. A class file's
  major version is bytes 6–7 of the file (`od -An -j6 -N2`): 65 = Java 21,
  69 = Java 25. Anything with `$` from PowerShell → WSL goes into a script file.

## Phase 2, Step 4.2: Verify every 26.1.2 coordinate BEFORE editing

- **How (read-only):** each repo's `maven-metadata.xml` (`curl`), NeoForge's
  Gradle module metadata (`neoforge-<v>.module`, JSON → its dependencies),
  and the `*.mixins.json` / `neoforge.mods.toml` inside the pack's jars.
- **Findings:** NeoForge 26.1.2.126 ✔. AE2 26.1.13-beta is NOT on modmaven
  anymore → Maven Central. JEI artifact names contain the MC version
  (`jei-26.1.2-neoforge`); ours was hard-coded to 1.21.1. NeoForge itself
  ships MixinExtras (0.5.0 on 21.1, 0.5.5 on 26.1), so our own dependency was
  redundant. Pack mods use Mixin `compatibilityLevel` JAVA_25. FML loader 4 → 11.
- **Lesson:** Check coordinates and what the platform already bundles
  before editing. Parse JSON with a real parser (my `paste - - -` hack
  garbled the field order).

## Phase 2, Step 4.3: The version jump (Phase 2 goal reached)

- **Changes:** gradle.properties: MC 26.1.2, NeoForge 26.1.2.126, Parchment
  removed, AE2 26.1.13-beta, ExtendedAE/AE2WTLib/JEI ranges from the pack, no
  upper bounds (decision). build.gradle: `mavenCentral()`,
  `jei-${minecraft_version}-neoforge`, only the MixinExtras annotation processor
  (0.5.5). mixins.json: `compatibilityLevel: JAVA_25`.
- **Check 1:** `./gradlew dependencies --configuration compileClasspath` →
  BUILD SUCCESSFUL. Everything resolves: neoforge 26.1.2.126, neoform 26.1.2-1,
  appliedenergistics2 26.1.13-beta, jei-26.1.2-neoforge 29.53.0.115,
  sponge-mixin 0.17.4, mixinextras 0.5.5, FML 11.0.15.
  **= Phase 2 goal: Gradle configures cleanly.**
- **Proof of "unobfuscated":** the NFRT steps for 1.21.1 include
  `downloadClientMappings mergeMappings … rename`; for 26.1.2 they don't
  (`downloadClient downloadServer preProcessJar decompile patch …`). No
  mappings step at all.
- **Check 2 (first look):** `compileJava` → 100 errors (javac's default cap),
  all "cannot find symbol": GuiGraphics, ResourceLocation, Icon, ClickType,
  Util, AEKeyRendering. Mostly client/GUI code. That's the input for Phase 3.
- **Lessons:** Prove "configures + resolves" separately from "compiles".
  The NFRT step list shows how Minecraft is prepared. A compile error
  count of exactly 100 is javac's cap, not the real total.

---

## Phase 3, Step 1: The full error list

- **Change (permanent, decision):** `options.compilerArgs.addAll(['-Xmaxerrs', '10000'])`
  in `tasks.withType(JavaCompile)`.
- **Result:** 108 errors, ALL "cannot find symbol", in 27 files (mostly
  client/GUI): GuiGraphics 68, ResourceLocation 24, AE2 `Icon` 7,
  ClickType 6, Util 2, AE2 `AEKeyRendering` 1.
- **Lessons:** javac works in phases. First it resolves names (imports,
  types); only when those succeed does it check method calls, overrides and
  types in detail. So this list is the FIRST LAYER. Fixing it will likely
  reveal more errors (changed method signatures etc.). Don't read the
  first count as the size of the job.

## Phase 3, Steps 2–3: ResourceLocation → Identifier

- **Research (read-only):** "Rosetta stone" = AE2's own port. AE2 19.2.18 imports
  `net.minecraft.resources.ResourceLocation` 188×, AE2 26.1.13 0×, but
  `net.minecraft.resources.Identifier` 156×. Confirmed in the MC 26.1.2 sources
  that MDG/NFRT produce (`build/moddev/artifacts/minecraft-patched-<neo>-sources.jar`):
  only `Identifier.java` exists, with the same `fromNamespaceAndPath(…)`.
- **Fix:** `sed -i -E 's/\bResourceLocation\b/Identifier/g' $(grep -rlw ResourceLocation src)`
  → 13 files (8 main incl. 4 public API classes, 5 tests), 43 lines, 1:1.
- **Result:** 108 → **84** errors (exactly the 24 ResourceLocation errors gone, no new ones).
  The test files are not compiled yet (compileJava fails first).
- **Lessons:** When a type vanishes, look for it in a big mod's port + the
  game sources; confirm the methods you call, not just the class name. Check
  for name collisions before a global rename. Our public API changes type
  too (note for the release notes).

## Phase 3, Steps 4–5: ClickType → ContainerInput, Util moved

- **Research:** `net.minecraft.Util` → `net.minecraft.util.Util` (AE2 19 vs 26 imports;
  `getMillis()` still at Util.java:137). `ClickType` no longer exists; the same
  package has `enum ContainerInput` with the identical constants (PICKUP,
  QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL), and `slotClicked(…)` /
  `clicked(…)` take it now. AE2's `AEBaseMenu` shows the same 1:1 swap.
- **Fix:** `sed -E 's/\bClickType\b/ContainerInput/g'` (3 files, incl. 2 Mixin handlers
  whose parameter types must match `slotClicked`) + the Util import (2 files).
- **Result:** 84 → **76** errors (both groups gone). Left: GuiGraphics, AE2 Icon, AEKeyRendering.
- **Side finding:** AE2 26 moved its client classes into a separate `client` source
  set (`src/client/java`), same packages. Look there for Mixin targets in Phase 4.
- **Lessons:** Three kinds of change so far: rename (ResourceLocation, ClickType),
  move (Util), and probably redesign (GuiGraphics?). A Mixin handler's
  parameters follow the TARGET method, so they change with the game's API.

## Phase 3, Steps 6–7: AE2 client moves (Icon, Blitter, AEKeyRendering)

- **Research:** `appeng.client.gui.Icon` → `appeng.util.Icon` (data only, no more
  `getBlitter()`; draw with `Blitter.icon(icon)`, as in AE2 26 `IconButton`).
  `appeng.api.client.AEKeyRendering` → `appeng.client.api.AEKeyRendering`;
  `getDisplayName(key)` removed → `key.getDisplayName()`.
- **zOffset decision:** Patrick: "B if most likely to retain functionality". Research:
  in 1.21.1, `Blitter.zOffset` set the vertex z (3D GUI with a depth test). MC 26's GUI
  is 2D (`pose()` = `Matrix3x2fStack`); layering = draw order, plus `nextStratum()`
  for a whole new layer. AE2 26 dropped zOffset for the SAME toolbar background.
  `nextStratum()` would put the buttons above everything (a behaviour CHANGE), so
  dropping zOffset is the closest match → done, with a comment. **In-game check:**
  the 4 pattern buttons must be visible and not hidden.
- **Result:** 76 → **68** errors. Only `GuiGraphics` is left (20 files).
- **Lessons:** "Keep the behaviour" means understanding what the old code
  DID (vertex z) and what the new system offers, not just finding a method
  with a similar name.

## Phase 3, Steps 8–9a: GuiGraphics → GuiGraphicsExtractor (the GUI redesign)

- **Research:** MC 26 widgets/screens no longer draw; they "extract a render state".
  Translation (verified in the MC 26 sources, the 1.21.1 NeoForm sources in the baseline
  worktree, AE2 19 vs 26): type → `GuiGraphicsExtractor`; `drawString`→`text`;
  `renderItem`→`item`; `renderTooltip(font, c, x, y)`→`setTooltipForNextFrame(c, x, y)`;
  `renderComponentTooltip`→`setComponentTooltipForNextFrame`; `fill`/`blit`/scissor stay;
  `pose()` = JOML `Matrix3x2fStack`: `pushPose/popPose`→`pushMatrix/popMatrix`,
  `translate(x,y,z)`→`translate(x,y)`, `scale(x,y,1)`→`scale(x,y)`.
  Hooks: buttons override `extractContents` (AbstractButton.extractWidgetRenderState is
  final), screens `extractRenderState`.
- **z decision (Patrick: option 1):** small z (4, 24: label above its own background) →
  dropped (draw order). Large z (700/500 context menus, 240 badge above items) →
  `graphics.nextStratum()` (the MC 26 tool for "above everything drawn before"; AE2 uses
  it for stack sizes over items). All are on the in-game checklist.
- **Fix:** sed for the 1:1 renames (20 files), plus manual edits for z and tooltips.
  My first sed failed on a wrong capture group (`\4` → `\3`); sed validates all
  expressions before touching files, so nothing was half-applied.
- **Result:** GuiGraphics errors gone. Total 68 → **106**: javac reached the NEXT LAYER
  (NBT getters returning Optional, networking `sendToServer`, input events, private
  fields, `@Override` mismatches, …).
- **Lessons:** Count going UP after a fix can be progress: it means javac now gets
  past name resolution into those files. `grep -P '\r'` also matches binary files
  (PNG), so read the file name before panicking.

## Phase 3, Step 9b: GUI render hooks

- **Changes (names only, bodies unchanged):** 3 IconButton subclasses
  `renderWidget(…)` → `extractContents(…)` (AbstractButton.extractWidgetRenderState is
  final and calls it; AE2 26 IconButton:62 does the same); screen `render`/`super.render`
  → `extractRenderState`/`super.extractRenderState`; calling a widget
  `amountField.render(…)` → `amountField.extractRenderState(…)` (AbstractWidget:59, public final).
- **Result:** 106 → **100**. The remaining 7 "does not override" errors are all INPUT hooks
  (onPress(), mouseClicked/Dragged/Released, keyPressed): the next group, since MC 26
  moved to event objects (`MouseButtonEvent`, `KeyEvent`, `InputWithModifiers`).
- **Lesson:** `@Override` is the compiler's safety net in a port: without it, a renamed
  hook would silently stop being called. PowerShell has no `head`, so a pipe to it
  stops the whole command line, and nothing else ran.

## Phase 3, group C: Input events

- **Research:** `net.minecraft.client.input`: `MouseButtonEvent(x, y, MouseButtonInfo(button,
  modifiers))`, `KeyEvent(key, scancode, modifiers)`, `CharacterEvent(codepoint)`, with NO
  modifiers; `InputWithModifiers.hasShiftDown()`… Static `Screen.hasShiftDown()` is gone →
  `Minecraft.hasShiftDown()`. NeoForge 26 `ScreenEvent.CharacterTyped` lost `getModifiers()`
  (checked with `javap` on `neoforge-26.1.2.126-universal.jar`, since NeoForge classes are not in
  MDG's MC sources jar).
- **Decision A (adapters at the edges):** our internal methods keep `(int key, int scanCode,
  int modifiers)` / `(char, int)`. Overrides unpack the event at the top
  (`double mx = event.x(); …`) so the bodies stay identical; calls into MC build events
  (`new KeyEvent(…)`, `new CharacterEvent(cp)`, `new MouseButtonEvent(x, y, new MouseButtonInfo(b, mods))`).
  Chars: modifiers → 0 (MC no longer has them). Mouse into EditBox: modifiers built from
  the live Shift/Ctrl/Alt state, so Shift+click keeps working as in 1.21.
- **Result:** 100 → **81**, input errors 0. (One more input call surfaced mid-step, the next layer.)
- **Lessons:** Records (`event.x()`) make translation easy: unpack once, keep the body.
  When an API drops data (char modifiers), check where the value went before
  deciding it's safe to pass 0. `javap -cp <jar> <class>` lists a class's methods
  without sources.

## Phase 3, group A: NBT (save data)

- **Key question first: does the STORAGE FORMAT change?** No. Only the Java API did:
  - `CompoundTag` getters return `Optional`; the 1.21 defaults are kept exactly with
    `getIntOr(k,0)`, `getLongOr(k,0L)`, `getStringOr(k,"")`, `getCompoundOrEmpty(k)`,
    `getListOrEmpty(k)`, `ListTag.getCompoundOrEmpty(i)`.
  - `contains(k, type)` is gone → `getString(k).isPresent()` / `getCompound(k).isPresent()`
    (verified: these return empty for the wrong tag type).
  - `putUUID/getUUID/hasUUID` are gone → `store/read(k, UUIDUtil.CODEC, …)`; UUIDUtil.CODEC =
    INT_STREAM = the same int[4] that 1.21 wrote.
  - AE2 26 `AEKey.toTagGeneric/fromTagGeneric` take ValueOutput/ValueInput, but use the same codec
    as AE2 19 → a local helper copying AE2 19's body (`AEKey.CODEC` + registry NbtOps) keeps the
    bytes identical.
- **Accepted edge case:** `getList(k, TAG_COMPOUND)` used to give an empty list for a list of
  the wrong element type; now such (corrupted) data makes `read` return null. We only ever
  write compound lists.
- **Phase 4 note:** AE2 26 CPU `readFromNBT(ValueInput)/writeToNBT(ValueOutput)`; our CPU
  save Mixins only got compile fixes here and will need a ValueInput/ValueOutput bridge.
- **Result:** 81 → **38**, NBT errors 0.
- **Lessons:** For save code, check the FORMAT before the API. Reproduce old defaults
  exactly (`…Or`, `…OrEmpty`) instead of sprinkling `.orElseThrow()`. Show the full diff
  before compiling storage changes.

## Phase 3, group D: Networking (client → server)

- **Research:** NeoForge 26 moved `sendToServer` from `net.neoforged.neoforge.network.PacketDistributor`
  (now server→client only) to `net.neoforged.neoforge.client.network.ClientPacketDistributor`
  (javap on the universal jar; AE2: 33× old → 33× new).
- **Fix:** 7 calls in 5 files; `CraftConfirmMenuMixin` keeps the old import for `sendToPlayer`.
- **Dedicated server:** AE2 26 calls `ClientPacketDistributor` from 11 common (`main`) files,
  incl. menus like `AEBaseMenu`/`MEStorageMenu`, so the pattern is the same as ours. Still
  checked with `runServer` in Phase 4/5.
- **Result:** 38 → **31**.
- **Lessons:** Verify every "AE2 does X too" claim before stating it: I had named a file
  (`SetStockAmountMenu`) without checking. A pattern like `PacketDistributor\.` also matches
  inside `ClientPacketDistributor.` (use `\b`). `wsl -- grep 'a|b'` goes through a shell, so
  `|` gets eaten; use a script file.

## Phase 3, E1: Player messages (chat vs action bar)

- **Research:** `Player.displayClientMessage(c, actionBar)` is gone. AE2: 21× → 0, now
  `sendOverlayMessage` 19× + `sendSystemMessage`. Compared the method BODIES (1.21.1 baseline vs 26):
  `ChatListener.handleSystemMessage(msg, boolean)` changed the MEANING of its boolean from
  `isOverlay` to `remote` (chat only); the action bar is the new `handleOverlay(msg)`.
- **Mapping (verified):** `displayClientMessage(c, true)` → `sendOverlayMessage(c)`;
  `displayClientMessage(c, false)` → `sendSystemMessage(c)`. 7 places (5 action bar, 2 chat).
- **Result:** 31 → **24**.
- **Lesson:** Same-looking signatures can change meaning (a boolean parameter
  renamed from `isOverlay` to `remote`). Read the old and new bodies, not just the names.
  A blind replace-all with `sendSystemMessage` would have moved every action-bar message
  into the chat.

## Phase 3, E2: Access changes + our first access transformer

- `Level.isClientSide` field is private in 26 → `isClientSide()` (already existed in 1.21).
- `Inventory.items` is private → `getNonEquipmentItems()`, which returns the SAME list
  (`return this.items`), so `get/set` behave as before.
- `AbstractContainerScreen.imageWidth/imageHeight` became `protected final` (set once via the new
  constructor). Our confirm screen resizes itself while open. **Decision A:** an access
  transformer, `src/main/resources/META-INF/accesstransformer.cfg`, with AE2 26's exact lines:
  `protected-f net.minecraft.client.gui.screens.inventory.AbstractContainerScreen imageWidth` (+ imageHeight).
- **Term:** an access transformer (AT) is a text file a mod ships; NeoForge applies it at load time
  to change the visibility/finality of Minecraft members (`protected-f` = protected, not final).
  MDG picks up the default path automatically and rebuilt the Minecraft artifact
  (`2 actionable tasks: 2 executed` → `createMinecraftArtifacts` re-ran, 44 s).
- **Result:** 24 → **13**.
- **Lessons:** When a field disappears, look for a getter that returns the same object. When
  a field becomes final and you really need to write it, check whether the big mod already
  solved it (here AE2's AT). An AT is a global change for every mod, so keep entries to the minimum.

## Phase 3, E3–E5: remainders, serverLevel, keybinds, blit (one commit each)

- **E6 was not a cause of its own:** the 3 "incompatible types" were E5 (key category String→Category,
  blit Identifier→RenderPipeline). Read javac's `required/found` lines before grouping.
- **E3:** `stack.has/getCraftingRemainingItem()` (NeoForge 1.21) → stack-aware
  `stack.getItem().getCraftingRemainder(stack)`: `@Nullable ItemStackTemplate`, `.create()` → ItemStack.
  13 → 10.
- **E4:** `ServerPlayer.serverLevel()` → `level()` (returns ServerLevel in 26). 10 → 8.
- **E5:** `KeyMapping.Category(AppliedEnhancements.id("category"))` + `event.registerCategory(...)`;
  lang key renamed to `key.category.appliedenhancements.category` in en_us, zh_cn AND the
  LocalizationCompletenessTest list (adapted, not deleted). Binding names unchanged → saved binds
  survive. `InputConstants.getKey(new KeyEvent(key, scan, 0))`. `blit(RenderPipelines.GUI_TEXTURED, tex,
  x, y, u, v, w, h, …)`: u/v moved BEFORE w/h; the old 7-arg blit implied a 256×256 texture → explicit.
  `translate` needs float casts. 8 → **1** (only JEI left).
- **Lessons:** When an argument order changes, write old and new side by side and map each
  value to its slot. Grep tests and resources too when renaming a key.

## Phase 3, group F: JEI internals → compileJava GREEN

- **Research:** `javap` on both JEI jars from the Gradle cache: `mezz.jei.common.Internal.getJeiClientConfigs()`
  → `getClientConfigs()`; `CommandUtil(IClientConfig, IConnectionToServer)` and `giveStack(stack, amount)`
  unchanged. Internal/CommandUtil are JEI internals (not `mezz.jei.api`) → risk on JEI updates.
- **Result:** `compileJava` **BUILD SUCCESSFUL** (first time on 26.1.2).
- **53 warnings, all `[removal]`** (deprecated-for-removal APIs that still work today):
  23× AbstractContainerScreen (WirelessExtendedPatternAccessDuplicateMixin), 21× `net.neoforged.neoforge.items`
  + 6× IItemHandler (ExternalStorageFacadeItemHandlerMixin, ItemHandlerStrategyMixin: NeoForge's old item
  capability), 3× JEI ingredient API. Not errors → recorded for later, not fixed now.
- **Phase 3 path:** 108 → 84 → 76 → 68 → (next layer) 106 → 100 → 81 → 38 → 31 → 24 → 13 → 10 → 8 → 1 → **0**,
  in 17 commits, one cause per commit.
- **Lesson:** "It compiles" ≠ "it works". Next: test compile, the 544 tests, then Mixins at runtime (Phase 4).

## Phase 3: Tests on 26.1.2 (runs 1–3)

- **Run 1:** compileTestJava, 15 errors → adapted 7 test files, never deleted: test AEKeys
  `toTag(HolderLookup.Provider)` → empty `toTag(ValueOutput)`; NBT getters; FML 11 `LoadingModList.of` has a
  3rd `List<ModFile>`; `assertNotNull(item.getCraftingRemainder(stack))`.
- **Run 2:** tests compiled, but JUnit DISCOVERY crashed (0/99 classes ran): `NoClassDefFoundError: …mixinextras…Operation`.
  `gradlew dependencies` showed MixinExtras only on the compile classpaths (NeoForge provides it at game runtime via FML).
  Our removed `implementation` line had covered the tests in 1.21.1 → `testRuntimeOnly 'io.github.llamalad7:mixinextras-neoforge:0.5.5'`.
- **Run 3:** **96/99 classes, 515 tests green with identical per-class counts.** 3 AELIS classes (29 tests) fail in
  `@BeforeAll`: `IllegalStateException: There is no current FML Loader` (NeoForge-patched `SharedConstants.<clinit>`
  → `FMLEnvironment.isProduction()` → `FMLLoader.getCurrent()`). FML 11's loader is an instance (`FMLLoader.create(StartupArgs)`),
  so the 1.21 trick `LoadingModList.of(...)` + `Bootstrap.bootStrap()` no longer works in plain JUnit.
- **Decision (Patrick): B** — build the FML test task now (instead of @Disabled until Phase 5).
- **Lessons:** Compile classpath ≠ runtime classpath; check both before dropping a dependency. One class that fails to
  load can stop JUnit discovery for ALL classes. Compare test results per class against the baseline.

## Phase 3: FML test task — all tests green (545 = 544 + 1 split)

- **Build:** `neoForge.unitTest { enable(); testedMod }` turns `test` into an FML test JVM (junit-fml 11.0.15 reads
  `fml.junit.argsfile`, MDG's `prepareNeoForgeTestFiles` writes vm/program args, working dir `build/minecraft-junit`).
  New `plainTest` Test task = same test classes, classpath without junit-fml. `fmlTestClasses` file-name list:
  `test` includes, `plainTest` excludes; `@Tag("fml")` as second filter/documentation. `check` depends on both.
  `testImplementation net.neoforged:testframework` + our `@BootstrapMinecraft` (= AE2 26's EphemeralTestServerProvider pattern).
- **Rounds:** (1) discovery crash: JUnit LOADS classes to read tags, Mixin-inspection tests can't load in FML → name
  filter first. (2) "Components not bound yet" → ephemeral test server binds item components (AE2's approach).
  (3) 2 tests "missing = null" — identical on 1.21.1 under FML; root cause: the test's fake State overrode
  get/setBigIntegerMissingAmounts but not recordBigIntegerMissing, and the real Mixin's concrete method replaced
  the interface default → completed the fake. (4) fake AE2 registry + DeferredHolder hack removed (real AE2 loaded;
  `setRegistry` is set-once). (5) `new CraftConfirmMenuMixin() {}` is forbidden under a running Mixin system → split:
  payload/formatter stays in FML, Mixin getter/setter checks moved unchanged to plain `CraftConfirmMenuCyclicAmountsTest`.
- **Result:** `clean build` green: plainTest 97/516/0 + test 29/0, jar built against MC 26.1.2.
- **Lessons:** Fakes for a missing environment break once the real environment exists; remove them. A fake for a
  Mixin interface must override EVERY method the real Mixin adds. If a test fails identically on the old version
  under the same conditions, it's not a port regression. Keep each assertion in the environment it needs.

---

## Phase 4, Steps 4.1–4.2: First server start + Mixin target audit

- **4.1:** `./gradlew runServer` (dev image) → `Done (2.124s)!`. A dev server keeps running (the task never
  "finishes"); stop it with `docker stop <container>`. No EULA prompt in the NeoForge dev run. Only 5 Mixin
  WARNs "Error loading class" for optional mods absent from the dev classpath (@Pseudo targets, expected).
  A clean start only proves the EARLY-loaded targets; world/client classes load later.
- **4.2:** read-only audit script (Python, `-I`): for each of the 67 Mixins, resolve the target class (imports,
  inner `$` classes, `targets = "…"`), look it up in AE2 26 src (main/client), the MC sources jar, or the pack's
  mod jars (class bytes contain member names), and check `method = "…"` + `@Shadow` names. Then hand-verified.
- **Verified work list:** W1 AbstractContainerScreenQuickMoveMixin (input handlers + render), W2–W4 CPU save
  Mixins (AE2/AdvancedAE now ValueInput/ValueOutput), W5 ExternalStorageFacadeItemHandlerMixin and W6
  ItemHandlerStrategyMixin (NeoForge's new transfer API: ResourceHandler<ItemResource> instead of IItemHandler).
- **Lessons:** Mixin targets are strings, so build your own checker. A name match is not enough: same name,
  new parameters (readFromNBT(CompoundTag…) vs (ValueInput)) still breaks the injector. Anonymous inner
  class targets (`HandlerStrategy$1`) and descriptors in `method = "insert(Lnet/…/IItemHandler;…)J"` are
  fragile across versions. Treat a script's "OK" as "not obviously broken" and confirm with a runtime start.

## Phase 4, Step 4.2: Mixin inventory (read-only)
- What: a table of all 67 Mixins: side (common/client), @Pseudo, target class, and whether the target
  still exists in AE2 26.1.13 (src/main or src/client) or MC 26.1.2 (sources jar).
  Result: 50 AE2 + 4 MC targets all exist; 13 external (@Pseudo, other mods).
- Why: Mixin targets are not checked by javac, and a Mixin is applied only when its TARGET CLASS LOADS.
  So a clean server start proves little: most AE2 crafting classes load only once a grid/crafting job exists.
- Command: mixin-inventory.sh (perl reads @Mixin(...), resolves imports, looks the class up) → out/phase4-mixin-inventory.txt.
- Lesson: "class exists" ≠ "Mixin works". The next layer is the method names/descriptors inside
  @Inject/@WrapOperation. Already visible: ItemHandlerStrategyMixin (HandlerStrategy$1) and
  ExternalStorageFacadeItemHandlerMixin reference NeoForge's old IItemHandler API; AE2 26 moved to the new
  transfer API (ResourceHandler<ItemResource>, Transaction) → these two will fail to apply.
- Gotcha: anonymous classes are named Outer$1, $2… by ORDER in the file; reordering upstream silently changes the target.

## Phase 4, Step 4.3a: method-level Mixin check (read-only, partial)
- What: mixin-methods.pl checks every `method = "…"`, `@At(target = "L…;…")`, @Accessor and @Invoker of the
  54 AE2/MC Mixins against the AE2 26.1.13 sources, the unpacked MC 26.1.2 sources (~/dev/ae-port/mc-src/26.1.2)
  and the NeoForge 26.1.2.126 jar (copied out of the gradle volume with a throwaway container).
  Result: 3 BROKEN (QuickMove `render`; ExternalStorageFacadeItemHandler ×2), 2 CHECK (generic `C handler`
  in HandlerStrategy), 131 "OK", 13 SKIP (external).
- Why: Mixin targets are strings; javac never sees them.
- Lessons:
  * Terms: an INJECTION POINT (@At) is the spot inside the target method; a DESCRIPTOR is the JVM signature
    string, e.g. (Lnet/minecraft/world/item/ItemStack;Z)V.
  * @Inject/@WrapMethod need the method DECLARED in the target class. An @At(INVOKE) target's owner may be a
    subclass (the method is declared in a superclass), so the check must walk up `extends`.
  * Check your checker: the first two runs had false BROKENs (superclass methods, `KeyCounter[]`, call lines
    matched as declarations) AND false OKs (type variables `C` matched anything; missing NeoForge classes skipped).
  * "Name exists" ≠ "applies": 106 OK rows are name-only. Known counter-examples: CraftingCpuLogicBatchMixin
    readFromNBT/writeToNBT (handler takes CompoundTag+Provider, AE2 26 takes ValueInput/ValueOutput) and
    QuickMove mouseClicked/Dragged/Released (now MouseButtonEvent). An @Inject handler's parameters must equal
    the target's parameters (+ CallbackInfo[Returnable]).
  * net.neoforged.neoforge.items.* still exists in NeoForge 26 (deprecated for removal), so IItemHandler
    descriptors resolve. What broke is that AE2 26 no longer CALLS that API.

## Phase 4, Step W1: AbstractContainerScreenQuickMoveMixin → MC 26 input/render hooks
- What: the 3 @Inject handlers for mouseClicked/mouseDragged/mouseReleased now take the MC 26 parameters
  (MouseButtonEvent[, boolean doubleClick | double dx, double dy]) and unpack x()/y()/button() at the top;
  the bridge interfaces are unchanged (= Group C decision A, adapters at the edges).
  `method = "render"` → `"extractRenderState"` (same parameters).
- Why: HANDLER SIGNATURE RULE: an @Inject handler must take exactly the target method's parameters, in order,
  plus CallbackInfo/CallbackInfoReturnable. javac can't check this (the target is a string); Mixin throws
  InvalidInjectionException when the target class loads. Client Mixin → runServer never loads it.
- Command (in the JDK 25 container): jdk25-build.sh <repo> phase4-w1-build compileJava plainTest
  → BUILD SUCCESSFUL, plainTest 97/516/0.
- Lesson: "compiles + tests green" says NOTHING about a Mixin's handler signature. The proof is a client that
  opens a container screen (runClient / TEST instance) with no InvalidInjectionException in latest.log.
## Phase 4, Step W2: CraftingCpuLogicBatchMixin save/load → ValueInput/ValueOutput
- What: AE2 26 CraftingCpuLogic.writeToNBT(ValueOutput) / readFromNBT(ValueInput) (was CompoundTag + Provider).
  Handlers now take those; save = output.store(KEY, CompoundTag.CODEC, writeRuntime(runtime, level.registryAccess())),
  load = input.read(KEY, CompoundTag.CODEC) + input.lookup() for the registries. AelisCycleExecutionNbt untouched.
- Why: ValueInput/ValueOutput = MC 26's save abstraction (a reader/writer object instead of the raw tag).
  CompoundTag.CODEC is Codec.PASSTHROUGH and TagValueOutput.store ends in output.put(name, tag), so the NBT on
  disk is the same compound under the same key as 1.21's data.put → old saves stay readable.
- Gotcha 1: ValueOutput has NO registries. Save takes them from cluster.getLevel() (the CPU is only saved by its
  core block entity: CraftingBlockEntity.saveAdditional → cluster.writeToNBT, guarded by isCoreBlock && cluster != null).
- Gotcha 2: ValueInput.lookup() is @Deprecated (not for removal) → 1 javac warning. AE2 26 uses it too
  (AEBaseBlockEntity:147), because at LOAD time the block entity may not have a level yet.
- Command (JDK 25 container): jdk25-build.sh <repo> phase4-w2-build compileJava plainTest → 97/516/0.
- Lesson: read the WHOLE declaration when researching an API (my grep listing dropped the @Deprecated line),
  and read every warning in a green build. Still unproven at runtime: needs a CPU with an AELIS job saved + reloaded.
## Phase 4, Step W3: AdvancedAeCraftingCycleMixin save/load → ValueInput/ValueOutput
- What: AdvancedAE 26.1.7 AdvCraftingCPULogic.writeToNBT(ValueOutput) / readFromNBT(ValueInput), read from BYTECODE
  (no source checkout): copy the jar out of the pack to out/jars, then in the JDK container
  `javap -p -cp <jar> <class>` (members) and `javap -p -c …` (method bodies). Same store/read pattern as W2.
- Why different from W2: the class has a field `cpu` (type AdvCraftingCPU), not `cluster`, and AdvancedAE is not a
  compile dependency. A @Shadow field must be declared with its exact type, so we cannot shadow `cpu`.
  DECISION A: registries on save = ServerLifecycleHooks.getCurrentServer().registryAccess() (null guard logs + skips).
  Verified equal: ServerLevel's constructor passes server.registryAccess() to Level (ServerLevel.java:245).
- Command (JDK 25 container): jdk25-build.sh <repo> phase4-w3-build compileJava plainTest → 97/516/0.
- Lessons:
  * javap = the JDK's class-file reader; it is the "source" for mods you only have as a jar.
  * A @Pseudo Mixin (target may be absent) on a mod that is not on the dev classpath is NEVER applied in runServer/
    runClient → only a TEST instance with that mod proves it.
  * Gradle compiles incrementally: this build shows 1 lookup() warning (only the changed file was recompiled);
    a clean build shows 2 (W2 + W3). Don't count warnings from incremental builds.
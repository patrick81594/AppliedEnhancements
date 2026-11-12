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

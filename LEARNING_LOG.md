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

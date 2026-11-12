# Dev setup (26.1.2 port)

How to start, stop, rebuild and reset the development environment.
See `LEARNING_LOG.md` for how and why it was built this way.

## Layout

| What | Where (WSL) | In the container |
|---|---|---|
| Repo | `~/dev/ae-port/AppliedEnhancements` | `/workspaces/AppliedEnhancements` (bind, rw) |
| Built jars for testing | `~/dev/ae-port/out` | `/workspaces/out` (bind, rw) |
| AE2 reference source | `~/dev/ae-port/ae2-src` | `/workspaces/ae2-src` (bind, **read-only**) |
| Gradle cache | Docker volume `ae-port-gradle-cache` | `/home/ubuntu/.gradle` |

- Image: `.devcontainer/Dockerfile`, based on `eclipse-temurin:25-jdk-noble`
  (JDK 25, Ubuntu 24.04) + git + sudo.
- User: `ubuntu` (UID/GID 1000 = the WSL user, so files on the host stay
  yours). Passwordless `sudo` for quick experiments; anything permanent
  goes into the Dockerfile.
- The `out/` and `ae2-src/` folders must exist on WSL, or the container
  won't start (`bind source path does not exist`):
  `mkdir -p ~/dev/ae-port/out ~/dev/ae-port/ae2-src`

## Requirements (host, Windows)

- Docker Desktop with WSL integration enabled for Ubuntu
  (Settings → Resources → WSL integration).
- VS Code with the **WSL** and **Dev Containers** extensions (Microsoft).

## Start

1. Ubuntu terminal: `cd ~/dev/ae-port/AppliedEnhancements && code .`
   (the ` .` matters: a *folder* must be open, otherwise the container
   commands don't appear).
2. Bottom-left should say **WSL: Ubuntu**. Click it → **Reopen in Container**
   (or `F1` → `Dev Containers: Reopen in Container`).
3. Bottom-left now says **Dev Container: AE port (JDK 25)**. The VS Code
   terminal runs inside the container.

Quick check in that terminal:
```
id; java -version; ls /workspaces; ./gradlew --version
```

## Stop

- Close the VS Code window: the container stops by itself shortly after.
- Or `F1` → `Dev Containers: Reopen Folder in WSL` to go back to the WSL
  window without the container.
- Nothing is lost: the repo and `out/` live on WSL, and the Gradle cache
  lives in its volume.

## Rebuild (after changing `.devcontainer/*`)

- `F1` → `Dev Containers: Rebuild Container`: rebuilds the image (reusing
  cached layers) and recreates the container.
- `F1` → `Dev Containers: Rebuild Container Without Cache`: rebuilds every
  layer from scratch (e.g. to get fresh apt packages or a newer base image).
- Test the Dockerfile on its own first, without VS Code (WSL):
  ```
  docker build -t ae-port-dev:test ~/dev/ae-port/AppliedEnhancements/.devcontainer
  ```

## Reset (run in WSL; each step loses more)

| Level | Command | What you lose |
|---|---|---|
| 1. Container | `docker rm -f $(docker ps -aq --filter label=devcontainer.local_folder)` | Only things changed in the container outside the mounts (e.g. a `sudo apt install`). |
| 2. Images | `docker images` → `docker rmi <vsc-appliedenhancements-... names>` and `docker rmi ae-port-dev:test` | Build time: the next start rebuilds the image. |
| 3. Gradle cache | `docker volume rm ae-port-gradle-cache` (remove the container first) | All downloaded Gradle distributions and dependencies; the next build downloads everything again. |
| 4. Everything Docker | `docker system prune` (asks first; add `--volumes` for volumes too) | **All** unused containers, images and networks, including other projects'. Use with care. |

Never touched by any reset: the repo, `out/`, `ae2-src/` (they are WSL folders).

## What runs where

| Task | Where | Why |
|---|---|---|
| `./gradlew build`, `test` | Container | Pinned JDK and tools, same for every run. |
| `./gradlew runServer`, `runGameTestServer` | Container | Headless: no window needed. |
| `./gradlew runClient` | Host (see below) | Needs a window and the GPU (OpenGL). |
| Playing / testing in FTB Evolution 2 | Host, Prism Launcher, a **TEST copy** of the instance | Never the real instance. |
| git | Container terminal or WSL | Both use the same repo. The push login is stored by Windows Git Credential Manager (WSL); inside the container, VS Code should forward your credentials (not tested yet; the push from WSL is proven). |

### runClient inside the container (explained, NOT set up)

Minecraft needs a display and OpenGL. The options, if ever wanted:
- **WSLg (Wayland/X11 from WSL):** Windows 11 ships a display server for WSL
  apps. VS Code already forwards a Wayland socket into the container. X11
  would also need the `/tmp/.X11-unix` socket mounted and `DISPLAY` set.
  Minecraft/LWJGL also need GL libraries (mesa) in the image, and GPU
  acceleration through WSLg into a container is not guaranteed. Software
  rendering (llvmpipe) is likely to be very slow.
- **An X server on Windows** (e.g. VcXsrv) with `DISPLAY=host.docker.internal:0`:
  similar library needs, usually worse performance.
- **Recommended:** run `runClient` on the host with a local JDK 25,
  or test the built jar in the TEST instance.

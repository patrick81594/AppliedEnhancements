#!/usr/bin/env bash
# Prepares the PACK-TEST server: a copy of the FTB Evolution 2 1.9.0 server (same mods/configs/KubeJS) with our
# current jar, the TEST-ONLY packtests jar and the game-test JVM flags. Host: WSL. Idempotent; the original
# server-1.9.0/server is never modified. A fresh superflat world is created on every run (packtest-run.sh).
set -euo pipefail
SRC=$HOME/dev/ae-port/server-1.9.0/server
P=$HOME/dev/ae-port/server-1.9.0/packtest
LIBS=$HOME/dev/ae-port/AppliedEnhancements/build/libs

if [ ! -d "$P" ]; then
  echo "creating the pack-test copy (once)…"
  mkdir -p "$P"
  # Everything except worlds, logs, backups and the console pipe.
  tar -C "$SRC" --exclude=./world --exclude=./ae-test-server --exclude=./logs --exclude=./ftbbackups3 \
      --exclude=./crash-reports --exclude=./console.in -cf - . | tar -C "$P" -xf -
fi

# Our jars: exactly the current build (main + test-only packtests).
rm -f "$P"/mods/appliedenhancements-*.jar
cp "$LIBS/appliedenhancements-1.1.6.jar" "$LIBS/appliedenhancements-packtests-1.1.6.jar" "$P/mods/"

# Game tests on: NeoForge registers mod tests, AE2 registers its test plots (incl. ours).
cat > "$P/user_jvm_args.txt" <<'EOF'
-Xmx8192M
-Dneoforge.enableGameTest=true
-Dappeng.tests=true
-Dappliedenhancements.packtests=true
EOF

# server.properties: FTB defaults + a flat creative test world without mobs/structures.
{ grep -vE '^(level-name|level-type|gamemode|force-gamemode|generate-structures|spawn-monsters|spawn-animals|online-mode|spawn-protection|pause-when-empty-seconds)=' "$SRC/default-server.properties"
  # 0 = never pause: MC 26 pauses an empty server after 60 s, which would freeze the game tests.
  printf 'pause-when-empty-seconds=0\n'
  printf 'level-name=packtest-world\nlevel-type=minecraft\\:flat\ngamemode=creative\nforce-gamemode=true\n'
  printf 'generate-structures=false\nspawn-monsters=false\nspawn-animals=false\nonline-mode=false\nspawn-protection=0\n'
} > "$P/server.properties"
printf '# Accepted by the server owner on 2026-11-17 (https://aka.ms/MinecraftEULA)\neula=true\n' > "$P/eula.txt"

echo "pack-test server: $P"
echo "mods: $(ls "$P/mods" | wc -l)   ours: $(ls "$P/mods" | grep -i appliedenhancements | tr '\n' ' ')"
echo "jvm args: $(tr '\n' ' ' < "$P/user_jvm_args.txt")"

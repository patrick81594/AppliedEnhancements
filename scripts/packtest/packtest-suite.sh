#!/usr/bin/env bash
# Boots the pack-test server ONCE and runs several test selectors ONE AFTER ANOTHER (needed when tests change a
# server-wide setting, e.g. the AELIS returned-item switch). Host: WSL.
# Usage: packtest-suite.sh <logname> <selector> [<selector> ...]
#   e.g. packtest-suite.sh packtest-suite-1 'ae2:appliedenhancements/smoke_*' 'ae2:appliedenhancements/sugar_chunks_on_8_bottles'
# Live: tail -f ~/dev/ae-port/out/<logname>.log
set -uo pipefail
NAME=$1; shift
P=$HOME/dev/ae-port/server-1.9.0/packtest; C=ae-packtest; LOG=$HOME/dev/ae-port/out/$NAME.log
# MC 26 prints e.g. "2 required test(s) failed :(" / "All required tests passed :)".
SUMMARY='All required tests passed|required test(\(s\)|s)? failed|No tests (found|to run)|Unknown or incomplete command|Incorrect argument|Found no|no_tests|No matches for selector'
bash "$HOME/dev/ae-port/tools/packtest-prep.sh" || exit 1
rm -rf "$P/packtest-world" "$P/logs"; : > "$P/console.in"
docker rm -f $C >/dev/null 2>&1
docker run -d --name $C --user 1000:1000 -v "$P:/srv" -w /srv ae-port-dev:test \
  sh -c 'tail -n 0 -f console.in | sh run.sh' >/dev/null
start=$(date +%s)
alive() { docker ps -q -f name=$C | grep -q .; }
snap() { docker logs $C > "$LOG" 2>&1; }
# 1. Boot.
booted=no
while [ $(( $(date +%s) - start )) -lt 900 ]; do
  sleep 5; snap
  alive || break
  grep -qE 'Crash Report|Exception in thread "main"|Failed to start the minecraft server' "$LOG" && break
  if grep -qE 'Done \([0-9.]+s\)!' "$LOG"; then booted=yes; break; fi
done
echo "boot=$booted after $(( $(date +%s) - start ))s   (log: $LOG)"
# 2. Selectors, one at a time: wait until a NEW summary line appears.
if [ $booted = yes ]; then
  for SEL in "$@"; do
    before=$(grep -cE "$SUMMARY" "$LOG")
    printf '%s\n' "test run $SEL" >> "$P/console.in"
    t0=$(date +%s); result=timeout
    while [ $(( $(date +%s) - t0 )) -lt 900 ]; do
      sleep 5; snap
      alive || { result=server-died; break; }
      now=$(grep -cE "$SUMMARY" "$LOG")
      if [ "$now" -gt "$before" ]; then result=$(grep -E "$SUMMARY" "$LOG" | tail -1 | sed 's/.*]: //' | cut -c1-120); break; fi
    done
    echo "[$SEL] $result  ($(( $(date +%s) - t0 ))s)"
  done
fi
printf '%s\n' "stop" >> "$P/console.in"; sleep 25; snap; docker rm -f $C >/dev/null 2>&1
echo "--- failures"
grep -nE 'failed at|\(optional\) .* failed' "$LOG" | cut -c1-300 | head -20
echo "--- rig reports"
grep -E '\[AE-PACKTEST\]|Chance top-up' "$LOG" | sed 's/.*\[AE-PACKTEST\]/[AE-PACKTEST]/; s/.*AppliedEnhancements\/\]: /[TOPUP] /' | head -80
echo "--- AELIS decisions (debug.log)"
grep -hE 'AELIS (plans .* in chunks|returned-item check|handed|skipped producer)' "$P/logs/debug.log" 2>/dev/null | sed 's/.*AppliedEnhancements\/\]: //' | cut -c1-260 | tail -15
echo "--- errors naming us / AE2 / our test mod"
grep -nE '\[(ERROR|FATAL)\]' "$LOG" | grep -iE 'appliedenhancements|aelis|appeng|packtest' | cut -c1-250 | head -10
# Exit code: 0 = every selector printed "All required tests passed", 1 = anything else.
if [ "$booted" = yes ] && [ "$(grep -cE 'All required tests passed' "$LOG")" -ge "$#" ] \
   && ! grep -qE 'failed at|required test(\(s\)|s)? failed|No matches for selector' "$LOG"; then
  echo "RESULT: ALL PASSED"; exit 0
fi
echo "RESULT: FAILURES (see above)"; exit 1

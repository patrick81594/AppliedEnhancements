# In-game checklist for the 26.1.2 port

What "compiles" and "the Mixin applies" cannot prove. Work through this in the TEST instance
(`FTB Evolution 2 - AE-TEST`) in a creative world. Never use the real pack instance for this.

Each item says what to look for and which port change it protects. Mark it `[x]` when it passes, or write
down what happened. After every session run `scripts/check-test-log.ps1` and note its result.

How to run a session (Windows host, PowerShell):

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-test.ps1 -Deploy
```

That deploys the newest build, launches the instance, joins the world and runs the log check.
Undo a deploy with `scripts\deploy-test.ps1 -Rollback`.

Status of this list: collected from the notes made during the port (Phases 3 and 4). Nothing here has been
checked in-game yet.

## 0. Startup

- [x] The game reaches the title screen with the mod installed (first load of the 26 client Mixins and of
      the Mixins for AdvancedAE, ae2wtlib and ExtendedAE). *2026-11-14, jar 07C3D660…: 433 mods loaded.*
- [x] A creative world loads. *2026-11-14: world "AE Test", joined after 357 s.*
- [x] `check-test-log.ps1` reports no Mixin failures, no errors naming `appliedenhancements`, no crash report.
      *2026-11-14. `logs/debug.log` showed 21 of our Mixins applied at that point.*

## 1. Controls

- [x] Options → Controls shows a category named "Applied Enhancements" with both bindings
      (right-click and Alt+right-click). *Protects: KeyMapping category change, new language key
      `key.category.appliedenhancements.category`.* *2026-11-14: header and both bindings seen.*
- [x] Rebinding one of them, restarting the game, and the binding is still set.
      *2026-11-14: "Open Item Actions Menu" rebound to `-`; 2026-11-17 after a restart still `-` (options.txt).*

## 2. ME terminal: network item context menu

- [x] The context menu opens on an item in the terminal and is drawn above everything else.
      *Protects: GUI layering (`nextStratum`), quick-move/context-menu Mixin on container screens (W1).*
      *2026-11-14. Extract One / Extract Stack and inserting also work (payload to the server).*
- [x] Typing a number, Enter and Escape work in the menu's amount field. *Protects: input event adapters.*
      *2026-11-14. The field appears after clicking "Custom Amount…"; too-large numbers turn red.*
- [x] Feedback messages after a menu action are visible. *2026-11-14: only "Copy Item ID" shows a message
      ("Copied: …"); extract/insert show none by design. It went to the action bar, which the terminal covers.
      2026-11-17: now an "Item Menu" toast (top-right, drawn above the screen), verified.*
- [x] "Start Craft" in the menu starts a craft for an item with a pattern. *2026-11-14.*
- [ ] The "force amount too large" message is visible. *Was chat (hidden behind the AELIS screen); since
      2026-11-17 a "Crafting plan" toast sent by the server (same route as the verified quick-move result).
      Only happens with huge missing amounts; not forced in game yet.*
- [x] Scrolling the terminal works while the menu is closed; while it is open the wheel is blocked on purpose
      (`NetworkItemContextMenu.mouseScrolled` returns `isOpen()`, same as 1.21.1, so the menu keeps pointing at
      its item). *2026-11-17: scrolls when closed, blocked while open, works again right after closing.*

## 3. JEI integration

Our menu key ("Open Item Actions Menu") works on JEI's item list and bookmark list. The ME entries (Extract,
Craft in ME, Search in ME) only appear when the screen underneath is an ME terminal, not on JEI's own recipe
screen.

- [x] The JEI item context menu works; its feedback is visible. *2026-11-17: opens on top in the item list and
      the bookmarks; Show Recipes/Uses, Search in ME, Extract One from ME work in the terminal. "Copied name"
      went to the action bar behind the terminal; now an "Item Menu" toast, verified.*
- [x] Bookmark missing works and its result is visible. *2026-11-17: missing materials bookmarked. Was action bar
      ("added") + chat ("unsupported"), both behind the AELIS screen; now one "Crafting plan" toast, verified.*
- [x] Cheat-give from the menu follows JEI's own give mode. *Protects: JEI internal config accessor
      (`Internal.getClientConfigs()`), which can break on any JEI update.* *2026-11-17: Give One / Give Stack
      shown with cheat mode on and working.*

## 4. Pattern access terminal

- [x] The four pattern buttons are visible and not hidden behind other GUI parts: the header action
      button and the Duplicate / Invalid / Quick-Pattern-Move toggles. *Protects: removed `zOffset`.*
      *2026-11-14.*
- [x] The three toggle buttons react to clicks, and their labels are readable. *2026-11-14, tooltips too.*
- [x] The duplicate badge (machine icon or letter) is drawn above the pattern item.
      *2026-11-14 FAILED (drawn behind: MC 26 draws the slots after drawFG). Fixed: badges are drawn from
      `ScreenEvent.Render.Foreground`. 2026-11-17 passed.*
- [x] The expanded terminal header looks seamless (no visible seam or shifted strip).
      *Protects: new `blit` parameter order.* *2026-11-17.*
- [x] Quick move: click, drag a selection box, release. Patterns move as expected. *Protects: W1
      (mouse events, `extractRenderState`).* *2026-11-14 FAILED (selection and highlight one row too low:
      the toolbar offset was added twice). Fixed. 2026-11-17 passed: select, Cut, Paste into another provider.*
- [x] The quick-move context menu opens above everything else. *2026-11-17.*
- [x] Quick-move feedback ("Cut N patterns.", "Moved N patterns.", "Batch move failed: …") is readable while
      the terminal is open. The header Cut all / Paste all icons only show with Quick Move ON and Duplicate OFF.
      *Was: "duplicate-pattern messages in the action bar" (wrong name; these are the quick-move messages).
      2026-11-17: Cut all / Paste all worked, but the action bar (Cut) and chat (Moved/Failed) were hidden behind
      the terminal: Minecraft draws the HUD before the screen. Changed to a toast (drawn after the screen),
      verified 2026-11-17.*
- [ ] The same checks in ExtendedAE's extended pattern terminal and in a wireless pattern access terminal
      (ExtendedAE / ae2wtlib). *Protects: the optional-mod Mixins, which are never applied in dev runs.*
      *2026-11-17: ExtendedAE Extended Pattern Access Terminal passed (its row Mixins applied, no crash).
      Wireless pattern access terminal (ae2wtlib) not tested yet.*

## 5. Crafting: amount screen and AELIS confirm screen

**Needs the AELIS planner:** the AELIS screen only replaces AE2's confirm screen for plans made by AELIS, and
`crafting.aelis.enable_automatic_planner` is `false` by default (same on 1.21.1). Turn it on first, e.g. in game:
Mods → Applied Enhancements → Config → Crafting → AELIS. With it off you get AE2's own screen (the "TTC" sorting
and throughput tooltips there come from the AE2 Crafting Time mod, not from us).

- [x] The craft amount screen opens and accepts an amount. *2026-11-17.*
- [x] The AELIS confirm screen opens; the AELIS mark is drawn at 24×24 and complete. *2026-11-17; filters,
      search and "AE2 view" work too.*
- [x] Shift + start (force start) works. *2026-11-17.*
- [x] The crafting graph can be dragged, zoomed and scrolled, and stays where it is put.
      *2026-11-17 FAILED in the normal view (tree only drawn in the bottom-right of its area): MC 26
      `enableScissor` applies the current pose, so the old `leftPos`/`topPos` offset counted twice. Fixed,
      2026-11-17 passed.*
- [x] Toggling fullscreen resizes the screen without glitches. *Protects: access transformer for
      `imageWidth` / `imageHeight`.* *2026-11-17; Exit fullscreen and Esc both work.*
- [ ] A wireless crafting terminal (ae2wtlib) opens and can start a craft.

## 6. Crafting CPUs: save and reload

- [x] AE2 crafting CPU: start a long job, save and quit while it runs, reload. The job is still there and
      finishes. *Protects: W2 (CPU save hooks on `ValueInput` / `ValueOutput`).* *2026-11-17: processing
      pattern 1 Cobblestone → 1 Stone into a plain chest (the job waits on purpose), 64 Stone; survived the
      reload and finished after inserting stone through the terminal.*
- [x] The same with a job that uses an AELIS cycle (cyclic or seed-protected recipe). *2026-11-17: processing
      pattern 1 Dirt → 2 Dirt, 4 Dirt stored, request 64 (AELIS screen shows "Cyclic qty"). After save and quit,
      `tools/find-nbt-key.py` found `appliedenhancementsCycleRuntime` in exactly the chunk of the crafting CPU
      (0 before the job). After reloading the job was still there.* W2 only writes this key while the cycle
      phase is unfinished, so a non-cyclic job does not test it.
- [x] AdvancedAE quantum computer: the same two checks. *Protects: W3 and W4.* *2026-11-17: a single Quantum
      Computer Core forms a computer on its own (cable on top or bottom). Dirt cycle job on it, chosen with the
      AELIS CPU selector; after save and quit the key was only in the quantum core's chunk (the AE2 CPU's job had
      been cancelled, which also removed the key there); after reloading the job was still on the quantum core,
      0 AELIS save/load lines, 0 errors. debug.log shows all three AdvancedAE Mixins applied. W4 itself only acts
      on Data Energistics "virtual orders"; that mod is not in the pack, so W4 is proven to APPLY, not to work.*
- [x] After reloading, `latest.log` has no "Cannot save AELIS …" or "Invalid AELIS …" error lines.
      *2026-11-17, both reloads; no "Cancelling AE2 crafting job" either.*

## 7. Storage bus and export bus

Config switches involved (`appliedenhancements-common.toml`, both default `true`):
`performance.storage_bus.enable_slot_index` (`ENABLE_STORAGE_BUS_SLOT_INDEX`) and
`performance.io_bus.enable_slot_routing` (`ENABLE_IO_BUS_OPTIMIZATION`). Do the checks with both on, then once
with both off. Set the storage bus priority above the drive cells (e.g. 10) so inserts go to the chest.

- [x] Storage bus on a chest: items in the chest show in the terminal; inserting through the terminal
      puts them into the chest; extracting takes them out. Counts stay correct, nothing is duplicated or lost.
      *Protects: W5 (slot index on the transfer API).* *2026-11-17, both switches on: 2×32 Iron + 16 Gold shown
      as 64/16, 64 Cobblestone inserted into the chest, 10 Iron extracted → 54 left. debug.log: W5 applied to
      `ExternalStorageFacade$ResourceHandlerFacade`, W6 to `HandlerStrategy$1`.*
- [x] Take items out of the chest by hand, then extract the same item through the terminal: the amount
      delivered matches what is really there. *2026-11-17, both switches on: 20 Iron removed by hand, terminal
      showed 34, extracting all gave exactly 34 and left none. (W5 dropped the old "re-check on retry" hook;
      this is the case it covered.)*
- [x] The same on a large multi-slot inventory. *2026-11-17, both switches on.*
- [x] Fluid storage bus on a tank still works (the storage-bus Mixin now also sits on the fluid facade).
      *2026-11-17, both switches on.*
- [x] Export bus into a chest and into a multi-slot machine: items arrive, the count is right.
      *Protects: W6 (slot hints on the transfer API).* *2026-11-17, both switches on: 64 Cobblestone → chest
      exactly 64 / network 0; Iron into a furnace only into its input slot, totals unchanged.*
- [x] ME interface pushing items into an adjacent inventory. *2026-11-17, switches off: interface set to 32 Coal
      stocked exactly 32 (all the network had) and no more. Note: an export bus exports from network storage, not
      from an interface's buffer, so it stays idle once the network is empty; use a hopper/pipe on the interface
      to move items out of it.*
- [x] With both switches OFF (via Configured, applied live without a restart): storage bus chest incl. the
      remove-by-hand case, export bus into a chest. *2026-11-17: all counts matched (AE2's own path).*

## 8. Dedicated server (later)

- [ ] A client connected to a dedicated server can use the craft confirm screen and batch pattern moves
      without a `NoClassDefFoundError` on the server. *Protects: use of the client-package class
      `ClientPacketDistributor` in common code.*

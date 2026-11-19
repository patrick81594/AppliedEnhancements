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

Status of this list (2026-11-17): sections 0–9 worked through in FTB Evolution 2 1.9.0; open items are marked
`[ ]`. Tasks for later are at the end ("Later").

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
- [x] The same checks in ExtendedAE's extended pattern terminal and in a wireless pattern access terminal
      (ExtendedAE / ae2wtlib). *Protects: the optional-mod Mixins, which are never applied in dev runs.*
      *2026-11-17: wireless pattern access terminal (ae2wtlib `WATScreen`, linked through the Wireless Access
      Point's link slots): toolbar, toggles, badges on top, quick move incl. Cut/Paste and toast all work.*
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
- [x] A wireless crafting terminal (ae2wtlib) opens and can start a craft. *2026-11-17: items shown, item menu
      (extract, Copy Item ID toast), craft amount → AELIS screen → craft started. `AE2WTLibCraftingTerminalHandlerMixin`
      (a clamp for negative "accessible amounts", only visible with overflowing storage) applied, no errors.*

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

## 9. Without the optional mods

Instance "AE-MINIMAL-TEST", created with `scripts/make-minimal-instance.ps1`: NeoForge 26.1.2.126 + AE2 +
GuideME (AE2's only required mod) + this mod. No JEI, ExtendedAE, ae2wtlib, AdvancedAE or Configured.
Without Configured there is no in-game config screen (this mod never registered its own, also not on 1.21.1);
set the AELIS planner in `config/appliedenhancements-common.toml`.

- [x] The game starts and a world loads. *2026-11-17: 5 mods; all 13 optional Mixin targets (ExtendedAE,
      ae2wtlib, AdvancedAE, Configured, AE2 Crafting Tree, Useless) skipped with a Mixin warning, no failures,
      no errors naming the mod.*
- [x] ME terminal and item menu work (no JEI entries). *2026-11-17.*
- [x] Pattern access terminal: badges, quick move, Cut/Paste, toasts. *2026-11-17.*
- [x] AELIS screen and Start work; "Bookmark missing" is disabled without JEI. *2026-11-17.*
- [ ] JEI present, ExtendedAE/ae2wtlib absent (`make-minimal-instance.ps1 -WithJei`). *Skipped for now.*

## 8. Dedicated server

Setup (2026-11-17): the official FTB Evolution 2 1.9.0 server (FTB pack 135, release 100614) installed with
FTB's server installer in a container, plus our jar; Just Dire Things matched to the CurseForge client (1.6.10
instead of FTB's 1.6.11). Scripts in `~/dev/ae-port/tools/` (outside the repo): `ftb-install.sh`,
`ftb-server-prep.sh`, `ftb-server-start.sh`, `ftb-server-cmd.sh "<command>"`, `ftb-server-restart.sh`,
`ftb-server-logcheck.sh`. The client is the TEST instance (Direct Connection `localhost`).

**On a server, the AELIS planner switch lives in the SERVER's `config/appliedenhancements-common.toml`.** It is a
common config: each side has its own copy and it is not synced, so Configured on the client only shows "local".
The server did not log a reload after editing the file; restart it.

- [x] A client connected to a dedicated server can use the craft confirm screen and batch pattern moves
      without a `NoClassDefFoundError` on the server. *Protects: use of the client-package class
      `ClientPacketDistributor` in common code.* *2026-11-17: quick move (select, Cut, Paste, toasts), AELIS
      confirm screen, Start and Shift + Force start all work. Server log: 0 class-loading errors after the join
      (all 19 are startup Mixin target scans of other mods' optional targets, plus Minecraft's harmless netty
      kqueue probe), 0 ERROR lines naming us/AE2/AELIS, no crash report.*

## Later

Tasks added on 2026-11-17, not done yet.

- [ ] **AELIS progress in the confirm-screen title.** With the AELIS planner on, request a craft big enough that
      the calculation takes a moment, and watch AE2's own confirm screen title while it calculates: it should show
      the AELIS progress line, then the normal title once the plan is ready. Repeat with Recursive AE2 Pattern
      Provider in the pack: both mods change that title (`docs/ADDON_COMPATIBILITY.md`), so check which text wins
      and whether either is lost. *Protects: `CraftConfirmScreenMixin` `@ModifyArg` on `setTextContent`.*
- [x] **Recursive / catalyst crafts (Mystical Agriculture essences).** *2026-11-18, real world (FTB Evolution 2
      1.9.0, AELIS planner on): AELIS planned 100 Prudentium from 400 Inferium + 1 Master Infusion Crystal and the
      craft ran through; no hand-back in debug.log. Already supported: an input that returns unchanged is
      `INVARIANT_REUSABLE` (`MolecularReusableInputAdapters`) and is borrowed once for all repetitions. Not
      supported (seen the same day): a DIFFERENT returned item that feeds back into the same chain (glass bottles
      from honey blocks: `fixed_remainder_input_feedback`), and duplicate producers of one item where one pattern
      has an unsupported shape (AELIS then hands the whole request to AE2).* In the pack (Mystical Agriculture 9.0.11),
      an essence of the next tier is crafted from 4 essences of the tier below plus 1 Master Infusion Crystal,
      which is not used up (it comes back as a crafting remainder). Encode such a pattern (e.g. 4 Inferium →
      1 Prudentium + crystal), put exactly 1 crystal in the network, and request e.g. 100 Prudentium:
      * Does the plan (AE2 screen and AELIS screen) ask for 400 Inferium and only **1** crystal, or for 100?
      * Does the craft run through with one crystal (molecular assembler and a pattern provider into a crafting
        machine), and is the crystal back in the network at the end?
      * Note what the existing code already does: AELIS "fixed remainder" handling (`AelisFixedRemainder`) and
        `MolecularReusableInputAdapters`. The result decides how much of Phase 7 (a "reused ingredient" marker in
        patterns) is still needed.
## D3 chance outputs (step a) - needs crafting.enable_chance_output_bonus = true
- [ ] Processing mode of the pattern encoding terminal shows a green/grey "%" toggle left of the clear button; tooltip texts OK; hidden in the other modes.
- [ ] Encode with the toggle on: reloading that pattern into the terminal shows it on; an unmarked pattern shows it off; marked and unmarked patterns do not stack.
- [ ] AE2 CPU: marked pattern with chance extras - extras count toward the requested total, job ends early; unmarked pattern unchanged.
- [ ] AdvancedAE quantum CPU: same (reflection path; look for "Chance-output bonus disabled" in the log).

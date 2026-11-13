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

- [ ] The game reaches the title screen with the mod installed (first load of the 26 client Mixins and of
      the Mixins for AdvancedAE, ae2wtlib and ExtendedAE).
- [ ] A creative world loads.
- [ ] `check-test-log.ps1` reports no Mixin failures, no errors naming `appliedenhancements`, no crash report.

## 1. Controls

- [ ] Options → Controls shows a category named "Applied Enhancements" with both bindings
      (right-click and Alt+right-click). *Protects: KeyMapping category change, new language key
      `key.category.appliedenhancements.category`.*
- [ ] Rebinding one of them, restarting the game, and the binding is still set.

## 2. ME terminal: network item context menu

- [ ] The context menu opens on an item in the terminal and is drawn above everything else.
      *Protects: GUI layering (`nextStratum`), quick-move/context-menu Mixin on container screens (W1).*
- [ ] Typing a number, Enter and Escape work in the menu's amount field. *Protects: input event adapters.*
- [ ] Feedback messages after a menu action appear in the ACTION BAR (above the hotbar).
- [ ] The "force amount too large" message appears in CHAT. *Protects: overlay vs. system message split.*
- [ ] Scrolling the terminal still works while the menu is closed and while it is open.

## 3. JEI integration

- [ ] The JEI item context menu works; its feedback appears in the action bar.
- [ ] "Bookmark added" appears in the action bar; "bookmark unsupported" appears in chat.
- [ ] Cheat-give from the menu follows JEI's own give mode. *Protects: JEI internal config accessor
      (`Internal.getClientConfigs()`), which can break on any JEI update.*

## 4. Pattern access terminal

- [ ] The four pattern buttons are visible and not hidden behind other GUI parts: the header action
      button and the Duplicate / Invalid / Quick-Pattern-Move toggles. *Protects: removed `zOffset`.*
- [ ] The three toggle buttons react to clicks, and their labels are readable.
- [ ] The duplicate badge (machine icon or letter) is drawn above the pattern item.
- [ ] The expanded terminal header looks seamless (no visible seam or shifted strip).
      *Protects: new `blit` parameter order.*
- [ ] Quick move: click, drag a selection box, release. Patterns move as expected. *Protects: W1
      (mouse events, `extractRenderState`).*
- [ ] The quick-move context menu opens above everything else.
- [ ] Duplicate-pattern messages appear in the action bar.
- [ ] The same checks in ExtendedAE's extended pattern terminal and in a wireless pattern access terminal
      (ExtendedAE / ae2wtlib). *Protects: the optional-mod Mixins, which are never applied in dev runs.*

## 5. Crafting: amount screen and AELIS confirm screen

- [ ] The craft amount screen opens and accepts an amount.
- [ ] The AELIS confirm screen opens; the AELIS mark is drawn at 24×24 and complete.
- [ ] Shift + start (force start) works.
- [ ] The crafting graph can be dragged, zoomed and scrolled, and stays where it is put.
- [ ] Toggling fullscreen resizes the screen without glitches. *Protects: access transformer for
      `imageWidth` / `imageHeight`.*
- [ ] A wireless crafting terminal (ae2wtlib) opens and can start a craft.

## 6. Crafting CPUs: save and reload

- [ ] AE2 crafting CPU: start a long job, save and quit while it runs, reload. The job is still there and
      finishes. *Protects: W2 (CPU save hooks on `ValueInput` / `ValueOutput`).*
- [ ] The same with a job that uses an AELIS cycle (cyclic or seed-protected recipe).
- [ ] AdvancedAE quantum computer: the same two checks. *Protects: W3 and W4.*
- [ ] After reloading, `latest.log` has no "Cannot save AELIS …" or "Invalid AELIS …" error lines.

## 7. Storage bus and export bus

Config switches involved: `ENABLE_STORAGE_BUS_SLOT_INDEX` and `ENABLE_IO_BUS_OPTIMIZATION`. Do the checks
with both on (default state to be confirmed in the config file), then once with both off.

- [ ] Storage bus on a chest: items in the chest show in the terminal; inserting through the terminal
      puts them into the chest; extracting takes them out. Counts stay correct, nothing is duplicated or lost.
      *Protects: W5 (slot index on the transfer API).*
- [ ] Take items out of the chest by hand, then extract the same item through the terminal: the amount
      delivered matches what is really there.
- [ ] The same on a large multi-slot inventory.
- [ ] Fluid storage bus on a tank still works (the storage-bus Mixin now also sits on the fluid facade).
- [ ] Export bus into a chest and into a multi-slot machine: items arrive, the count is right.
      *Protects: W6 (slot hints on the transfer API).*
- [ ] ME interface pushing items into an adjacent inventory.

## 8. Dedicated server (later)

- [ ] A client connected to a dedicated server can use the craft confirm screen and batch pattern moves
      without a `NoClassDefFoundError` on the server. *Protects: use of the client-package class
      `ClientPacketDistributor` in common code.*

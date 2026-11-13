package com.appliedenhancements.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.appliedenhancements.Config;
import com.appliedenhancements.storage.StorageBusSlotIndex;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Reuses AE2's periodic external-inventory listing as positive slot hints.
 * Every candidate is revalidated, and incomplete lookups fall back to AE2's scan.
 *
 * <p>AE2 26 (NeoForge transfer API): the handler field and the methods hooked here moved from
 * {@code ItemHandlerFacade} into the generic superclass {@code ResourceHandlerFacade}, which also backs the
 * fluid facade. Injections need the method to be declared in the target class, so this Mixin targets the
 * superclass and only acts on the item facade (item keys / {@link ItemResource}); fluids pass through.
 *
 * <p>Gone with AE2 19: {@code extractFromHandler} (per-slot extraction with a retry loop) and the hook that
 * re-checked the slot before every retry. The indexed {@code ResourceHandler.extract(slot, resource, ...)} is
 * told which resource it may take, so it cannot extract a different item; no retry loop is left to guard.
 */
@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ResourceHandlerFacade", remap = false)
public abstract class ExternalStorageFacadeItemHandlerMixin {
    @Shadow
    @Final
    protected ResourceHandler<?> handler;

    /** True for the item facade; this Mixin also sits on the fluid facade (shared superclass). */
    @Unique
    private boolean appliedenhancements$isItemFacade() {
        return ((ExternalStorageFacade) (Object) this).getKeyType() == AEKeyType.items();
    }

    /**
     * Extracts from one slot, replacing AE2 19's {@code extractFromHandler}. Like AE2 26's own
     * {@code extractExternal}, it uses a root transaction and commits only when not simulating.
     */
    @Unique
    @SuppressWarnings("unchecked")
    private int appliedenhancements$extractFromSlot(
            int slot, AEItemKey itemKey, int maxExtract, Actionable mode) {
        if (maxExtract <= 0) {
            return 0;
        }
        // Callers only get here for item keys, i.e. on the item facade.
        var items = (ResourceHandler<ItemResource>) handler;
        try (var transaction = Transaction.openRoot()) {
            int extracted = items.extract(slot, itemKey.toResource(), maxExtract, transaction);
            if (!mode.isSimulate()) {
                transaction.commit();
            }
            return extracted;
        }
    }

    @Unique
    private StorageBusSlotIndex<AEItemKey> appliedenhancements$slotIndex;

    @Unique
    private int appliedenhancements$lastObservedSlot = -1;

    @Unique
    private AEItemKey appliedenhancements$lastObservedKey;

    @Inject(method = "extractExternal", at = @At("RETURN"))
    private void appliedenhancements$invalidateIndexAfterExtraction(
            AEKey what, int amount, Actionable mode,
            CallbackInfoReturnable<Integer> callback) {
        if (mode == Actionable.MODULATE && callback.getReturnValue() > 0
                && appliedenhancements$slotIndex != null) {
            appliedenhancements$slotIndex.invalidate();
        }
    }

    @Inject(method = "insertExternal", at = @At("RETURN"))
    private void appliedenhancements$invalidateIndexAfterInsertion(
            AEKey what, int amount, Actionable mode,
            CallbackInfoReturnable<Integer> callback) {
        if (mode == Actionable.MODULATE && callback.getReturnValue() > 0
                && appliedenhancements$slotIndex != null) {
            appliedenhancements$slotIndex.invalidate();
        }
    }

    @Inject(method = "getStackInSlot", at = @At("RETURN"))
    private void appliedenhancements$rememberObservedSlot(
            int slot,
            CallbackInfoReturnable<GenericStack> cir) {
        var stack = cir.getReturnValue();
        if (Config.ENABLE_IO_BUS_OPTIMIZATION.get()
                && stack != null
                && stack.what() instanceof AEItemKey itemKey) {
            appliedenhancements$lastObservedSlot = slot;
            appliedenhancements$lastObservedKey = itemKey;
        } else {
            appliedenhancements$clearObservedSlot();
        }
    }

    @Inject(method = "getAvailableStacks", at = @At("HEAD"))
    private void appliedenhancements$beginSlotIndexRebuild(KeyCounter output, CallbackInfo ci) {
        if (Config.ENABLE_STORAGE_BUS_SLOT_INDEX.get() && appliedenhancements$isItemFacade()) {
            appliedenhancements$getSlotIndex().beginRebuild();
        } else if (appliedenhancements$slotIndex != null) {
            appliedenhancements$slotIndex.abortRebuild();
        }
    }

    // AE2 26 reads each slot with handler.getResource(i) (was IItemHandler.getStackInSlot(i)).
    // The descriptor uses the erased return type Resource.
    @WrapOperation(method = "getAvailableStacks", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/transfer/ResourceHandler;getResource(I)Lnet/neoforged/neoforge/transfer/resource/Resource;"))
    private Resource appliedenhancements$recordListedSlot(
            ResourceHandler<?> inventory,
            int slot,
            Operation<Resource> original) {
        var resource = original.call(inventory, slot);
        if (appliedenhancements$slotIndex != null
                && resource instanceof ItemResource item && !item.isEmpty()) {
            appliedenhancements$slotIndex.record(AEItemKey.of(item), slot);
        }
        return resource;
    }

    @Inject(method = "getAvailableStacks", at = @At("RETURN"))
    private void appliedenhancements$publishSlotIndex(KeyCounter output, CallbackInfo ci) {
        if (appliedenhancements$slotIndex != null) {
            appliedenhancements$slotIndex.commitRebuild();
        }
    }

    @WrapMethod(method = "extractExternal")
    private int appliedenhancements$extractFromIndexedSlots(
            AEKey what,
            int amount,
            Actionable mode,
            Operation<Integer> original) {
        if (amount <= 0
                || !(what instanceof AEItemKey itemKey)
                || !appliedenhancements$hasFastPath(itemKey)) {
            return original.call(what, amount, mode);
        }

        int observedSlot = -1;
        int indexedExtracted = 0;
        var candidates = Config.ENABLE_STORAGE_BUS_SLOT_INDEX.get()
                && appliedenhancements$slotIndex != null
                ? appliedenhancements$slotIndex.candidates(itemKey) : null;
        if (Config.ENABLE_IO_BUS_OPTIMIZATION.get()
                && itemKey.equals(appliedenhancements$lastObservedKey)) {
            observedSlot = appliedenhancements$lastObservedSlot;
            appliedenhancements$clearObservedSlot();
            if (observedSlot >= 0 && observedSlot < handler.size()) {
                indexedExtracted = appliedenhancements$extractFromSlot(
                        observedSlot, itemKey, amount, mode);
            }
        }

        if (indexedExtracted < amount
                && candidates != null) {
            indexedExtracted += appliedenhancements$extractCandidates(
                    candidates,
                    itemKey,
                    amount - indexedExtracted,
                    mode,
                    observedSlot);
        }

        if (indexedExtracted >= amount) {
            return amount;
        }

        // Simulations do not modify candidate slots, so a partial hint cannot be
        // added to a full scan without double-counting. The full result also covers
        // changes made by another mod since the last inventory listing.
        if (mode == Actionable.SIMULATE) {
            return original.call(what, amount, mode);
        }

        return indexedExtracted + original.call(what, amount - indexedExtracted, mode);
    }

    @Unique
    private StorageBusSlotIndex<AEItemKey> appliedenhancements$getSlotIndex() {
        if (appliedenhancements$slotIndex == null) {
            appliedenhancements$slotIndex = new StorageBusSlotIndex<>();
        }
        return appliedenhancements$slotIndex;
    }

    @Unique
    private boolean appliedenhancements$hasFastPath(AEItemKey itemKey) {
        boolean observedSlotAvailable = Config.ENABLE_IO_BUS_OPTIMIZATION.get()
                && appliedenhancements$lastObservedSlot >= 0
                && itemKey.equals(appliedenhancements$lastObservedKey);
        boolean indexedSlotsAvailable = Config.ENABLE_STORAGE_BUS_SLOT_INDEX.get()
                && appliedenhancements$slotIndex != null
                && !appliedenhancements$slotIndex.candidates(itemKey).isEmpty();
        return observedSlotAvailable || indexedSlotsAvailable;
    }

    @Unique
    private void appliedenhancements$clearObservedSlot() {
        appliedenhancements$lastObservedSlot = -1;
        appliedenhancements$lastObservedKey = null;
    }

    @Unique
    private int appliedenhancements$extractCandidates(
            StorageBusSlotIndex.CandidateSlots candidates,
            AEItemKey itemKey,
            int amount,
            Actionable mode,
            int slotToSkip) {
        int extracted = 0;
        int slotCount = handler.size();

        for (int i = 0; i < candidates.size() && extracted < amount; i++) {
            int slot = candidates.get(i);
            if (slot < 0 || slot >= slotCount || slot == slotToSkip) {
                continue;
            }

            extracted += appliedenhancements$extractFromSlot(
                    slot, itemKey, amount - extracted, mode);
        }

        return extracted;
    }
}

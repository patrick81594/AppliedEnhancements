package com.appliedenhancements.mixin;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.appliedenhancements.Config;
import com.appliedenhancements.storage.WeakSlotHintCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.stacks.AEItemKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Reuses accepting item-handler slots between export-bus simulation and commit. */
@Mixin(targets = "appeng.parts.automation.HandlerStrategy$1", remap = false)
public abstract class ItemHandlerStrategyMixin {
    @Unique
    private WeakSlotHintCache<ResourceHandler<ItemResource>, AEItemKey> appliedenhancements$slotHints;

    // MC 26 / AE2 26: items move through NeoForge's transfer API. AE2 calls the "any slot"
    // handler.insert(resource, amount, tx), whose default implementation scans every slot in order.
    // We replace that scan with indexed inserts: hinted slots first, then the rest. All inserts run
    // inside AE2's own transaction, which AE2 commits or (when simulating) rolls back.
    // The full method descriptor is needed: the anonymous class also has a bridge insert(Object, ...).
    @WrapOperation(method = "insert(Lnet/neoforged/neoforge/transfer/ResourceHandler;Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/transfer/ResourceHandler;insert(Lnet/neoforged/neoforge/transfer/resource/Resource;ILnet/neoforged/neoforge/transfer/transaction/TransactionContext;)I"))
    private int appliedenhancements$insertUsingSlotHint(
            ResourceHandler<ItemResource> handler,
            Resource resource,
            int amount,
            TransactionContext transaction,
            Operation<Integer> original) {
        if (!Config.ENABLE_IO_BUS_OPTIMIZATION.get() || handler == null || amount <= 0
                || !(resource instanceof ItemResource item) || item.isEmpty()) {
            return original.call(handler, resource, amount, transaction);
        }

        var itemKey = AEItemKey.of(item);
        if (itemKey == null) {
            return original.call(handler, resource, amount, transaction);
        }

        int slotCount = handler.size();
        var cache = appliedenhancements$getSlotHints();
        List<Integer> hints = cache.findAll(handler, itemKey);
        var visited = new BitSet(slotCount);
        var acceptingSlots = new ArrayList<Integer>(hints.size());
        int remaining = amount;
        for (int hintedSlot : hints) {
            if (hintedSlot < 0 || hintedSlot >= slotCount || visited.get(hintedSlot)) {
                continue;
            }
            visited.set(hintedSlot);
            int accepted = handler.insert(hintedSlot, item, remaining, transaction);
            if (accepted > 0) {
                acceptingSlots.add(hintedSlot);
                remaining -= accepted;
            }
            if (remaining <= 0) {
                cache.rememberAll(handler, itemKey, acceptingSlots);
                return amount;
            }
        }

        for (int slot = 0; slot < slotCount; slot++) {
            if (visited.get(slot)) {
                continue;
            }
            visited.set(slot);

            int accepted = handler.insert(slot, item, remaining, transaction);
            if (accepted > 0) {
                acceptingSlots.add(slot);
                remaining -= accepted;
            }
            if (remaining <= 0) {
                cache.rememberAll(handler, itemKey, acceptingSlots);
                return amount;
            }
        }

        cache.rememberAll(handler, itemKey, acceptingSlots);
        return amount - remaining;
    }

    @Unique
    private WeakSlotHintCache<ResourceHandler<ItemResource>, AEItemKey> appliedenhancements$getSlotHints() {
        if (appliedenhancements$slotHints == null) {
            appliedenhancements$slotHints = new WeakSlotHintCache<>(128);
        }
        return appliedenhancements$slotHints;
    }
}

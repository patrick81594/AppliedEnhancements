package com.appliedenhancements.chance.mixin;

import appeng.api.storage.ITerminalHost;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.appliedenhancements.chance.ChanceOutputMarker;
import com.appliedenhancements.chance.ChanceOutputMenu;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D3: the "chance outputs" toggle of the processing encoding panel.
 *
 * Same shape as AE2's own "substitutions" toggle: a @GuiSync field the client reads, plus a client
 * action to change it. The encoded processing pattern gets our marker component when the toggle is
 * on, and loading a pattern into the encoded slot sets the toggle from that pattern's marker.
 */
@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuChanceMixin extends MEStorageMenu implements ChanceOutputMenu {
    private PatternEncodingTermMenuChanceMixin(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {
        super(menuType, id, ip, host);
    }

    @Unique
    private static final ClientActionKey<Boolean> APPLIEDENHANCEMENTS$ACTION_SET_CHANCE =
            new ClientActionKey<>("appliedenhancementsSetChanceOutput");

    // 210: AE2 menus use 0-12, 20-33, 100 and 200-203.
    @GuiSync(210)
    @Unique
    public boolean appliedenhancements$chanceOutput = false;

    @Shadow
    private RestrictedInputSlot encodedPatternSlot;

    /** Only the full constructor: the short one delegates to it, so hooking both would register twice. */
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("TAIL"))
    private void appliedenhancements$registerChanceAction(CallbackInfo ci) {
        registerClientAction(APPLIEDENHANCEMENTS$ACTION_SET_CHANCE, ByteBufCodecs.BOOL,
                on -> this.appliedenhancements$chanceOutput = on);
    }

    @Override
    public boolean appliedenhancements$isChanceOutput() {
        return appliedenhancements$chanceOutput;
    }

    @Override
    public void appliedenhancements$setChanceOutput(boolean on) {
        if (isClientSide()) {
            sendClientAction(APPLIEDENHANCEMENTS$ACTION_SET_CHANCE, on);
        }
        appliedenhancements$chanceOutput = on;
    }

    /** Marks the freshly encoded processing pattern (server side; encode runs only there). */
    @ModifyReturnValue(method = "encodeProcessingPattern", at = @At("RETURN"))
    private ItemStack appliedenhancements$markChanceOutput(ItemStack encoded) {
        if (encoded != null && appliedenhancements$chanceOutput) {
            ChanceOutputMarker.withMarker(encoded, true);
        }
        return encoded;
    }

    /**
     * A pattern placed in the encoded slot sets the toggle from its marker. Emptying the slot keeps the
     * toggle (otherwise taking out a freshly encoded pattern would reset it, found in game).
     */
    @Inject(method = "onSlotChange", at = @At("TAIL"))
    private void appliedenhancements$loadChanceOutput(Slot slot, CallbackInfo ci) {
        if (slot == (Object) encodedPatternSlot && isServerSide() && !slot.getItem().isEmpty()) {
            appliedenhancements$chanceOutput = ChanceOutputMarker.isMarked(slot.getItem());
        }
    }
}

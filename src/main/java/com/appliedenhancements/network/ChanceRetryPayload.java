package com.appliedenhancements.network;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.appliedenhancements.mixin.CraftingStatusMenuAccessor;
import com.appliedenhancements.AppliedEnhancements;
import com.appliedenhancements.mixin.CraftingCPUMenuAccessor;
import com.appliedenhancements.runtime.ChanceTopUpControl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the player middle-clicked an item in the crafting-status screen to try its chance top-up
 * again as if the retry limit had not been reached. Validated against the player's OPEN crafting-status menu;
 * only a running job's own marked chance output is reset.
 */
public record ChanceRetryPayload(AEKey key) implements CustomPacketPayload {
    public static final Type<ChanceRetryPayload> TYPE = new Type<>(AppliedEnhancements.id("chance_retry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChanceRetryPayload> STREAM_CODEC =
            StreamCodec.composite(AEKey.STREAM_CODEC, ChanceRetryPayload::key, ChanceRetryPayload::new);

    public static void handle(ChanceRetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftingCPUMenu menu)
                    || !menu.stillValid(player)) {
                AppliedEnhancements.LOGGER.info("Chance retry request for {} ignored: no valid crafting status menu open ({})",
                        payload.key(), context.player() instanceof ServerPlayer p ? p.containerMenu.getClass().getName() : "no player");
                return;
            }
            Object cpu = selectedCpu(menu);
            if (!(logicOf(cpu) instanceof ChanceTopUpControl control)) {
                AppliedEnhancements.LOGGER.info("Chance retry request for {} ignored: selected CPU {}@{} (serial {}) has no retry support (logic {})",
                        payload.key(), cpu == null ? "none" : cpu.getClass().getName(), System.identityHashCode(cpu),
                        menu instanceof CraftingStatusMenu s ? s.getSelectedCpuSerial() : "n/a", logicOf(cpu));
                return;
            }
            var result = control.appliedenhancements$requestChanceRetry(payload.key());
            AppliedEnhancements.LOGGER.info("Chance retry request for {}: {} (selected serial {}, cpu {}@{}, menu {})",
                    payload.key(), result, menu instanceof CraftingStatusMenu s ? s.getSelectedCpuSerial() : "n/a",
                    cpu.getClass().getName(), System.identityHashCode(cpu), menu.getClass().getSimpleName());
            if ((result == ChanceTopUpControl.Result.NO_JOB || result == ChanceTopUpControl.Result.NOT_MARKED)
                    && menu instanceof CraftingStatusMenu status) {
                // The selected entry is not the CPU that runs the job (a Quantum computer keeps one CPU object per
                // job plus a remaining-capacity one): try the other CPUs this menu has seen on the same grid.
                for (var other : new java.util.ArrayList<>(
                        ((CraftingStatusMenuAccessor) status).appliedenhancements$getCpuSerialMap().keySet())) {
                    if (other == cpu || !(logicOf(other) instanceof ChanceTopUpControl otherControl)) {
                        continue;
                    }
                    var otherResult = otherControl.appliedenhancements$requestChanceRetry(payload.key());
                    AppliedEnhancements.LOGGER.info("Chance retry request for {}: fallback cpu {}@{} -> {}",
                            payload.key(), other.getClass().getName(), System.identityHashCode(other), otherResult);
                    if (otherResult == ChanceTopUpControl.Result.STARTED
                            || otherResult == ChanceTopUpControl.Result.ALREADY_RUNNING) {
                        result = otherResult;
                        break;
                    }
                }
            }
            String message = switch (result) {
                case STARTED -> "message.appliedenhancements.chance_retry.started";
                case NO_JOB -> "message.appliedenhancements.chance_retry.no_job";
                case ALREADY_RUNNING -> "message.appliedenhancements.chance_retry.already_running";
                case NOT_WAITING -> "message.appliedenhancements.chance_retry.not_waiting";
                case NOT_MARKED -> "message.appliedenhancements.chance_retry.not_marked";
            };
            FeedbackToastPayload.send(player, FeedbackToastPayload.Kind.CRAFTING_PLAN, message, 0);
        });
    }

    /**
     * The CPU the menu shows: AE2's cluster CPUs are kept in the menu's {@code cpu} field; other CPU types (the
     * Quantum CPU) are only known by the status menu's serial number for the selected entry.
     */
    private static Object selectedCpu(CraftingCPUMenu menu) {
        Object cpu = ((CraftingCPUMenuAccessor) menu).appliedenhancements$getCpu();
        if (cpu == null && menu instanceof CraftingStatusMenu status) {
            int serial = status.getSelectedCpuSerial();
            for (var entry : ((CraftingStatusMenuAccessor) status).appliedenhancements$getCpuSerialMap().entrySet()) {
                if (entry.getValue() == serial) {
                    return entry.getKey();
                }
            }
        }
        return cpu;
    }

    /** The crafting logic of an AE2 cluster CPU or of AdvancedAE's CPU (public {@code craftingLogic} field). */
    private static Object logicOf(Object cpu) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            return cluster.craftingLogic;
        }
        if (cpu == null) {
            return null;
        }
        try {
            return cpu.getClass().getField("craftingLogic").get(cpu);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

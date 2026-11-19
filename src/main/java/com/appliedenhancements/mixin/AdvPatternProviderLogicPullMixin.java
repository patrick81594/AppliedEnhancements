package com.appliedenhancements.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.parts.AEBasePart;
import com.appliedenhancements.provider.OutputPullState;
import com.appliedenhancements.provider.ProviderOutputPullHost;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feature G on AdvancedAE's pattern provider logic (both the Advanced and the Small Advanced Pattern Provider share
 * it). AdvancedAE's logic is NOT a subclass of AE2's, so it has its own hooks; the state and rules are the same as
 * on AE2's provider ({@link OutputPullState}).
 *
 * <p>Two push paths write {@code sendDirection}: the normal external-inventory path inside {@code pushPattern}, and
 * the directional path inside the private {@code pushInputsDirectionally}. Both record the same owed outputs. The
 * directional path does not receive the pattern, so the details are captured at the start of {@code pushPattern}.
 *
 * <p>Pseudo target: AdvancedAE is optional, so this Mixin is skipped when it is absent.
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvPatternProviderLogicPullMixin implements ProviderOutputPullHost, OutputPullState.Host {
    private static final String ADV_LOGIC = "Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogic;";

    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private Set<AEKey> patternInputs;
    @Shadow private Direction sendDirection;
    @Shadow @Final private PatternProviderReturnInventory returnInv;
    @Shadow @Final private IActionSource actionSource;

    /** Private in AdvancedAE: the body is never run, Mixin replaces the call with the target's method. */
    @Shadow
    private Set<Direction> getActiveSides() {
        throw new AssertionError("replaced by Mixin");
    }

    @Shadow
    public abstract void saveChanges();

    /** Created on first use, like the AE2 Mixin: the Host methods read the shadowed fields above. */
    @Unique private OutputPullState appliedenhancements$pullState;
    /** The pattern of the push that is running (set for the whole {@code pushPattern} call). */
    @Unique private IPatternDetails appliedenhancements$pushingDetails;

    @Unique
    private OutputPullState appliedenhancements$state() {
        if (appliedenhancements$pullState == null) {
            appliedenhancements$pullState = new OutputPullState(this);
        }
        return appliedenhancements$pullState;
    }

    // ---------------------------------------------------------------- recording owed outputs on push

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void appliedenhancements$beginPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        appliedenhancements$pushingDetails = details;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void appliedenhancements$endPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        appliedenhancements$pushingDetails = null;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean appliedenhancements$recordMachinePush(ICraftingMachine machine, IPatternDetails details,
            KeyCounter[] inputs, Direction machineSide, Operation<Boolean> original) {
        boolean pushed = original.call(machine, details, inputs, machineSide);
        if (pushed) {
            appliedenhancements$state().recordOwed(machineSide.getOpposite(), details);
        }
        return pushed;
    }

    /** Normal path: the outputs go to the provider face's inventory. */
    @Inject(method = "pushPattern", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = ADV_LOGIC + "sendDirection:Lnet/minecraft/core/Direction;", shift = At.Shift.AFTER))
    private void appliedenhancements$recordInventoryPush(CallbackInfoReturnable<Boolean> callback) {
        recordCurrentPush();
    }

    /** Directional path: the inputs go to the machine's sides; the pattern is the one captured above. */
    @Inject(method = "pushInputsDirectionally", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = ADV_LOGIC + "sendDirection:Lnet/minecraft/core/Direction;", shift = At.Shift.AFTER))
    private void appliedenhancements$recordDirectionalPush(CallbackInfoReturnable<Boolean> callback) {
        recordCurrentPush();
    }

    @Unique
    private void recordCurrentPush() {
        if (sendDirection != null && appliedenhancements$pushingDetails != null) {
            appliedenhancements$state().recordOwed(sendDirection, appliedenhancements$pushingDetails);
        }
    }

    // ---------------------------------------------------------------- ticking: only while something is owed

    @ModifyReturnValue(method = "hasWorkToDo", at = @At("RETURN"))
    private boolean appliedenhancements$workWhileOwed(boolean original) {
        return original || appliedenhancements$state().hasOwed();
    }

    @ModifyReturnValue(method = "doWork", at = @At("RETURN"))
    private boolean appliedenhancements$pullOwedOutputs(boolean original) {
        // Bitwise OR on purpose: always try to pull.
        return original | appliedenhancements$state().pull();
    }

    // ---------------------------------------------------------------- count-down when outputs arrive otherwise

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void appliedenhancements$countDownReturned(GenericStack stack, CallbackInfo callback) {
        appliedenhancements$state().countDownReturned(stack);
    }

    // ---------------------------------------------------------------- save / load

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void appliedenhancements$writePull(ValueOutput output, CallbackInfo callback) {
        appliedenhancements$state().write(output);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void appliedenhancements$readPull(ValueInput input, CallbackInfo callback) {
        appliedenhancements$state().read(input);
    }

    // ---------------------------------------------------------------- OutputPullState.Host

    @Override
    public BlockEntity appliedenhancements$pullBlockEntity() {
        // AdvancedAE's logic has no host field of its own type we could type here; the grid node's owner is the
        // block entity (or the part, whose block entity we ask).
        IGridNode node = mainNode.getNode();
        Object owner = node == null ? null : node.getOwner();
        if (owner instanceof BlockEntity be) {
            return be;
        }
        if (owner instanceof AEBasePart part) {
            return part.getBlockEntity();
        }
        return null;
    }

    @Override
    public boolean appliedenhancements$isPullInput(AEKey key) {
        return patternInputs.contains(key.dropSecondary());
    }

    @Override
    public void appliedenhancements$alertPull() {
        mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Override
    public long appliedenhancements$insertPullReturn(AEKey key, long amount, Actionable mode) {
        return returnInv.insert(key, amount, mode, actionSource);
    }

    @Override
    public IActionSource appliedenhancements$pullActionSource() {
        return actionSource;
    }

    @Override
    public Set<Direction> appliedenhancements$pullSides() {
        return getActiveSides();
    }

    @Override
    public void appliedenhancements$pullSave() {
        saveChanges();
    }

    // ---------------------------------------------------------------- ProviderOutputPullHost

    @Override
    public byte appliedenhancements$getPullMode(Direction face) {
        return appliedenhancements$state().mode(face);
    }

    @Override
    public void appliedenhancements$setPullMode(Direction face, byte mode) {
        appliedenhancements$state().setMode(face, mode);
    }

    @Override
    public KeyCounter appliedenhancements$getOwedOutputs(Direction face) {
        return appliedenhancements$state().owedCopy(face);
    }

    @Override
    public Set<Direction> appliedenhancements$getPushFaces() {
        return getActiveSides();
    }

    @Override
    public BlockEntity appliedenhancements$getPullBlockEntity() {
        return appliedenhancements$pullBlockEntity();
    }
}

package com.appliedenhancements.outputpull.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import com.appliedenhancements.outputpull.OutputPullState;
import com.appliedenhancements.outputpull.ProviderOutputPullHost;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feature G on AE2's pattern provider logic (also used by ExtendedAE's extended pattern provider). This Mixin only
 * records the pushes and forwards the ticking, saving and network hooks; the state and rules live in
 * {@link OutputPullState}, which AdvancedAE's providers share (see {@code AdvPatternProviderLogicPullMixin}).
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicPullMixin implements ProviderOutputPullHost, OutputPullState.Host {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private Set<AEKey> patternInputs;
    @Shadow private Direction sendDirection;
    @Shadow @Final private PatternProviderReturnInventory returnInv;
    @Shadow @Final private IActionSource actionSource;

    @Shadow
    protected abstract Set<Direction> getActiveSides();

    /** Created on first use: the Host methods read the shadowed fields above, which are not set yet during construction. */
    @Unique private OutputPullState appliedenhancements$pullState;

    @Unique
    private OutputPullState appliedenhancements$state() {
        if (appliedenhancements$pullState == null) {
            appliedenhancements$pullState = new OutputPullState(this);
        }
        return appliedenhancements$pullState;
    }

    // ---------------------------------------------------------------- recording owed outputs on push

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

    @Inject(method = "pushPattern", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;sendDirection:Lnet/minecraft/core/Direction;",
            shift = At.Shift.AFTER))
    private void appliedenhancements$recordInventoryPush(IPatternDetails details, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        if (sendDirection != null) {
            appliedenhancements$state().recordOwed(sendDirection, details);
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

    /** Every stack the return inventory hands to the network passes here (our pulls and machine push-backs). */
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
        return host.getBlockEntity();
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
        host.saveChanges();
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
        return host.getBlockEntity();
    }
}

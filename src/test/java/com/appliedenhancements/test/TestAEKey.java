package com.appliedenhancements.test;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

public final class TestAEKey extends AEKey {
    private final Identifier id;
    private final AEKeyType type;

    public TestAEKey(String path) {
        this(path, null);
    }

    public TestAEKey(String path, AEKeyType type) {
        this.id = Identifier.fromNamespaceAndPath("test", path);
        this.type = type;
    }

    @Override
    public AEKeyType getType() {
        return type;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public void toTag(ValueOutput output) {
        // AE2 26: toTag writes into a ValueOutput. Test key: nothing to write (as before).
    }

    @Override
    public Object getPrimaryKey() {
        return id;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    protected Component computeDisplayName() {
        return Component.literal(id.toString());
    }

    @Override
    public void addDrops(
            long amount, List<ItemStack> drops, Level level, BlockPos pos) {
    }

    @Override
    public boolean hasComponents() {
        return false;
    }
}

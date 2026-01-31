package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;


public final class BCTransitPassFactory {

    private BCTransitPassFactory() {}

    @Nullable
    public static BCTransitPass fromHeldItem(Player player) {
        ItemStack item = HumanHand.getItemInMainHand(player);

        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        CommonTagCompound nbt = CommonItemStack.of(item).getCustomData();
        PassType type = PassType.fromId(nbt.getValue(BCTransitPass.KEY_TRANSIT_PASS_TYPE, "")).orElse(PassType.NONE);

        return switch (type) {
            case TICKET, NONE -> BCTicket.fromHeldItem(player);
            case CARD   -> BCCard.fromHeldItem(player);
        };
    }

    @Nullable
    public static BCTransitPass fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        CommonTagCompound nbt = CommonItemStack.of(item).getCustomData();
        PassType type = PassType.fromId(nbt.getValue(BCTransitPass.KEY_TRANSIT_PASS_TYPE, "")).orElse(PassType.NONE);

        return switch (type) {
            case TICKET, NONE -> BCTicket.fromItemStack(item);
            case CARD   -> BCCard.fromItemStack(item);
        };
    }
}

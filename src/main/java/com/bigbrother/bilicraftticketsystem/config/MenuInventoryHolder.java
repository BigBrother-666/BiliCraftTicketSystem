package com.bigbrother.bilicraftticketsystem.config;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class MenuInventoryHolder implements InventoryHolder {
    private final Inventory inventory;

    public MenuInventoryHolder(int size, Component title) {
        this.inventory = plugin.getServer().createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}

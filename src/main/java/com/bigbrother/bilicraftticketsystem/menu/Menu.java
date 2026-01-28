package com.bigbrother.bilicraftticketsystem.menu;


import com.bigbrother.bilicraftticketsystem.menu.impl.*;
import org.bukkit.Bukkit;
import xyz.xenondevs.invui.window.Window;

import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public abstract class Menu {
    protected Window window;

    public void open() {
        if (Bukkit.isPrimaryThread()) {
            if (this.window.isOpen()) {
                close();
            }
            this.window.open();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (this.window.isOpen()) {
                    close();
                }
                this.window.open();
            });
        }
    }

    public void close() {
        if (Bukkit.isPrimaryThread()) {
            this.window.close();
        } else {
            Bukkit.getScheduler().runTask(plugin, this.window::close);
        }
    }

    public static void reloadAll() {
        MenuMain.reload();
        MenuLocation.reload();
        MenuFilter.reload();
        MenuTicketbg.reload();
    }

    public static void clearPlayerCache(UUID uuid) {
        MenuMain.getMainMenuMapping().remove(uuid);
        MenuLocation.getLocationMenuMapping().remove(uuid);
        MenuFilter.getFilterMenuMapping().remove(uuid);
        MenuTicketbg.getTicketbgMenuMapping().remove(uuid);
    }
}

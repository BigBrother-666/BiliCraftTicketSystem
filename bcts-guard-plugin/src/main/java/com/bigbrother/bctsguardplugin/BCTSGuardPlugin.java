package com.bigbrother.bctsguardplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BCTSGuardPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(new GuardListeners(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

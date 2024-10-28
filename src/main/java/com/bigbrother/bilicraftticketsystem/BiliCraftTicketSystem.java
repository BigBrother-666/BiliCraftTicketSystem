package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.loadMainConfig;
import static com.bigbrother.bilicraftticketsystem.config.Menu.loadMenu;

public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;

    @Override
    public void onEnable() {
        plugin = this;
        // Plugin startup logic
        // 生成配置文件
        saveResource("config.yml", /* replace */ false);
        saveResource("menu_main.yml", /* replace */ false);
        saveResource("menu_location.yml", /* replace */ false);
        saveResource("menuitems.yml", /* replace */ false);
        saveResource("routes.txt", /* replace */ false);

        // 注册指令
        new BCTicketSystemCommand(this);
        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);

        Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::loadConfig, 20);
    }

    public void loadConfig() {
        loadMainConfig(this);
        loadMenu(this);
        TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.txt");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

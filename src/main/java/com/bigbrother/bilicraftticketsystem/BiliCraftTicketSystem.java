package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import org.bukkit.plugin.java.JavaPlugin;

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
        loadMenu(this);
        // 注册指令
        new BCTicketSystemCommand(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

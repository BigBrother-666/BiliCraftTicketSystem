package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.loadMainConfig;

public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;
    public static Economy econ = null;

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
        Bukkit.getPluginManager().registerEvents(new TrainListeners(), this);

        // 加载经济系统
        if (!setupEconomy() ) {
            getLogger().severe("Vault初始化失败！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 加载配置文件
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::loadConfig, 20);
    }

    public void loadConfig() {
        loadMainConfig(this);
        Menu.loadMenuConfig(this);
        TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.txt");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

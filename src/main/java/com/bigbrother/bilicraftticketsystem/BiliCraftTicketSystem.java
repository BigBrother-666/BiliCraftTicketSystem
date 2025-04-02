package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import com.bigbrother.bilicraftticketsystem.config.ItemsConfig;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseManager;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.signactions.*;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicketDisplay;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

@Slf4j
public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;
    public static Economy econ = null;
    public static TrainDatabaseManager trainDatabaseManager;
    public static final Component PREFIX = Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD);

    // 控制牌
    public final CustomSignActionAnnounce customSignActionAnnounce = new CustomSignActionAnnounce();
    public final SignActionBCSpawn signActionBCSpawn = new SignActionBCSpawn();
    public final CustomSignActionStation customSignActionStation = new CustomSignActionStation();
    public final SignActionShowroute signActionShowroute = new SignActionShowroute();
    public final CustomSignActionSpawn customSignActionSpawn = new CustomSignActionSpawn();
    public final CustomSignActionProperties customSignActionProperties = new CustomSignActionProperties();

    @Override
    public void onEnable() {
        printLogo();

        plugin = this;
        // Plugin startup logic
        // 生成配置文件
        saveResource(EnumConfig.MAIN_CONFIG.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_MAIN.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_LOCATION.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_FILTER.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_ITEMS.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_TICKETBG.getFileName(), /* replace */ false);
        saveResource(EnumConfig.ROUTE_MMD.getFileName(), /* replace */ false);

        // 注册指令
        new BCTicketSystemCommand(this);

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new TrainListeners(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);
        Bukkit.getPluginManager().registerEvents(new SignActionShowroute(), this);

        // 加载经济系统
        if (!setupEconomy()) {
            getLogger().severe("Vault初始化失败！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 加载配置文件
        Bukkit.getScheduler().runTaskAsynchronously(this, sender -> loadConfig(Bukkit.getConsoleSender()));

        // 注册控制牌
        SignAction.register(customSignActionAnnounce, true);
        SignAction.register(signActionBCSpawn);
        SignAction.register(customSignActionStation, true);
        SignAction.register(signActionShowroute);
        SignAction.register(customSignActionSpawn, true);
        SignAction.register(customSignActionProperties, true);
    }

    /**
     * 加载所有配置
     * 加载插件和reload时调用
     * @param sender sender
     */
    public void loadConfig(CommandSender sender) {
        try {
            MainConfig.loadMainConfig(this);
            plugin.getLogger().log(Level.INFO, "成功加载主配置");
            ItemsConfig.loadItemsConfig(this);
            MenuConfig.loadMenuConfig(this);
            plugin.getLogger().log(Level.INFO, "成功加载GUI配置");
            BCTicketDisplay.loadFont();
            TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.mmd");
            plugin.getLogger().log(Level.INFO, "mermaid路径解析成功");
            if (trainDatabaseManager != null) {
                trainDatabaseManager.getDs().close();
            }
            trainDatabaseManager = new TrainDatabaseManager(plugin);
            plugin.getLogger().log(Level.INFO, "数据库加载完成");
            MenuMain.clearAll();
            MenuLocation.clearAll();
            MenuFilter.clearAll();
            MenuTicketbg.clearAll();
        } catch (Exception e) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getLogger().log(Level.WARNING, "加载配置时发生错误：" + e.getMessage());
            } else {
                sender.sendMessage(Component.text("加载配置时发生错误：" + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }

        if (sender instanceof ConsoleCommandSender) {
            plugin.getLogger().log(Level.INFO, "所有配置加载完成");
        } else {
            sender.sendMessage(Component.text("所有配置加载完成", NamedTextColor.GREEN));
            plugin.getLogger().log(Level.INFO, "所有配置加载完成");
        }
    }

    /**
     * 加载经济插件
     * @return 是否成功
     */
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

    private void printLogo() {
        List<Component> logo = List.of(
                Component.text("           ___    ___          _____    _            _             _      ___    _  _          _                   \n", NamedTextColor.GOLD),
                Component.text("    o O O | _ )  / __|        |_   _|  (_)    __    | |__   ___   | |_   / __|  | || |  ___   | |_    ___   _ __   \n", NamedTextColor.GOLD),
                Component.text("   o      | _ \\ | (__           | |    | |   / _|   | / /  / -_)  |  _|  \\__ \\   \\_, | (_-<   |  _|  / -_) | '  \\  \n", NamedTextColor.GOLD),
                Component.text("  TS__[O] |___/  \\___|         _|_|_  _|_|_  \\__|_  |_\\_\\  \\___|  _\\__|  |___/  _|__/  /__/_  _\\__|  \\___| |_|_|_| \n", NamedTextColor.GOLD),
                Component.text(" {======||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"|| \"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"| \n", NamedTextColor.GOLD),
                Component.text("./o--000'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-' \n", NamedTextColor.GOLD)
        );
        Component output = Component.text("\n");
        for (Component component : logo) {
            output = output.append(component);
        }
        output = output.color(NamedTextColor.GOLD);
        Bukkit.getConsoleSender().sendMessage(output);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        SignAction.unregister(customSignActionAnnounce);
        SignAction.unregister(signActionBCSpawn);
        SignAction.unregister(customSignActionStation);
        SignAction.unregister(signActionShowroute);
        SignAction.unregister(customSignActionSpawn);
        SignAction.unregister(customSignActionProperties);
    }
}

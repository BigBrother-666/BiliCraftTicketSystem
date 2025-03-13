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
import com.bigbrother.bilicraftticketsystem.menu.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.MenuMain;
import com.bigbrother.bilicraftticketsystem.signactions.CustomSignActionAnnounce;
import com.bigbrother.bilicraftticketsystem.signactions.CustomSignActionSpawn;
import com.bigbrother.bilicraftticketsystem.signactions.CustomSignActionStation;
import com.bigbrother.bilicraftticketsystem.signactions.SignActionShowroute;
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
    public final CustomSignActionAnnounce signActionAnnounce = new CustomSignActionAnnounce();
    public final CustomSignActionSpawn signActionSpawn = new CustomSignActionSpawn();
    public final CustomSignActionStation signActionStation = new CustomSignActionStation();
    public final SignActionShowroute signActionShowroute = new SignActionShowroute();

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
        saveResource(EnumConfig.ROUTE_MMD.getFileName(), /* replace */ false);

        // 加载数据库
        trainDatabaseManager = new TrainDatabaseManager(plugin);

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
        SignAction.register(signActionAnnounce, true);
        SignAction.register(signActionSpawn);
        SignAction.register(signActionStation, true);
        SignAction.register(signActionShowroute);
    }

    /**
     * 加载所有配置
     * 加载插件和reload时调用
     * @param sender sender
     */
    public void loadConfig(CommandSender sender) {
        try {
            MainConfig.loadMainConfig(this);
            ItemsConfig.loadItemsConfig(this);
            MenuConfig.loadMenuConfig(this);
            BCTicketDisplay.loadFont();
            TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.mmd");
            if (trainDatabaseManager != null) {
                trainDatabaseManager.getDs().close();
            }
            trainDatabaseManager = new TrainDatabaseManager(plugin);
            MenuMain.clearAll();
            MenuLocation.clearAll();
            MenuFilter.clearAll();
        } catch (Exception e) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getLogger().log(Level.WARNING, "加载配置文件时发生错误：" + e.getMessage());
            } else {
                sender.sendMessage(Component.text("加载配置文件时发生错误：" + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }

        if (sender instanceof ConsoleCommandSender) {
            plugin.getLogger().log(Level.INFO, "所有配置文件加载完成！");
        } else {
            sender.sendMessage(Component.text("所有配置文件加载完成！", NamedTextColor.GREEN));
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
        SignAction.unregister(signActionAnnounce);
        SignAction.unregister(signActionSpawn);
        SignAction.unregister(signActionStation);
        SignAction.unregister(signActionShowroute);
    }
}

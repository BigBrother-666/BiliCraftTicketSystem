package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bigbrother.bilicraftticketsystem.addon.AddonConfig;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeoCommand;
import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import com.bigbrother.bilicraftticketsystem.config.*;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseManager;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.addon.signactions.*;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicketDisplay;
import lombok.Getter;
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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Slf4j
public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;
    public static final Component PREFIX = Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD);
    @Getter
    private Economy econ = null;
    @Getter
    private TrainDatabaseManager trainDatabaseManager;
    @Getter
    private BCTicketSystemCommand bcTicketSystemCommand;
    @Getter
    private final File geodataDir = new File(this.getDataFolder(), "geojson");

    // 控制牌
    private final CustomSignActionAnnounce customSignActionAnnounce = new CustomSignActionAnnounce();
    private final SignActionBCSpawn signActionBCSpawn = new SignActionBCSpawn();
    private final CustomSignActionStation customSignActionStation = new CustomSignActionStation();
    private final SignActionShowroute signActionShowroute = new SignActionShowroute();
    private final CustomSignActionSpawn customSignActionSpawn = new CustomSignActionSpawn();
    private final CustomSignActionProperties customSignActionProperties = new CustomSignActionProperties();

    @Override
    public void onEnable() {
        printLogo();

        plugin = this;
        // Plugin startup logic
        // 生成配置文件
        this.getComponentLogger().info(Component.text("拷贝配置文件...", NamedTextColor.GOLD));

        saveResource(EnumConfig.MAIN_CONFIG.getFileName(), /* replace */ false);
        saveResource(EnumConfig.RAILWAY_ROUTES_CONFIG.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_MAIN.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_LOCATION.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_FILTER.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_ITEMS.getFileName(), /* replace */ false);
        saveResource(EnumConfig.MENU_TICKETBG.getFileName(), /* replace */ false);
        saveResource(EnumConfig.ROUTE_MMD.getFileName(), /* replace */ false);
        saveResource(EnumConfig.ADDON_CONFIG.getFileName(), /* replace */ false);

        // 注册指令
        this.bcTicketSystemCommand = new BCTicketSystemCommand(this);
        new GeoCommand(this);
        this.getComponentLogger().info(Component.text("指令注册成功", NamedTextColor.GOLD));

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new TrainListeners(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);
        Bukkit.getPluginManager().registerEvents(new SignActionShowroute(), this);
        this.getComponentLogger().info(Component.text("监听器注册成功", NamedTextColor.GOLD));

        // 加载经济系统
        if (!setupEconomy()) {
            this.getComponentLogger().error(Component.text("Vault初始化失败！", NamedTextColor.RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.getComponentLogger().info(Component.text("Vault初始化成功", NamedTextColor.GOLD));

        // 注册控制牌
        SignAction.register(customSignActionAnnounce, true);
        SignAction.register(signActionBCSpawn);
        SignAction.register(customSignActionStation, true);
        SignAction.register(signActionShowroute);
        SignAction.register(customSignActionSpawn, true);
        SignAction.register(customSignActionProperties, true);
        this.getComponentLogger().info(Component.text("控制牌注册成功", NamedTextColor.GOLD));

        // 加载配置文件
        this.getComponentLogger().info(Component.text("开始异步读取配置文件...", NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskAsynchronously(this, sender -> loadConfig(Bukkit.getConsoleSender()));
    }

    /**
     * 加载所有配置
     * 加载插件和reload时调用
     * @param sender sender
     */
    public void loadConfig(CommandSender sender) {
        try {
            Bukkit.getScheduler().cancelTasks(plugin);

            MainConfig.loadMainConfig(this);
            RailwayRoutesConfig.load(this);
            plugin.getComponentLogger().info(Component.text("成功加载主配置", NamedTextColor.GOLD));

            ItemsConfig.loadItemsConfig(this);
            MenuConfig.loadMenuConfig(this);
            plugin.getComponentLogger().info(Component.text("成功加载GUI配置", NamedTextColor.GOLD));

            AddonConfig.loadAddonConfig(this);
            plugin.getComponentLogger().info(Component.text("成功加载额外功能", NamedTextColor.GOLD));

            BCTicketDisplay.loadFont();
            TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.mmd");
            plugin.getComponentLogger().info(Component.text("mermaid路径解析成功", NamedTextColor.GOLD));

            if (trainDatabaseManager != null) {
                trainDatabaseManager.getDs().close();
            }
            trainDatabaseManager = new TrainDatabaseManager(plugin);
            plugin.getComponentLogger().info(Component.text("数据库加载完成", NamedTextColor.GOLD));

            MenuMain.clearAll();
            MenuLocation.clearAll();
            MenuFilter.clearAll();
            MenuTicketbg.clearAll();
        } catch (Exception e) {
            if (sender instanceof ConsoleCommandSender) {
                plugin.getComponentLogger().error(Component.text("加载配置时发生错误：" + e, NamedTextColor.RED));
            } else {
                plugin.getComponentLogger().error(Component.text("加载配置时发生错误：" + e, NamedTextColor.RED));
                sender.sendMessage(Component.text("加载配置时发生错误：" + e, NamedTextColor.RED));
            }
            return;
        }

        if (sender instanceof ConsoleCommandSender) {
            plugin.getComponentLogger().info(Component.text("所有配置加载完成", NamedTextColor.GOLD));
        } else {
            sender.sendMessage(Component.text("所有配置加载完成", NamedTextColor.GOLD));
            plugin.getComponentLogger().info(Component.text("所有配置加载完成", NamedTextColor.GOLD));
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

    /**
     * 获取地理信息采集任务的logger
     * log存放在 logs/ 下
     *
     * @return logger
     */
    public Logger getGeoTaskLogger() {
        Logger logger = Logger.getLogger("bcts-" + System.currentTimeMillis());
        logger.setUseParentHandlers(false); // 不输出到控制台

        try {
            // 创建日志文件夹
            var logDir = getDataFolder().toPath().resolve("logs").toFile();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filePath = logDir.getAbsolutePath() + File.separator + timeStr + ".log";

            FileHandler fileHandler = new FileHandler(filePath, false);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);

        } catch (IOException e) {
            getLogger().severe("无法创建日志文件: " + e.getMessage());
        }

        return logger;
    }

    private void printLogo() {
        List<Component> logo = List.of(
                Component.text("============================================================================================================\n"),
                Component.text("           ___    ___          _____    _            _             _      ___    _  _          _                   \n", NamedTextColor.GOLD),
                Component.text("    o O O | _ )  / __|        |_   _|  (_)    __    | |__   ___   | |_   / __|  | || |  ___   | |_    ___   _ __   \n", NamedTextColor.GOLD),
                Component.text("   o      | _ \\ | (__           | |    | |   / _|   | / /  / -_)  |  _|  \\__ \\   \\_, | (_-<   |  _|  / -_) | '  \\  \n", NamedTextColor.GOLD),
                Component.text("  TS__[O] |___/  \\___|         _|_|_  _|_|_  \\__|_  |_\\_\\  \\___|  _\\__|  |___/  _|__/  /__/_  _\\__|  \\___| |_|_|_| \n", NamedTextColor.GOLD),
                Component.text(" {======||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"|| \"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"| \n", NamedTextColor.GOLD),
                Component.text("./o--000'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-' \n", NamedTextColor.GOLD),
                Component.text("=========================================== ")
                        .append(Component.text("v" + this.getPluginMeta().getVersion() + "  by " + String.join(", ", this.getPluginMeta().getAuthors()), NamedTextColor.AQUA))
                        .append(Component.text(" ==========================================="))
        );
        Component output = Component.text("\n");
        for (Component component : logo) {
            output = output.append(component);
        }
        this.getComponentLogger().info(output);
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

        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

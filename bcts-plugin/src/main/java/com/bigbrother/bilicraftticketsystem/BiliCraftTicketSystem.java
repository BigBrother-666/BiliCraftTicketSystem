package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.addon.AddonConfig;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeoCommand;
import com.bigbrother.bilicraftticketsystem.addon.geodata.GeoDatabaseManager;
import com.bigbrother.bilicraftticketsystem.addon.geodata.prgeotask.PRGeoTask;
import com.bigbrother.bilicraftticketsystem.addon.signactions.*;
import com.bigbrother.bilicraftticketsystem.commands.*;
import com.bigbrother.bilicraftticketsystem.commands.argument.CommandParsers;
import com.bigbrother.bilicraftticketsystem.commands.argument.CommandSuggestions;
import com.bigbrother.bilicraftticketsystem.config.*;
import com.bigbrother.bilicraftticketsystem.database.TrainDatabaseManager;
import com.bigbrother.bilicraftticketsystem.listeners.CardListeners;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.route.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.ticket.BCCardInfo;
import com.bigbrother.bilicraftticketsystem.ticket.BCTicketDisplay;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.C;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.io.File;
import java.util.List;

@Slf4j
@Getter
public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;
    public static final Component PREFIX = Component.text("[帕拉伦国有铁路车票系统] ", NamedTextColor.GOLD);
    private Economy econ = null;
    private TrainDatabaseManager trainDatabaseManager;
    private GeoDatabaseManager geoDatabaseManager;

    private final File geodataDir = new File(this.getDataFolder(), "geojson");

    // 控制牌
    private final CustomSignActionAnnounce customSignActionAnnounce = new CustomSignActionAnnounce();
    private final SignActionBCSpawn signActionBCSpawn = new SignActionBCSpawn();
    private final CustomSignActionStation customSignActionStation = new CustomSignActionStation();
    private final SignActionShowroute signActionShowroute = new SignActionShowroute();
    private final CustomSignActionSpawn customSignActionSpawn = new CustomSignActionSpawn();
    private final CustomSignActionProperties customSignActionProperties = new CustomSignActionProperties();

    // Command
    private final AdminCommand adminCommand = new AdminCommand(this);
    private final RouteCommand routeCommand = new RouteCommand(this);
    private final TicketbgCommand ticketbgCommand = new TicketbgCommand(this);
    private final TransitPassCommand transitPassCommand = new TransitPassCommand(this);
    private final CardCommand cardCommand = new CardCommand(this);
    private final GeoCommand geoCommand = new GeoCommand(this);


    @Override
    public void onEnable() {
        printLogo();
        plugin = this;
        // 生成配置文件
        copyResources();
        // 注册指令
        initCommands();
        // 注册监听器
        initListeners();
        // 加载经济系统
        setupEconomy();
        // 注册控制牌
        initSignActions();
        // 加载配置文件
        this.getComponentLogger().info(Component.text("开始异步读取配置文件...", NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskAsynchronously(this, sender -> loadConfig(Bukkit.getConsoleSender()));
    }

    /**
     * 加载所有配置
     * 加载插件和reload时调用
     *
     * @param sender sender
     */
    public void loadConfig(CommandSender sender) {
        try {
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
            trainDatabaseManager = new TrainDatabaseManager(this);
            geoDatabaseManager = new GeoDatabaseManager(this);
            plugin.getComponentLogger().info(Component.text("数据库加载完成", NamedTextColor.GOLD));

            Menu.reloadAll();
            BCCardInfo.reloadAllCache();
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

    private void copyResources() {
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
        saveResource(EnumConfig.MENU_CARD.getFileName(), /* replace */ false);
    }

    private void initCommands() {
        LegacyPaperCommandManager<CommandSender> commandManager = LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.simpleCoordinator()
        );
        @SuppressWarnings({"unchecked", "rawtypes"})
        AnnotationParser<C> annotationParser = new AnnotationParser(commandManager, CommandSender.class);
        annotationParser.parse(new CommandSuggestions(), new CommandParsers());
        annotationParser.parse(adminCommand, routeCommand, ticketbgCommand, transitPassCommand, geoCommand, cardCommand);
        this.getComponentLogger().info(Component.text("指令注册成功", NamedTextColor.GOLD));
    }

    private void initListeners() {
        Bukkit.getPluginManager().registerEvents(new TrainListeners(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);
        Bukkit.getPluginManager().registerEvents(new CardListeners(), this);
        Bukkit.getPluginManager().registerEvents(new GuardListeners(), this);
        Bukkit.getPluginManager().registerEvents(signActionShowroute, this);
        Bukkit.getPluginManager().registerEvents(geoCommand, this);
        this.getComponentLogger().info(Component.text("监听器注册成功", NamedTextColor.GOLD));
    }

    private void initSignActions() {
        SignAction.register(customSignActionAnnounce, true);
        SignAction.register(signActionBCSpawn);
        SignAction.register(customSignActionStation, true);
        SignAction.register(signActionShowroute);
        SignAction.register(customSignActionSpawn, true);
        SignAction.register(customSignActionProperties, true);
        this.getComponentLogger().info(Component.text("控制牌注册成功", NamedTextColor.GOLD));
    }

    /**
     * 加载经济插件
     */
    private void setupEconomy() {
        Plugin vaultPlugin = getServer().getPluginManager().getPlugin("Vault");
        if (vaultPlugin != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
            this.getComponentLogger().info(Component.text("Vault初始化成功", NamedTextColor.GOLD));
            return;
        }
        this.getComponentLogger().error(Component.text("Vault初始化失败！", NamedTextColor.RED));
        getServer().getPluginManager().disablePlugin(this);
    }

    public CoreProtectAPI getCoreProtectAPI() {
        Plugin pl = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (!(pl instanceof CoreProtect)) {
            return null;
        }
        CoreProtectAPI coreProtect = ((CoreProtect) pl).getAPI();
        if (!coreProtect.isEnabled()) {
            return null;
        }
        return coreProtect;
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

        BCCardInfo.saveAll();
        if (this.geoCommand != null) {
            geoCommand.getTaskMap().values().forEach(PRGeoTask::closeLoggerHandler);
        }

        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

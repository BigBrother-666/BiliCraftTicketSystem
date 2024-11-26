package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bigbrother.bilicraftticketsystem.commands.BCTicketSystemCommand;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import com.bigbrother.bilicraftticketsystem.listeners.PlayerListeners;
import com.bigbrother.bilicraftticketsystem.listeners.TrainListeners;
import com.bigbrother.bilicraftticketsystem.signactions.CustomSignActionAnnounce;
import com.bigbrother.bilicraftticketsystem.signactions.CustomSignActionSpawn;
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

import static com.bigbrother.bilicraftticketsystem.config.MainConfig.loadMainConfig;

@Slf4j
public final class BiliCraftTicketSystem extends JavaPlugin {
    public static BiliCraftTicketSystem plugin;
    public static Economy econ = null;
    public final CustomSignActionAnnounce signActionAnnounce = new CustomSignActionAnnounce();
    public final CustomSignActionSpawn signActionSpawn = new CustomSignActionSpawn();

    @Override
    public void onEnable() {
        printLogo();

        plugin = this;
        // Plugin startup logic
        // 生成配置文件
        saveResource("config.yml", /* replace */ false);
        saveResource("menu_main.yml", /* replace */ false);
        saveResource("menu_location.yml", /* replace */ false);
        saveResource("menuitems.yml", /* replace */ false);
        saveResource("routes.mmd", /* replace */ false);

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
        Bukkit.getScheduler().runTaskAsynchronously(this, sender -> loadConfig(Bukkit.getConsoleSender()));

        // 注册控制牌
        SignAction.register(signActionAnnounce, true);
        SignAction.register(signActionSpawn);
    }

    public void loadConfig(CommandSender sender) {
        try {
            loadMainConfig(this);
            Menu.loadMenuConfig(this);
            TrainRoutes.readGraphFromFile(this.getDataFolder().getPath() + File.separator + "routes.mmd");
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
                Component.text("           ___    ___          _____    _            _             _      ___    _  _          _                   " + "\n", NamedTextColor.GOLD),
                Component.text("    o O O | _ )  / __|        |_   _|  (_)    __    | |__   ___   | |_   / __|  | || |  ___   | |_    ___   _ __   " + "\n", NamedTextColor.GOLD),
                Component.text("   o      | _ \\ | (__           | |    | |   / _|   | / /  / -_)  |  _|  \\__ \\   \\_, | (_-<   |  _|  / -_) | '  \\  " + "\n", NamedTextColor.GOLD),
                Component.text("  TS__[O] |___/  \\___|         _|_|_  _|_|_  \\__|_  |_\\_\\  \\___|  _\\__|  |___/  _|__/  /__/_  _\\__|  \\___| |_|_|_| " + "\n", NamedTextColor.GOLD),
                Component.text(" {======||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"|| \"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"||\"\"\"\"\"| " + "\n", NamedTextColor.GOLD),
                Component.text("./o--000'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-'`-0-0-' " + "\n", NamedTextColor.GOLD)
        );
        Component output = Component.text("\n");
        for (Component component : logo) {
            output = output.append(component);
        }
        Bukkit.getConsoleSender().sendMessage(output);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        SignAction.unregister(signActionAnnounce);
        SignAction.unregister(signActionSpawn);
    }
}

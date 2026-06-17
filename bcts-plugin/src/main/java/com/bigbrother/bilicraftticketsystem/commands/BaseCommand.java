package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import com.bigbrother.bilicraftticketsystem.wizard.RouteWizard;
import com.bigbrother.bilicraftticketsystem.wizard.SystemWizard;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

public class BaseCommand {
    private final BiliCraftTicketSystem plugin;

    public BaseCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("打开车票系统界面")
    @Command("ticket")
    @Permission("bcts.ticket.open")
    public void openTicketGui(Player player) {
        MenuMain.getMenu(player).open();
    }

    @CommandDescription("打开车票系统背景图设置界面")
    @Command("ticket bg")
    @Permission("bcts.ticket.open")
    public void openBgGui(Player player) {
        MenuTicketbg.getMenu(player).open();
    }
}

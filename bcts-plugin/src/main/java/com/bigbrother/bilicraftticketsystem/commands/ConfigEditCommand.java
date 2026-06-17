package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
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

public class ConfigEditCommand {
    private final BiliCraftTicketSystem plugin;

    public ConfigEditCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("游戏内新建 / 修改线路配置（routes.yml）")
    @Command("ticketconfig editRoute <lineId>")
    @Permission("bcts.ticket.editroute")
    public void editRoute(
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        boolean editMode = LineConfig.contains(lineId);
        if (editMode) {
            // 修改模式：校验玩家在该线路所属铁路系统的成员内
            String systemId = LineConfig.getSystemId(lineId);
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            if (system == null) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                        "线路 [%s] 所属铁路系统 [%s] 不存在，无法校验权限。".formatted(lineId, systemId),
                        NamedTextColor.RED)));
                return;
            }
            if (!system.isMember(player.getUniqueId())) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                        "你不是该线路所属铁路系统 [%s] 的成员，无权修改。".formatted(systemId),
                        NamedTextColor.RED)));
                return;
            }
        } else {
            // 新建模式：玩家必须至少属于一个铁路系统，才能把线路归入某系统
            if (RailwaySystemConfig.getSystemsOfMember(player.getUniqueId()).isEmpty()) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                        "你还不属于任何铁路系统，无法新建线路。请先用 /ticket editSystem <id> 创建一个系统。",
                        NamedTextColor.RED)));
                return;
            }
        }
        WizardManager.start(new RouteWizard(player, lineId, editMode));
    }

    @CommandDescription("游戏内新建 / 修改铁路系统配置（railway_system.yml）")
    @Command("ticketconfig editSystem <systemId>")
    @Permission("bcts.ticket.editsystem")
    public void editSystem(
            Player player,
            @Argument(value = "systemId", description = "铁路系统 id", suggestions = "systemId")
            String systemId
    ) {
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        boolean editMode = RailwaySystemConfig.contains(systemId);
        if (editMode) {
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            if (!system.isMember(player.getUniqueId())) {
                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                        "你不是铁路系统 [%s] 的成员，无权修改。".formatted(systemId),
                        NamedTextColor.RED)));
                return;
            }
        }
        WizardManager.start(new SystemWizard(player, systemId, editMode));
    }
}

package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.wizard.RouteWizard;
import com.bigbrother.bilicraftticketsystem.wizard.SystemWizard;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import java.util.List;

public class ConfigEditCommand {
    private final BiliCraftTicketSystem plugin;

    public ConfigEditCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    @CommandDescription("游戏内新建 / 修改线路配置（railway_routes.yml）")
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

    @CommandDescription("删除一条线路配置（railway_routes.yml）")
    @Command("ticketconfig delRoute <lineId>")
    @Permission("bcts.ticket.editroute")
    public void delRoute(
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        if (!LineConfig.contains(lineId)) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "线路 [%s] 不存在。".formatted(lineId), NamedTextColor.RED)));
            return;
        }
        // 校验玩家在该线路所属铁路系统的成员内（与 editRoute 修改模式一致）
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
                    "你不是该线路所属铁路系统 [%s] 的成员，无权删除。".formatted(systemId),
                    NamedTextColor.RED)));
            return;
        }
        // 要求点击确认，点了才删除
        LineInfo line = LineConfig.get(lineId);
        String lineName = line == null ? lineId : line.getLineName();
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "确认删除线路 %s (%s)？".formatted(lineName, lineId), NamedTextColor.YELLOW)));
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("[点击确认删除]", NamedTextColor.RED)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.callback(audience -> {
                            // 点击时重新校验（线路是否仍存在 / 成员关系可能已变化）
                            if (!LineConfig.contains(lineId)) {
                                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                                        "线路 [%s] 已不存在。".formatted(lineId), NamedTextColor.RED)));
                                return;
                            }
                            RailwaySystemInfo latest = RailwaySystemConfig.get(LineConfig.getSystemId(lineId));
                            if (latest == null || !latest.isMember(player.getUniqueId())) {
                                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                                        "你无权删除线路 [%s]。".formatted(lineId), NamedTextColor.RED)));
                                return;
                            }
                            performRouteDelete(player, lineId);
                        })))
                .append(Component.text(" 取消请忽略本消息。", NamedTextColor.GRAY)));
    }

    /**
     * 执行线路删除并重载配置。
     *
     * @param player 操作玩家
     * @param lineId 线路 id
     */
    private void performRouteDelete(Player player, String lineId) {
        LineConfig.delete(lineId);
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "已删除线路 [%s]，正在重载配置...".formatted(lineId), NamedTextColor.GREEN)));
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "提示：删除的线路如果在运营（包含在路由图中），请用 /railgeo walkAll 重新遍历生成路由图。",
                NamedTextColor.YELLOW)));
        reloadConfig(player);
    }

    @CommandDescription("删除一个铁路系统配置（railway_system.yml），并连带删除其下所有线路")
    @Command("ticketconfig delSystem <systemId>")
    @Permission("bcts.ticket.editsystem")
    public void delSystem(
            Player player,
            @Argument(value = "systemId", description = "铁路系统 id", suggestions = "systemId")
            String systemId
    ) {
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        if (system == null) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "铁路系统 [%s] 不存在。".formatted(systemId), NamedTextColor.RED)));
            return;
        }
        if (!system.isMember(player.getUniqueId())) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "你不是铁路系统 [%s] 的成员，无权删除。".formatted(systemId),
                    NamedTextColor.RED)));
            return;
        }
        // 先删除该系统下的所有线路，再删除系统本身（避免遗留孤儿线路指向已删系统）
        List<String> lineIds = LineConfig.getLineIdsOfSystem(systemId);
        // 系统下无线路：直接删除
        if (lineIds.isEmpty()) {
            performSystemDelete(player, systemId, lineIds);
            return;
        }
        // 系统下有线路：列出「线路名(线路id)」并要求点击确认，点了才连带删除
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "铁路系统 [%s] 下还有 %d 条线路，删除该系统将一并删除：".formatted(systemId, lineIds.size()),
                NamedTextColor.YELLOW)));
        for (String lineId : lineIds) {
            LineInfo line = LineConfig.get(lineId);
            String lineName = line == null ? lineId : line.getLineName();
            player.sendMessage(Component.text("  - %s (%s)".formatted(lineName, lineId), NamedTextColor.GRAY));
        }
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("[点击确认删除]", NamedTextColor.RED)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.callback(audience -> {
                            // 点击时重新校验（系统是否仍存在 / 成员关系可能已变化）
                            RailwaySystemInfo latest = RailwaySystemConfig.get(systemId);
                            if (latest == null) {
                                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                                        "铁路系统 [%s] 已不存在。".formatted(systemId), NamedTextColor.RED)));
                                return;
                            }
                            if (!latest.isMember(player.getUniqueId())) {
                                player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                                        "你不是铁路系统 [%s] 的成员，无权删除。".formatted(systemId), NamedTextColor.RED)));
                                return;
                            }
                            performSystemDelete(player, systemId, LineConfig.getLineIdsOfSystem(systemId));
                        })))
                .append(Component.text(" 取消请忽略本消息。", NamedTextColor.GRAY)));
    }

    /**
     * 执行铁路系统删除：先删其下所有线路，再删系统本身，最后重载配置。
     *
     * @param player   操作玩家
     * @param systemId 系统 id
     * @param lineIds  该系统下的线路 id 列表（删除前快照）
     */
    private void performSystemDelete(Player player, String systemId, List<String> lineIds) {
        for (String lineId : lineIds) {
            LineConfig.delete(lineId);
        }
        RailwaySystemConfig.delete(systemId);
        if (lineIds.isEmpty()) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "已删除铁路系统 [%s]，正在重载配置...".formatted(systemId), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "已删除铁路系统 [%s] 及其下 %d 条线路 %s，正在重载配置...".formatted(
                            systemId, lineIds.size(), lineIds), NamedTextColor.GREEN)));
        }
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "提示：删除的铁路系统下如果包含正在运营的线路（包含在路由图中的线路），请用 /railgeo walkAll 重新遍历生成路由图。",
                NamedTextColor.YELLOW)));
        reloadConfig(player);
    }

    /**
     * 删除后重载线路 / 铁路系统内存缓存，并向玩家反馈结果。
     *
     * @param player 操作玩家
     */
    private void reloadConfig(Player player) {
        try {
            RailwaySystemConfig.load(plugin);
            LineConfig.load(plugin);
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载完成", NamedTextColor.GREEN)));
        } catch (Exception e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载时发生错误：" + e.getMessage(), NamedTextColor.RED)));
        }
    }
}

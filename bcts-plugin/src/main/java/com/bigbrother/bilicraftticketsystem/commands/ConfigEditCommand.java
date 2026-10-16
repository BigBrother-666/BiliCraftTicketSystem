package com.bigbrother.bilicraftticketsystem.commands;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.StationAliasConfig;
import com.bigbrother.bilicraftticketsystem.config.StationConfigRules;
import com.bigbrother.bilicraftticketsystem.config.StationGroupConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geodata.GeodataLineRemover;
import com.bigbrother.bilicraftticketsystem.route.geodata.traversal.GeoTraversalTask;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRouteEngine;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.wizard.RouteWizard;
import com.bigbrother.bilicraftticketsystem.wizard.SystemWizard;
import com.bigbrother.bilicraftticketsystem.wizard.WizardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class ConfigEditCommand {
    private final BiliCraftTicketSystem plugin;

    public ConfigEditCommand(BiliCraftTicketSystem plugin) {
        this.plugin = plugin;
    }

    /**
     * 铁轨遍历期间禁止线路/铁路系统配置：遍历依赖当前配置，配置中途变化会让遍历结果不一致。
     *
     * @param player 操作玩家
     * @return 正在遍历返回 true（已向玩家提示），调用方应直接返回
     */
    private boolean blockedByTraversal(Player player) {
        if (GeoTraversalTask.isTraversalRunning()) {
            player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                    MainConfig.message.get("traversal-config-blocked",
                            "<red>铁路系统正在进行铁轨遍历，暂停线路/铁路系统配置指令，请稍后再试"))));
            return true;
        }
        return false;
    }

    @CommandDescription("游戏内新建 / 修改线路配置（railway_routes.yml）")
    @Command("ticketconfig editRoute <lineId>")
    @Permission("bcts.ticket.editroute")
    public void editRoute(
            Player player,
            @Argument(value = "lineId", description = "线路 id", suggestions = "lineId")
            String lineId
    ) {
        if (blockedByTraversal(player)) {
            return;
        }
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        boolean editMode = LineConfig.contains(lineId);
        if (editMode) {
            // 修改模式：校验玩家在该线路所属铁路系统的成员内
            String systemId = LineConfig.getSystemId(lineId);
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            if (system == null) {
                player.sendMessage(MainConfig.prefix.append(Component.text(
                        "线路 [%s] 所属铁路系统 [%s] 不存在，无法校验权限。".formatted(lineId, systemId),
                        NamedTextColor.RED)));
                return;
            }
            if (!system.isMember(player.getUniqueId())) {
                player.sendMessage(MainConfig.prefix.append(Component.text(
                        "你不是该线路所属铁路系统 [%s] 的成员，无权修改。".formatted(systemId),
                        NamedTextColor.RED)));
                return;
            }
        } else {
            // 新建模式：玩家必须至少属于一个铁路系统，才能把线路归入某系统
            if (RailwaySystemConfig.getSystemsOfMember(player.getUniqueId()).isEmpty()) {
                player.sendMessage(MainConfig.prefix.append(Component.text(
                        "你还不属于任何铁路系统，无法新建线路。请先用 /ticketconfig editSystem <id> 创建一个系统。",
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
        if (blockedByTraversal(player)) {
            return;
        }
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        boolean editMode = RailwaySystemConfig.contains(systemId);
        if (editMode) {
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            if (!system.isMember(player.getUniqueId())) {
                player.sendMessage(MainConfig.prefix.append(Component.text(
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
        if (blockedByTraversal(player)) {
            return;
        }
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        if (!LineConfig.contains(lineId)) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "线路 [%s] 不存在。".formatted(lineId), NamedTextColor.RED)));
            return;
        }
        // 校验玩家在该线路所属铁路系统的成员内（与 editRoute 修改模式一致）
        String systemId = LineConfig.getSystemId(lineId);
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        if (system == null) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "线路 [%s] 所属铁路系统 [%s] 不存在，无法校验权限。".formatted(lineId, systemId),
                    NamedTextColor.RED)));
            return;
        }
        if (!system.isMember(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "你不是该线路所属铁路系统 [%s] 的成员，无权删除。".formatted(systemId),
                    NamedTextColor.RED)));
            return;
        }
        // 要求点击确认，点了才删除
        LineInfo line = LineConfig.get(lineId);
        String lineName = line == null ? lineId : line.getLineName();
        player.sendMessage(MainConfig.prefix.append(Component.text(
                "确认删除线路 %s (%s)？".formatted(lineName, lineId), NamedTextColor.YELLOW)));
        player.sendMessage(MainConfig.prefix.append(Component.text("[点击确认删除]", NamedTextColor.RED)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.callback(audience -> {
                            if (blockedByTraversal(player)) {
                                return;
                            }
                            // 点击时重新校验（线路是否仍存在 / 成员关系可能已变化）
                            if (!LineConfig.contains(lineId)) {
                                player.sendMessage(MainConfig.prefix.append(Component.text(
                                        "线路 [%s] 已不存在。".formatted(lineId), NamedTextColor.RED)));
                                return;
                            }
                            RailwaySystemInfo latest = RailwaySystemConfig.get(LineConfig.getSystemId(lineId));
                            if (latest == null || !latest.isMember(player.getUniqueId())) {
                                player.sendMessage(MainConfig.prefix.append(Component.text(
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
        deleteLineFiles(lineId);
        player.sendMessage(MainConfig.prefix.append(Component.text(
                "已删除线路 [%s]，正在重载配置...".formatted(lineId), NamedTextColor.GREEN)));
        reloadConfig(player);
        removeGeodata(player, List.of(lineId), List.of());
    }

    @CommandDescription("删除一个铁路系统配置（railway_system.yml），并连带删除其下所有线路（仅创建者）")
    @Command("ticketconfig delSystem <systemId>")
    @Permission("bcts.ticket.editsystem")
    public void delSystem(
            Player player,
            @Argument(value = "systemId", description = "铁路系统 id", suggestions = "systemId")
            String systemId
    ) {
        if (blockedByTraversal(player)) {
            return;
        }
        if (WizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("你正在进行另一项编辑，请先完成或退出。", NamedTextColor.RED)));
            return;
        }
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        if (system == null) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "铁路系统 [%s] 不存在。".formatted(systemId), NamedTextColor.RED)));
            return;
        }
        if (!canDeleteSystem(player, system)) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "只有铁路系统 [%s] 的创建者可以删除该系统。".formatted(systemId),
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
        player.sendMessage(MainConfig.prefix.append(Component.text(
                "铁路系统 [%s] 下还有 %d 条线路，删除该系统将一并删除：".formatted(systemId, lineIds.size()),
                NamedTextColor.YELLOW)));
        for (String lineId : lineIds) {
            LineInfo line = LineConfig.get(lineId);
            String lineName = line == null ? lineId : line.getLineName();
            player.sendMessage(Component.text("  - %s (%s)".formatted(lineName, lineId), NamedTextColor.GRAY));
        }
        player.sendMessage(MainConfig.prefix.append(Component.text("[点击确认删除]", NamedTextColor.RED)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.callback(audience -> {
                            if (blockedByTraversal(player)) {
                                return;
                            }
                            // 点击时重新校验（系统是否仍存在 / 成员关系可能已变化）
                            RailwaySystemInfo latest = RailwaySystemConfig.get(systemId);
                            if (latest == null) {
                                player.sendMessage(MainConfig.prefix.append(Component.text(
                                        "铁路系统 [%s] 已不存在。".formatted(systemId), NamedTextColor.RED)));
                                return;
                            }
                            if (!canDeleteSystem(player, latest)) {
                                player.sendMessage(MainConfig.prefix.append(Component.text(
                                        "只有铁路系统 [%s] 的创建者可以删除该系统。".formatted(systemId), NamedTextColor.RED)));
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
        deleteSystemFiles(systemId, lineIds);
        if (lineIds.isEmpty()) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "已删除铁路系统 [%s]，正在重载配置...".formatted(systemId), NamedTextColor.GREEN)));
        } else {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "已删除铁路系统 [%s] 及其下 %d 条线路 %s，正在重载配置...".formatted(
                            systemId, lineIds.size(), lineIds), NamedTextColor.GREEN)));
        }
        reloadConfig(player);
        removeGeodata(player, lineIds, List.of(systemId));
    }

    /**
     * 删除线路后同步清理其线路图数据，使删除立即生效、无需重新 {@code /railgeo walkAll}：
     * <ol>
     *   <li>删除这些线路登记的遍历起点（否则残留的起点会让下次遍历报错、也会挡住孤立文件清理）；</li>
     *   <li>删除这些线路的 geojson 文件，并清掉其它线路文件中通往它们的区间（见 {@link GeodataLineRemover}）；</li>
     *   <li>重载路由图，并在已连接网页后端时推送最新快照。</li>
     * </ol>
     * 文件读写放异步线程，完成后回主线程反馈与推送。
     *
     * @param player    操作玩家
     * @param lineIds   被删除的线路 id
     * @param systemIds 被删除的铁路系统 id（只删线路时为空）
     */
    private void removeGeodata(Player player, Collection<String> lineIds, Collection<String> systemIds) {
        removeGeodata(plugin, lineIds, systemIds, (result, error) -> {
            if (error != null) {
                player.sendMessage(MainConfig.prefix.append(Component.text(
                        "清理线路图数据失败：%s。请用 /railgeo walkAll 重新构建线路图".formatted(error),
                        NamedTextColor.RED)));
                return;
            }
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "已清理线路图数据（删除 %d 个文件，更新 %d 个文件）".formatted(
                            result.deletedFiles(), result.rewrittenFiles()), NamedTextColor.GREEN)));
        });
    }

    // ===== 共享删除流程（游戏内指令与网页端 config.request 共用） =====

    /**
     * delSystem 权限：仅系统创建者或拥有 {@code bcts.bypass} 的玩家可删除。
     *
     * @param player 操作玩家
     * @param system 铁路系统
     * @return true 表示允许删除
     */
    public static boolean canDeleteSystem(Player player, RailwaySystemInfo system) {
        return system.isCreator(player.getUniqueId()) || player.hasPermission(GeoTraversalTask.PERM_BYPASS_COOLDOWN);
    }

    /**
     * 从 railway_routes.yml 删除一条线路（仅写文件，调用方随后重载并 {@link #removeGeodata}）。
     *
     * @param lineId 线路 id
     */
    public static void deleteLineFiles(String lineId) {
        LineConfig.delete(lineId);
    }

    /**
     * 删除铁路系统：先删其下所有线路，再删系统本身（仅写文件，调用方随后重载并 {@link #removeGeodata}）。
     *
     * @param systemId 系统 id
     * @param lineIds  该系统下的线路 id 列表（删除前快照）
     */
    public static void deleteSystemFiles(String systemId, Collection<String> lineIds) {
        for (String lineId : lineIds) {
            LineConfig.delete(lineId);
        }
        RailwaySystemConfig.delete(systemId);
    }

    /**
     * 删除线路后同步清理其线路图数据，使删除立即生效、无需重新 {@code /railgeo walkAll}：
     * <ol>
     *   <li>删除这些线路登记的遍历起点（否则残留的起点会让下次遍历报错、也会挡住孤立文件清理）；</li>
     *   <li>删除这些线路的 geojson 文件，并清掉其它线路文件中通往它们的区间（见 {@link GeodataLineRemover}）；</li>
     *   <li>重载路由图，并在已连接网页后端时推送最新快照。</li>
     * </ol>
     * 文件读写放异步线程，完成后回主线程推送快照并回调 {@code onDone}（结果, 错误信息）。须在主线程调用。
     *
     * @param plugin    插件实例
     * @param lineIds   被删除的线路 id
     * @param systemIds 被删除的铁路系统 id（只删线路时为空）
     * @param onDone    完成回调（主线程）：成功时 error 为 null
     */
    public static void removeGeodata(BiliCraftTicketSystem plugin, Collection<String> lineIds, Collection<String> systemIds,
                                     BiConsumer<GeodataLineRemover.Result, String> onDone) {
        for (String lineId : lineIds) {
            plugin.getGeoDatabaseManager().deleteGeoNodeLoc(lineId);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = null;
            GeodataLineRemover.Result result = null;
            try {
                result = new GeodataLineRemover().removeLines(plugin.getGeodataDir(), lineIds, systemIds);
                GeoRouteEngine.load(plugin.getGeodataDir(), plugin.getComponentLogger());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String finalError = error;
            GeodataLineRemover.Result finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalError == null && plugin.getWebLink() != null && plugin.getWebLink().getClient().isConnected()) {
                    plugin.getWebLink().getSnapshotPublisher().publishAll();
                }
                if (onDone != null) {
                    onDone.accept(finalResult, finalError);
                }
            });
        });
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
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "配置重载完成", NamedTextColor.GREEN)));
        } catch (Exception e) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "配置重载时发生错误：" + e.getMessage(), NamedTextColor.RED)));
        }
    }

    @CommandDescription("新增 / 修改车站别名（station_alias.yml）")
    @Command("ticketconfig editStationAlias <stationName> [englishName]")
    @Permission("bcts.ticket.stationalias")
    public void editStationAlias(
            CommandSender sender,
            @Argument(value = "stationName", description = "中文车站名", suggestions = "stationName")
            String stationName,
            @Greedy
            @Argument(value = "englishName", description = "英文车站名（可含空格）")
            @Nullable String englishName
    ) {
        if (englishName == null || englishName.isBlank()) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(
                    "请输入英文车站名：/ticketconfig editStationAlias <中文车站名> <英文车站名>", NamedTextColor.RED)));
            return;
        }
        // 合并连续空白，保留单词间单个空格
        String en = StationConfigRules.normalizeEnglishName(englishName);
        warnIfStationMissing(sender, List.of(stationName));
        String old = StationAliasConfig.get(stationName);
        StationAliasConfig.upsert(stationName, en);
        sender.sendMessage(MainConfig.prefix.append(Component.text(
                (old == null ? "已添加车站别名：%s -> %s" : "已修改车站别名：%s -> %s").formatted(stationName, en),
                NamedTextColor.GREEN)));
        publishStations();
    }

    @CommandDescription("删除车站别名（station_alias.yml）")
    @Command("ticketconfig delStationAlias <stationName>")
    @Permission("bcts.ticket.stationalias")
    public void delStationAlias(
            CommandSender sender,
            @Argument(value = "stationName", description = "中文车站名", suggestions = "stationAliasName")
            String stationName
    ) {
        if (!StationAliasConfig.delete(stationName)) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(
                    "车站 [%s] 没有设置英文名。".formatted(stationName), NamedTextColor.RED)));
            return;
        }
        sender.sendMessage(MainConfig.prefix.append(Component.text(
                "已删除车站 [%s] 的英文名。".formatted(stationName), NamedTextColor.GREEN)));
        publishStations();
    }

    @CommandDescription("新建 / 覆盖车站组，组内不同名车站两两之间可换乘（station_group.yml）")
    @Command("ticketconfig editStationGroup <groupName> <stations>")
    @Permission("bcts.ticket.stationgroup")
    public void editStationGroup(
            CommandSender sender,
            @Argument(value = "groupName", description = "车站组名", suggestions = "stationGroupName")
            String groupName,
            @Greedy
            @Argument(value = "stations", description = "车站名（至少两个，空格分隔）", suggestions = "stationNames")
            String stations
    ) {
        // 去重并保持输入顺序
        Set<String> unique = StationConfigRules.distinctStations(List.of(stations.trim().split("\\s+")));
        if (unique.size() < 2) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(
                    "车站组至少需要两个不同的车站：/ticketconfig editStationGroup <组名> <车站1> <车站2> [车站3...]",
                    NamedTextColor.RED)));
            return;
        }
        // 一个车站最多属于一个车站组（覆盖本组时，本组原有车站不算冲突）
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, String> e : StationConfigRules.groupConflicts(groupName, unique).entrySet()) {
            conflicts.add("%s(已在组 %s)".formatted(e.getKey(), e.getValue()));
        }
        if (!conflicts.isEmpty()) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(
                    "以下车站已属于其它车站组，一个车站只能属于一个组：" + String.join("、", conflicts),
                    NamedTextColor.RED)));
            return;
        }
        warnIfStationMissing(sender, unique);
        boolean existed = StationGroupConfig.all().containsKey(groupName);
        List<String> members = new ArrayList<>(unique);
        StationGroupConfig.upsert(groupName, members);
        sender.sendMessage(MainConfig.prefix.append(Component.text(
                (existed ? "已覆盖车站组 [%s]：%s" : "已创建车站组 [%s]：%s").formatted(groupName, String.join(" ", members)),
                NamedTextColor.GREEN)));
        publishStations();
    }

    @CommandDescription("列出所有车站组（station_group.yml）")
    @Command("ticketconfig listStationGroup")
    @Permission("bcts.ticket.stationgroup")
    public void listStationGroup(CommandSender sender) {
        Map<String, List<String>> groups = StationGroupConfig.all();
        if (groups.isEmpty()) {
            sender.sendMessage(MainConfig.prefix.append(Component.text("当前没有车站组。", NamedTextColor.YELLOW)));
            return;
        }
        sender.sendMessage(MainConfig.prefix.append(Component.text(
                "当前共有 %d 个车站组：".formatted(groups.size()), NamedTextColor.AQUA)));
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            sender.sendMessage(Component.text("  - %s: ".formatted(e.getKey()), NamedTextColor.GOLD)
                    .append(Component.text(String.join(" ", e.getValue()), NamedTextColor.GRAY)));
        }
    }

    @CommandDescription("删除车站组（station_group.yml）")
    @Command("ticketconfig delStationGroup <groupName>")
    @Permission("bcts.ticket.stationgroup")
    public void delStationGroup(
            CommandSender sender,
            @Argument(value = "groupName", description = "车站组名", suggestions = "stationGroupName")
            String groupName
    ) {
        if (!StationGroupConfig.delete(groupName)) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(
                    "车站组 [%s] 不存在。".formatted(groupName), NamedTextColor.RED)));
            return;
        }
        sender.sendMessage(MainConfig.prefix.append(Component.text(
                "已删除车站组 [%s]。".formatted(groupName), NamedTextColor.GREEN)));
        publishStations();
    }

    /**
     * 车站不在当前线路图中时给出警告（仍会保存：线路图可能尚未构建）。
     *
     * @param sender   指令发送者
     * @param stations 待检查的车站名
     */
    private void warnIfStationMissing(CommandSender sender, Collection<String> stations) {
        String warning = StationConfigRules.missingStationsWarning(stations);
        if (warning != null) {
            sender.sendMessage(MainConfig.prefix.append(Component.text(warning, NamedTextColor.YELLOW)));
        }
    }

    /**
     * 已连接网页后端时推送最新车站别名 / 车站组快照。
     */
    private void publishStations() {
        if (plugin.getWebLink() != null && plugin.getWebLink().getClient().isConnected()) {
            plugin.getWebLink().getSnapshotPublisher().publishStations();
        }
    }
}

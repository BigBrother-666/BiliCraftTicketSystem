package com.bigbrother.bilicraftticketsystem.listeners;

import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.RailwayRoutesConfig;
import com.bigbrother.bilicraftticketsystem.database.entity.TicketbgInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuTicketbg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public class PlayerListeners implements Listener {
    // 记录添加route的步骤
    private final Map<UUID, Integer> stepMap = new HashMap<>();

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        MenuMain.getMainMenuMapping().remove(uuid);
        MenuLocation.getLocationMenuMapping().remove(uuid);
        MenuFilter.getFilterMenuMapping().remove(uuid);
        MenuTicketbg.getTicketbgMenuMapping().remove(uuid);
        stepMap.remove(uuid);
        String removedRouteid = plugin.getBcTicketSystemCommand().getAddRouteMode().remove(uuid);
        if (removedRouteid != null) {
            RailwayRoutesConfig.railwayRoutes.remove(removedRouteid);
            // 添加route时退出游戏，重新加载config
            RailwayRoutesConfig.load(plugin);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TicketbgInfo info = plugin.getTrainDatabaseManager().getCurrTicketbgInfo(player.getUniqueId().toString());
        MenuTicketbg.getTicketbgUsageMapping().put(player.getUniqueId(), info);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查玩家是否与制图台交互
        InventoryView view = event.getView();
        if (view.getTopInventory().getType() == InventoryType.CARTOGRAPHY && TicketStore.isTicketItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    // 处理route添加
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent chatEvent) {
        Player player = chatEvent.getPlayer();
        UUID uuid = player.getUniqueId();
        if (plugin.getBcTicketSystemCommand().getAddRouteMode().containsKey(uuid)) {
            chatEvent.setCancelled(true);
            if (!stepMap.containsKey(uuid)) {
                stepMap.put(uuid, 0);
            }

            // step
            Integer step = stepMap.get(uuid);
            String routeid = plugin.getBcTicketSystemCommand().getAddRouteMode().get(uuid);
            String chatStr = chatEvent.getMessage();
            switch (step) {
                case 0:
                    // 输入到站时的内容
                    RailwayRoutesConfig.railwayRoutes.set("%s.curr-station-title".formatted(routeid), chatStr);
                    stepMap.put(uuid, step + 1);
                    player.sendMessage(Component.text("step2: 请输入完整车站列表，车站名之间使用 -> 分隔：", NamedTextColor.AQUA));
                    break;
                case 1:
                    String[] stations = chatStr.split("->");
                    if (stations.length > 1) {
                        RailwayRoutesConfig.railwayRoutes.set("%s.route".formatted(routeid), chatStr);
                        stepMap.put(uuid, step + 1);
                    } else {
                        player.sendMessage(Component.text("至少要包含两个车站，请重新输入：", NamedTextColor.RED));
                        return;
                    }
                    player.sendMessage(Component.text("step3: 请输入空格分隔的五个参数，分别是: Bossbar颜色 已经过的车站格式 未经过的车站格式 已经过的车站显示个数 未经过的车站显示个数：", NamedTextColor.AQUA));
                    player.sendMessage(Component.text("       Bossbar颜色：PINK BLUE RED GREEN YELLOW PURPLE WHITE", NamedTextColor.DARK_AQUA));
                    player.sendMessage(Component.text("       已经过和未经过的车站格式: mc格式化代码（&后的字符，不用写&）", NamedTextColor.DARK_AQUA));
                    break;
                case 2:
                    // 检查参数
                    String[] args = chatStr.split(" ");
                    if (args.length < 5) {
                        player.sendMessage(Component.text("缺少参数，需要5个空格分隔的参数，请重新输入：", NamedTextColor.RED));
                        return;
                    }
                    // 参数1
                    try {
                        BossBar.Color.valueOf(args[0].trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Component.text("Bossbar颜色错误，请重新输入：", NamedTextColor.RED));
                        return;
                    }
                    // 参数2 3 不检查

                    // 参数4 5
                    try {
                        int a = Integer.parseInt(args[3].trim());
                        int b = Integer.parseInt(args[4].trim());
                        if (a < 0 || b < 0 || (a == 0 && b == 0)) {
                            player.sendMessage(Component.text("已经过和未经过的车站显示个数为非负整数，且不能同时为0，请重新输入：", NamedTextColor.RED));
                            return;
                        }
                        int cnt = RailwayRoutesConfig.railwayRoutes.get("%s.route".formatted(routeid), "").split("->").length;
                        if (a + b > cnt) {
                            player.sendMessage(Component.text("已经过和未经过的车站显示个数之和不能超过车站个数(%s)，请重新输入：".formatted(cnt), NamedTextColor.RED));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("已经过和未经过的车站显示个数为非负整数，且不能同时为0，请重新输入：", NamedTextColor.RED));
                        return;
                    }

                    // 添加
                    RailwayRoutesConfig.railwayRoutes.set("%s.args".formatted(routeid), chatStr);
                    RailwayRoutesConfig.save();
                    RailwayRoutesConfig.load(plugin);
                    stepMap.remove(uuid);
                    plugin.getBcTicketSystemCommand().getAddRouteMode().remove(uuid);
                    player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text("线路 %s 添加成功。".formatted(routeid), NamedTextColor.GREEN)));
                    break;
                default:
                    stepMap.remove(uuid);
            }
        }
    }
}

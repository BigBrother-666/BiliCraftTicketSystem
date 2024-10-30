package com.bigbrother.bilicraftticketsystem.listeners;

import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.Menu;
import com.bigbrother.bilicraftticketsystem.entity.BCTicket;
import com.bigbrother.bilicraftticketsystem.entity.PlayerOption;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.econ;
import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.config.MainConfig.message;

public class PlayerListeners implements Listener {

    // 记录玩家的选择
    private static final Map<UUID, PlayerOption> playerOptionMap = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && event.getView().getType() == InventoryType.CHEST) {
            Component title = event.getView().title();
            if (title.equals(Menu.mainMenu.title)) {
                // 主菜单处理逻辑
                event.setCancelled(true);
                mainMenu(Menu.itemLocReverse.get(title), event);
            } else if (title.equals(Menu.locationMenu.title)) {
                event.setCancelled(true);
                locationMenu(Menu.itemLocReverse.get(title), event);
            }
        }
    }

    private void locationMenu(Map<Integer, String> itemSlot, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) {
            return;
        }
        if (playerOptionMap.get(player.getUniqueId()).isStartStationFlag()) {
            playerOptionMap.get(player.getUniqueId()).setStartStation(Component.text(itemSlot.get(event.getSlot()), NamedTextColor.GOLD));
        } else {
            playerOptionMap.get(player.getUniqueId()).setEndStation(Component.text(itemSlot.get(event.getSlot()), NamedTextColor.GOLD));
        }
        event.getView().close();
        player.openInventory(Menu.mainMenu.inventory);
//        if (currentItem != )
    }

    private void mainMenu(Map<Integer, String> itemSlot, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String itemName = itemSlot.get(event.getSlot());
        if (itemName == null) {
            itemName = "";
        }
        PlayerOption option = playerOptionMap.get(player.getUniqueId());
        List<Integer> ticketSlots = Menu.mainMenu.ticketSlots;
        switch (itemName) {
            case "start":
                option.setStartStationFlag(true);
                player.openInventory(Menu.locationMenu.inventory);
                playerOptionMap.get(player.getUniqueId()).getTickets().clear();
                break;
            case "stop":
                option.setStartStationFlag(false);
                player.openInventory(Menu.locationMenu.inventory);
                playerOptionMap.get(player.getUniqueId()).getTickets().clear();
                break;
            case "speed":
                // 设置速度
                if (event.isLeftClick()) {
                    double targetSpeed = option.getSpeed() + MainConfig.speedStep;
                    option.setSpeed(Math.min(MainConfig.maxSpeed, targetSpeed));
                } else if (event.isRightClick()) {
                    double targetSpeed = option.getSpeed() - MainConfig.speedStep;
                    option.setSpeed(Math.max(MainConfig.minSpeed, targetSpeed));
                }
                // 动态设置lore
                ItemStack speedItem = event.getCurrentItem();
                if (speedItem == null) {
                    return;
                }
                ItemMeta itemMeta = speedItem.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("当前选择的速度：%.1fkm/h".formatted(option.getSpeed() * 20 * 3.6), NamedTextColor.GOLD));
                lore.add(Component.text("左键+%.1fkm/h，右键-%.1fkm/h".formatted(MainConfig.speedStep * 20 * 3.6, MainConfig.speedStep * 20 * 3.6), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("最大%.1fkm/h，最小%.1fkm/h".formatted(MainConfig.maxSpeed * 20 * 3.6, MainConfig.minSpeed * 20 * 3.6), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                itemMeta.lore(lore);
                speedItem.setItemMeta(itemMeta);
                break;
            case "uses":
                // 设置使用次数
                if (event.isLeftClick()) {
                    double targetUses = option.getUses() + 5;
                    option.setUses((int) Math.min(MainConfig.maxUses, targetUses));
                } else if (event.isRightClick()) {
                    double targetUses = option.getUses() - 1;
                    option.setUses((int) Math.max(1, targetUses));
                }
                // 动态设置lore
                ItemStack usesItem = event.getCurrentItem();
                if (usesItem == null) {
                    return;
                }
                ItemMeta usesItemMeta = usesItem.getItemMeta();
                List<Component> usesLore = new ArrayList<>();
                usesLore.add(Component.text("当前选择的使用次数：%d次".formatted(option.getUses()), NamedTextColor.GOLD));
                usesLore.add(Component.text("左键+5次，右键-1次", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                usesItemMeta.lore(usesLore);
                usesItem.setItemMeta(usesItemMeta);
                break;
            case "search":
                // cooldown 1s
                if (option.isSearchedFlag() || !option.canSearch()) {
                    return;
                }
                option.setSearchedFlag(true);
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> option.setSearchedFlag(false), 20);

                List<TrainRoutes.PathInfo> pathInfoList = TrainRoutes.getPathInfoList(option.getStartStationString(), option.getEndStationString());
                plugin.getLogger().log(Level.INFO, pathInfoList.toString());
                if (pathInfoList.isEmpty()) {
                    ItemStack barrier = new ItemStack(Material.BARRIER);
                    ItemMeta barrierMeta = barrier.getItemMeta();
                    barrierMeta.displayName(Component.text("所选两站暂时没有直达方案！", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    barrier.setItemMeta(barrierMeta);
                    event.getView().setItem(ticketSlots.get(0), barrier);
                } else {
                    // 显示车票
                    for (int i = 0; i < ticketSlots.size() && pathInfoList.size() > i; i++) {
                        BCTicket ticket = BCTicket.createTicket(option, pathInfoList.get(i));
                        event.getView().setItem(ticketSlots.get(i), ticket.getItem(player));
                        option.getTickets().put(ticketSlots.get(i), ticket);
                    }
                }
                break;
            default:
                // 点击车票
                ItemStack item0 = event.getView().getItem(Menu.mainMenu.ticketSlots.get(0));
                if (ticketSlots.contains(event.getSlot()) && item0 != null && !item0.getType().equals(Material.BARRIER)) {
                    BCTicket bcTicket = option.getTickets().get(event.getSlot());
                    EconomyResponse r = econ.withdrawPlayer(player, bcTicket.getTotalPrice());

                    if (r.transactionSuccess()) {
                        player.sendMessage(Component.text(
                                message.get("buy-success", "您成功花费{cost}购买了{name}")
                                        .replace("{cost}", "%.2f".formatted(r.amount))
                                        .replace("{name}", bcTicket.getItemName())));
                        bcTicket.giveTo(player);
                    } else {
                        player.sendMessage(Component.text(
                                message.get("buy-failure", "车票购买失败：{error}")
                                        .replace("{error}", r.errorMessage)));
                    }
                    option.setUses(1);
                    event.getView().close();
                }
                break;
        }
        updateTickets(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && event.getView().getType() == InventoryType.CHEST) {
            Component title = event.getView().title();
            Map<String, Integer> itemSlot = Menu.itemLoc.get(title);
            if (title.equals(Menu.mainMenu.title)) {
                // 先创建option
                if (!playerOptionMap.containsKey(player.getUniqueId())) {
                    playerOptionMap.put(player.getUniqueId(), new PlayerOption());
                }

                // 获取item
                ItemStack startStation = event.getView().getItem(itemSlot.get("start"));
                ItemStack endStation = event.getView().getItem(itemSlot.get("stop"));
                ItemStack search = event.getView().getItem(itemSlot.get("search"));
                ItemStack speed = event.getView().getItem(itemSlot.get("speed"));
                ItemStack uses = event.getView().getItem(itemSlot.get("uses"));
                if (startStation == null || endStation == null || search == null || speed == null || uses == null) {
                    return;
                }

                // 初始化物品动态lore
                PlayerOption option = playerOptionMap.get(player.getUniqueId());

                ItemMeta itemMeta = startStation.getItemMeta();
                itemMeta.lore(List.of(Component.text("当前选择：", NamedTextColor.GREEN).append(option.getStartStation())));
                startStation.setItemMeta(itemMeta);

                itemMeta = endStation.getItemMeta();
                itemMeta.lore(List.of(Component.text("当前选择：", NamedTextColor.GREEN).append(option.getEndStation())));
                endStation.setItemMeta(itemMeta);

                itemMeta = search.getItemMeta();
                if (option.canSearch()) {
                    itemMeta.lore(List.of(Component.text("点击搜索车票", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
                } else {
                    itemMeta.lore(List.of(Component.text("不可用，未选择起始站/终到站", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
                }
                search.setItemMeta(itemMeta);

                itemMeta = speed.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("当前选择的速度：%.1fkm/h".formatted(option.getSpeed() * 20 * 3.6), NamedTextColor.GOLD));
                lore.add(Component.text("左键+%.1fkm/h，右键-%.1fkm/h".formatted(MainConfig.speedStep * 20 * 3.6, MainConfig.speedStep * 20 * 3.6), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("最大%.1fkm/h，最小%.1fkm/h".formatted(MainConfig.maxSpeed * 20 * 3.6, MainConfig.minSpeed * 20 * 3.6), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                itemMeta.lore(lore);
                speed.setItemMeta(itemMeta);

                itemMeta = uses.getItemMeta();
                List<Component> usesLore = new ArrayList<>();
                usesLore.add(Component.text("当前选择的使用次数：%d次".formatted(option.getUses()), NamedTextColor.GOLD));
                usesLore.add(Component.text("左键+5次，右键-1次", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                itemMeta.lore(usesLore);
                uses.setItemMeta(itemMeta);
            }
        }
    }

    private void updateTickets(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        PlayerOption playerOption = playerOptionMap.get(player.getUniqueId());
        Map<Integer, BCTicket> tickets = playerOption.getTickets();
        ItemStack item0 = event.getView().getItem(Menu.mainMenu.ticketSlots.get(0));
        if (tickets.isEmpty() && item0 != null && !item0.getType().equals(Material.BARRIER)) {
            for (Integer ticketSlot : Menu.mainMenu.ticketSlots) {
                event.getView().setItem(ticketSlot, new ItemStack(Material.AIR));
            }
            return;
        }
        for (Map.Entry<Integer, BCTicket> entry: tickets.entrySet()) {
            entry.getValue().updateProperties(playerOption);
            event.getView().setItem(entry.getKey(), entry.getValue().getItem(player));
        }
    }
}

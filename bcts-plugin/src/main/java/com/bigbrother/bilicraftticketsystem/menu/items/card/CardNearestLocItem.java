package com.bigbrother.bilicraftticketsystem.menu.items.card;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuCard;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocationCard;
import com.bigbrother.bilicraftticketsystem.ticket.BCCard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.menu.items.location.NearestLocItem.bcspawnInfoList;

public class CardNearestLocItem extends CardLocationItem {
    private final Player viewer;
    private BcspawnInfo nearestBcspawn = null;

    public CardNearestLocItem(Player viewer, MenuLocationCard fromMenu) {
        super(CommonUtils.loadItemFromFile("nearest"), fromMenu);
        this.viewer = viewer;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        BCCard card = BCCard.fromHeldItem(player);
        if (card == null) {
            player.closeInventory();
            return;
        }

        if (nearestBcspawn != null) {
            MenuCard menu = MenuCard.getMenu(player);
            Component name = CommonUtils.mmStr2Component(MenuConfig.getLocationMenuConfig().get("content.%s.name".formatted(nearestBcspawn.getSpawnStation()), nearestBcspawn.getSpawnStation())).decoration(TextDecoration.ITALIC, false);
            if (fromMenu.isStart()) {
                card.setStartStation(name);
            } else {
                card.setEndStation(name);
            }
            menu.open();
        }
    }

    public void calcNearestStation() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 获取全部bcspawn数据
            if (bcspawnInfoList.isEmpty()) {
                bcspawnInfoList = plugin.getTrainDatabaseManager().getBcspawnService().getAllBcspawnInfo();
            }

            // 计算最近的车站
            Location playerLocation = viewer.getLocation();
            double minDistanceSquared = Double.MAX_VALUE;
            for (BcspawnInfo bcspawnInfo : bcspawnInfoList) {
                if (bcspawnInfo.getWorld().equals(playerLocation.getWorld().getName())) {
                    Location bcspawnLocation = bcspawnInfo.getFixedLocation();
                    if (bcspawnLocation != null) {
                        double distanceSquared = playerLocation.distanceSquared(bcspawnLocation);
                        if (minDistanceSquared > distanceSquared) {
                            minDistanceSquared = distanceSquared;
                            nearestBcspawn = bcspawnInfo;
                        }
                    }
                }
            }

            // 添加lore
            ItemMeta itemMeta = this.itemStack.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (nearestBcspawn != null) {
                lore.add(Component.text("距离最近的车站：%s(%.2fm)，位于(x=%d, z=%d, z=%d)附近"
                        .formatted(nearestBcspawn.getSpawnStation(), Math.sqrt(minDistanceSquared), nearestBcspawn.getCoordX(), nearestBcspawn.getFixedY(), nearestBcspawn.getCoordZ()), NamedTextColor.DARK_AQUA));
            } else {
                lore.add(Component.text("当前世界没有国铁车站", NamedTextColor.RED));
            }
            itemMeta.lore(lore);
            this.itemStack.setItemMeta(itemMeta);

            // 更新
            this.notifyWindows();
        });
    }
}
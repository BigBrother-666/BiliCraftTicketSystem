package com.bigbrother.bilicraftticketsystem.menu.items.location;

import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.database.entity.BcspawnInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuFilter;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public class NearestLocItem extends LocationItem {
    @Getter @Setter
    private static List<BcspawnInfo> bcspawnInfoList = new ArrayList<>();

    private final Player viewer;
    private BcspawnInfo nearestBcspawn = null;

    public NearestLocItem(Player viewer) {
        super(Utils.loadItemFromFile("nearest"));
        this.viewer = viewer;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        if (nearestBcspawn != null) {
            MenuMain menu = MenuMain.getMenu(player);
            Component name = MiniMessage.miniMessage().deserialize(MenuConfig.getLocationMenuConfig().get("content.%s.name".formatted(nearestBcspawn.getSpawnStation()), nearestBcspawn.getSpawnStation())).decoration(TextDecoration.ITALIC, false);
            if (MenuLocation.getMenu(player).isStart()) {
                menu.getPlayerOption().setStartStation(name);
            } else {
                menu.getPlayerOption().setEndStation(name);
            }
            MenuFilter.getMenu(player).getFilterStations().clear();
            menu.clearTickets();
            menu.open();
        }
    }

    public void calcNearestStation() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 获取全部bcspawn数据
            if (bcspawnInfoList.isEmpty()) {
                bcspawnInfoList = plugin.getTrainDatabaseManager().getAllBcspawnInfo();
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
                lore.add(Component.text("距离最近的车站：%s(%.2fm)，位于(x=%d, y=%d, z=%d)附近"
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
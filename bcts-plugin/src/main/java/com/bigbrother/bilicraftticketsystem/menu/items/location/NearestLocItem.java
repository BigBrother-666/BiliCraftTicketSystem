package com.bigbrother.bilicraftticketsystem.menu.items.location;

import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.database.entity.PlatfromInfo;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuLocation;
import com.bigbrother.bilicraftticketsystem.menu.impl.MenuMain;
import com.bigbrother.bilicraftticketsystem.menu.station.StationProvider;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    public static List<PlatfromInfo> platfromInfoList = new ArrayList<>();

    private final Player viewer;
    private PlatfromInfo nearestBcspawn = null;

    public NearestLocItem(Player viewer) {
        super(CommonUtils.loadItemFromFile("nearest"));
        this.viewer = viewer;
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        if (nearestBcspawn != null) {
            MenuMain menu = MenuMain.getMenu(player);
            Component name = StationProvider.stationNameComponent(nearestBcspawn.getStationName());
            if (MenuLocation.getMenu(player).isStart()) {
                menu.getPlayerOption().setStartStation(name);
            } else {
                menu.getPlayerOption().setEndStation(name);
            }
            menu.clearTickets();
            menu.open();
        }
    }

    public void calcNearestStation() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 获取全部bcspawn数据
            if (platfromInfoList.isEmpty()) {
                platfromInfoList = plugin.getTrainDatabaseManager().getBcspawnService().getAllPlatfromInfo();
            }

            // 计算最近的车站
            Location playerLocation = viewer.getLocation();
            double minDistanceSquared = Double.MAX_VALUE;
            for (PlatfromInfo platfromInfo : platfromInfoList) {
                if (platfromInfo.getWorld().equals(playerLocation.getWorld().getName())) {
                    Location bcspawnLocation = platfromInfo.getFixedLocation();
                    if (bcspawnLocation != null) {
                        double distanceSquared = playerLocation.distanceSquared(bcspawnLocation);
                        if (minDistanceSquared > distanceSquared) {
                            minDistanceSquared = distanceSquared;
                            nearestBcspawn = platfromInfo;
                        }
                    }
                }
            }

            // 添加lore
            ItemMeta itemMeta = this.itemStack.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (nearestBcspawn != null) {
                lore.add(Component.text("距离最近的车站：%s(%.2fm)，位于(x=%d, z=%d, z=%d)附近"
                        .formatted(nearestBcspawn.getStationName(), Math.sqrt(minDistanceSquared), nearestBcspawn.getCoordX(), nearestBcspawn.getFixedY(), nearestBcspawn.getCoordZ()), NamedTextColor.DARK_AQUA));
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
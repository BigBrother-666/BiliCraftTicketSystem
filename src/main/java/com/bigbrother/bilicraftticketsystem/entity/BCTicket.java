package com.bigbrother.bilicraftticketsystem.entity;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bigbrother.bilicraftticketsystem.TrainRoutes;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


@AllArgsConstructor
@Getter
public class BCTicket {
    private Ticket ticket;
    private TrainRoutes.PathInfo pathInfo;

    public static BCTicket createTicket(PlayerOption option, TrainRoutes.PathInfo info) {
        Ticket ticket = TicketStore.getTicket(MainConfig.expressTicketName);
        ConfigurationNode properties = new ConfigurationNode();
        properties.set("speedLimit", option.getSpeed());
        ticket.setProperties(properties);
        return new BCTicket(ticket, info);
    }

    public ItemStack getItem(Player player) {
        ItemStack item = this.ticket.createItem(player);
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("起始站 ---> 终到站：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("%s ---直达---> %s".formatted(pathInfo.getStart(), pathInfo.getEnd()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("可使用的站台：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(pathInfo.getStartPlatform(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("路线详情（只停起始站和终到站）：", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        List<String> path = pathInfo.getPath();
        Component join = Component.text("");
        for (int i = 0; i < path.size(); i++) {
            if (i == path.size() - 1) {
                join = join.append(Component.text(path.get(i), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                continue;
            }
            if (i % 7 != 0) {
                join = join.append(Component.text(path.get(i)+ "→", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else if (i == 0) {
                join = join.append(Component.text(path.get(i) + "→", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(path.get(i) + "→", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text("共%.2fkm".formatted(pathInfo.getDistance()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("==========================", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("售价：%.2f银币       左键点击购买".formatted(pathInfo.getPrice()), NamedTextColor.DARK_PURPLE));
        itemMeta.lore(lore);
        item.setItemMeta(itemMeta);
        return item;
    }

    public void giveTo(Player player) {
        ItemStack item = getItem(player);
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = itemMeta.lore();
        if (lore != null && lore.size() > 2) {
            lore.remove(lore.size() - 1);
            lore.remove(lore.size() - 2);
        }
    }
}

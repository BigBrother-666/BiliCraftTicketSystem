package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardSpeedItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardStartEndItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.ChargeItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.Collections;

public class MenuCard extends Menu {
    private final Gui gui;

    public MenuCard(Player viewer) {
        FileConfiguration cardConfig = MenuConfig.getCardMenuConfig();

        Gui.Builder.Normal guiBuilder = Gui.normal().setStructure(cardConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : cardConfig.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length == 2) {
                String itemName;
                if (split[1].startsWith("item-")) {
                    itemName = split[1].replaceFirst("item-", "");
                } else {
                    itemName = split[1];
                }
                switch (itemName) {
                    case "start":
                        guiBuilder.addIngredient(split[0].charAt(0), new CardStartEndItem(true));
                        break;
                    case "end":
                        guiBuilder.addIngredient(split[0].charAt(0), new CardStartEndItem(false));
                        break;
                    case "speed":
                        guiBuilder.addIngredient(split[0].charAt(0), new CardSpeedItem());
                        break;
                    case "charge":
                        guiBuilder.addIngredient(split[0].charAt(0), new ChargeItem());
                        break;
                    default:
                        try {
                            guiBuilder.addIngredient(split[0].charAt(0), new SimpleItem(new ItemBuilder(Material.valueOf(itemName))));
                        } catch (IllegalArgumentException e) {
                            guiBuilder.addIngredient(split[0].charAt(0), new ItemBuilder(Utils.loadItemFromFile(itemName)));
                        }
                }
            }
        }

        gui = guiBuilder.build();
        this.window = Window.single()
                .setViewer(viewer)
                .setTitle(new AdventureComponentWrapper(Component.text(cardConfig.get("title", String.class, ""))))
                .setGui(gui)
                .build();
    }

    /**
     * 获取公交卡菜单对象
     * 注意：从缓存中取时，需要更新commonItemStack，因为BKCommonLib每次更新地图都会新建一个commonItemStack
     */
    public static MenuCard getMenu(Player player) {
        return new MenuCard(player);
    }
}

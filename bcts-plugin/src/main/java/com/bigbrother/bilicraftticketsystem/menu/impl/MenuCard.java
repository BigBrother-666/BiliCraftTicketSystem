package com.bigbrother.bilicraftticketsystem.menu.impl;

import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.config.MenuConfig;
import com.bigbrother.bilicraftticketsystem.menu.Menu;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardSpeedItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.CardStartEndItem;
import com.bigbrother.bilicraftticketsystem.menu.items.card.ChargeItem;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;

public class MenuCard extends Menu {
    @SuppressWarnings("FieldCanBeLocal")
    private final Gui gui;

    public MenuCard(Player viewer) {
        FileConfiguration cardConfig = MenuConfig.getCardMenuConfig();

        gui = Gui.normal()
                .setStructure(buildStructure(cardConfig, itemName -> switch (itemName) {
                    case "start" -> new CardStartEndItem(true);
                    case "end" -> new CardStartEndItem(false);
                    case "speed" -> new CardSpeedItem();
                    case "charge" -> new ChargeItem();
                    default -> null;
                }))
                .build();
        this.window = buildSingleWindow(viewer, cardConfig, gui);
    }

    /**
     * 获取公交卡菜单对象
     * 注意：从缓存中取时，需要更新commonItemStack，因为BKCommonLib每次更新地图都会新建一个commonItemStack
     */
    public static MenuCard getMenu(Player player) {
        return new MenuCard(player);
    }
}

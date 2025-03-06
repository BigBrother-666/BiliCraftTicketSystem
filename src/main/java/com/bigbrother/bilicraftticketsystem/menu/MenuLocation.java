package com.bigbrother.bilicraftticketsystem.menu;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.Utils;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import com.bigbrother.bilicraftticketsystem.menu.items.LocationItem;
import com.bigbrother.bilicraftticketsystem.menu.items.ScrollDownItem;
import com.bigbrother.bilicraftticketsystem.menu.items.ScrollUpItem;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.ScrollGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.*;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;
import static com.bigbrother.bilicraftticketsystem.Utils.loadItemFromFile;


public class MenuLocation implements Menu {
    @Getter
    private static final Map<UUID, MenuLocation> locationMenuMapping = new HashMap<>();

    private final Window window;

    @Getter
    @Setter
    private boolean isStart;

    private MenuLocation(Player player, boolean isStart) {
        this.isStart = isStart;

        FileConfiguration locationConfig = new FileConfiguration(plugin, EnumConfig.MENU_LOCATION.getFileName());
        locationConfig.load();

        ScrollGui.@NotNull Builder<@NotNull Item> guiBuilder = ScrollGui.items()
                .setStructure(locationConfig.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));

        // 设置映射
        for (String mapping : locationConfig.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length == 2) {
                String itemName;
                if (split[1].startsWith("item-")) {
                    itemName = split[1].replaceFirst("item-", "");
                } else {
                    itemName = split[1];
                }
                switch (itemName) {
                    case "content":
                        guiBuilder.addIngredient(split[0].charAt(0), Markers.CONTENT_LIST_SLOT_HORIZONTAL);
                        break;
                    case "scrollup":
                        guiBuilder.addIngredient(split[0].charAt(0), new ScrollUpItem());
                        break;
                    case "scrolldown":
                        guiBuilder.addIngredient(split[0].charAt(0), new ScrollDownItem());
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

        // 添加物品
        ConfigurationNode contents = locationConfig.getNode("content");
        for (Map.Entry<String, Object> entry : contents.getValues().entrySet()) {
            ConfigurationNode item = contents.getNode(entry.getKey());

            // 设置物品信息
            String material = item.get("material", "");
            ItemStack customItem;
            if (material.startsWith("item-")) {
                customItem = loadItemFromFile(material.replaceFirst("item-", "").trim());
            } else {
                customItem = new ItemStack(Material.valueOf(item.get("material", "").trim()));
            }
            ItemMeta itemMeta = customItem.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String s : item.getList("lore", String.class, Collections.emptyList())) {
                lore.add(MiniMessage.miniMessage().deserialize(s).decoration(TextDecoration.ITALIC, false));
            }
            itemMeta.lore(lore);
            itemMeta.displayName(MiniMessage.miniMessage().deserialize(item.get("name", String.class, "")).decoration(TextDecoration.ITALIC, false));
            customItem.setItemMeta(itemMeta);

            // 添加物品
            guiBuilder.addContent(new LocationItem(customItem));
        }

        this.window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(Component.text(locationConfig.get("title", String.class, ""))))
                .setGui(guiBuilder.build())
                .build();

        locationMenuMapping.put(player.getUniqueId(), this);
    }

    @Override
    public void open() {
        if (window.isOpen()) {
            close();
            return;
        }
        this.window.open();
    }

    @Override
    public void close() {
        Bukkit.getScheduler().runTask(plugin, window::close);
    }

    public static void clearAll() {
        for (Map.Entry<UUID, MenuLocation> entry : locationMenuMapping.entrySet()) {
            entry.getValue().close();
        }
        locationMenuMapping.clear();
    }

    public static MenuLocation getMenu(Player player, Boolean isStart) {
        if (locationMenuMapping.containsKey(player.getUniqueId())) {
            MenuLocation menuLocation = locationMenuMapping.get(player.getUniqueId());
            if (isStart != null) {
                menuLocation.setStart(isStart);
            }
            return menuLocation;
        } else {
            return new MenuLocation(player, isStart);
        }
    }
}

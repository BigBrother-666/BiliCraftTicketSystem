package com.bigbrother.bilicraftticketsystem.menu;


import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.menu.impl.*;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.structure.Marker;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

public abstract class Menu {
    protected Window window;

    /**
     * 把一个 {@code mapping} 中的物品名解析为界面结构的填充元素。
     * <p>
     * 各菜单子类只需实现本接口处理自己关心的物品名（如 start/search/content...），返回
     * {@code null} 时由 {@link #buildStructure} 走统一的默认回退（bukkit 物品枚举名或
     * {@code item-自定义物品名} 装饰物品），从而消除每个子类重复的 mapping 解析循环。
     */
    @FunctionalInterface
    public interface IngredientResolver {
        /**
         * @param itemName 物品名（已剥离 {@code item-} 前缀）
         * @return 结构填充元素（{@link xyz.xenondevs.invui.item.Item}、
         * {@link xyz.xenondevs.invui.gui.structure.Marker}、
         * {@link xyz.xenondevs.invui.item.ItemProvider} 等 {@link Structure#addIngredient} 支持的类型）；
         * 返回 null 表示交给默认装饰物品回退
         */
        Object resolve(String itemName);
    }

    /**
     * 按配置的 {@code structure} + {@code mapping} 构建界面结构，统一处理 {@code item-} 前缀剥离、
     * 自定义物品名分派与默认装饰物品回退。
     * <p>
     * 取代各子类内重复的 mapping 解析循环：子类传入 resolver 处理各自的功能物品即可。
     *
     * @param config   菜单配置（含 structure / mapping）
     * @param resolver 物品名解析器（返回 null 走默认回退）
     * @return 已填充好的界面结构，供各 GUI builder 的 {@code setStructure} 使用
     */
    protected static Structure buildStructure(FileConfiguration config, IngredientResolver resolver) {
        Structure structure = new Structure(
                config.getList("structure", String.class, Collections.emptyList()).toArray(new String[0]));
        for (String mapping : config.getList("mapping", String.class, Collections.emptyList())) {
            String[] split = mapping.split(" ");
            if (split.length != 2) {
                continue;
            }
            char key = split[0].charAt(0);
            String itemName = split[1].startsWith("item-") ? split[1].replaceFirst("item-", "") : split[1];

            Object ingredient = resolver == null ? null : resolver.resolve(itemName);
            addIngredient(structure, key, Objects.requireNonNullElseGet(ingredient, () -> defaultIngredient(itemName)));
        }
        return structure;
    }

    /**
     * 默认装饰物品回退：物品名是 bukkit 物品枚举名则用该材质，否则当作 {@code menuitems.yml} 自定义物品名加载。
     *
     * @param itemName 物品名
     * @return 装饰用 {@link SimpleItem}
     */
    private static SimpleItem defaultIngredient(String itemName) {
        try {
            return new SimpleItem(new ItemBuilder(Material.valueOf(itemName)));
        } catch (IllegalArgumentException e) {
            return new SimpleItem(new ItemBuilder(CommonUtils.loadItemFromFile(itemName)));
        }
    }

    /**
     * 按 {@code ingredient} 的实际类型分派到对应的 {@link Structure#addIngredient} 重载。
     */
    private static void addIngredient(Structure structure, char key, Object ingredient) {
        switch (ingredient) {
            case Marker marker -> structure.addIngredient(key, marker);
            case Item item -> structure.addIngredient(key, item);
            case ItemProvider provider -> structure.addIngredient(key, provider);
            case ItemStack itemStack -> structure.addIngredient(key, itemStack);
            default -> throw new IllegalArgumentException("不支持的界面结构填充类型：" + ingredient.getClass());
        }
    }

    /**
     * 解析菜单配置的标题为 invui 可用的标题包装（同时支持 MiniMessage 与 legacy &amp; 代码）。
     *
     * @param config 菜单配置
     * @return 标题包装
     */
    protected static AdventureComponentWrapper title(FileConfiguration config) {
        return new AdventureComponentWrapper(CommonUtils.mmStr2Component(config.get("title", String.class, "")));
    }

    /**
     * 解析菜单标题并替换其中的占位符（如 {@code {railway_system}}）。
     * <p>
     * 用 {@link PlaceholderParser#parse} 解析模板（同时支持占位符、MiniMessage 与 legacy 代码），
     * 取首行作为标题。供需要在标题里展示动态内容的菜单使用。
     *
     * @param config       菜单配置（取标题模板）
     * @param placeholders 占位符键值
     * @return 标题包装
     */
    protected static AdventureComponentWrapper title(FileConfiguration config, Map<String, Object> placeholders) {
        String template = config.get("title", String.class, "");
        List<Component> parsed = PlaceholderParser.parse(List.of(template), placeholders);
        Component title = parsed.isEmpty() ? Component.empty() : parsed.getFirst();
        return new AdventureComponentWrapper(title);
    }

    /**
     * 以占位符重算标题并应用到已构建的窗口（窗口在构造时不知道占位符值，打开前调用本方法刷新标题）。
     *
     * @param config       菜单配置（取标题模板）
     * @param placeholders 占位符键值
     */
    protected void applyTitle(FileConfiguration config, Map<String, Object> placeholders) {
        if (window != null) {
            window.changeTitle(title(config, placeholders));
        }
    }

    /**
     * 用统一参数构建一个普通单窗口（viewer + 标题 + gui）。
     *
     * @param player 查看者
     * @param config 菜单配置（取标题）
     * @param gui    界面
     * @return 构建好的窗口
     */
    protected static Window buildSingleWindow(Player player, FileConfiguration config, Gui gui) {
        return Window.single()
                .setViewer(player)
                .setTitle(title(config))
                .setGui(gui)
                .build();
    }

    public void open() {
        if (Bukkit.isPrimaryThread()) {
            if (this.window.isOpen()) {
                close();
            }
            this.window.open();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (this.window.isOpen()) {
                    close();
                }
                this.window.open();
            });
        }
    }

    public void close() {
        if (Bukkit.isPrimaryThread()) {
            this.window.close();
        } else {
            Bukkit.getScheduler().runTask(plugin, this.window::close);
        }
    }

    public static void reloadAll() {
        MenuMain.reload();
        MenuLocation.reload();
        MenuTicketbg.reload();
    }

    public static void clearPlayerCache(UUID uuid) {
        MenuMain.getMainMenuMapping().remove(uuid);
        MenuLocation.getLocationMenuMapping().remove(uuid);
        MenuTicketbg.getTicketbgMenuMapping().remove(uuid);
    }
}

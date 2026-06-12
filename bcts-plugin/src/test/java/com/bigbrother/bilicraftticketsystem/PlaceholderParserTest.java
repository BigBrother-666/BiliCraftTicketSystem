package com.bigbrother.bilicraftticketsystem;

import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一配置文本解析器测试：legacy &amp; 代码 ↔ MiniMessage 转换、两者混用、纯文本透传。
 */
public class PlaceholderParserTest {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void legacyCodeConvertsToMiniMessageTag() {
        assertEquals("<gold>金<red>红", PlaceholderParser.legacyToMiniMessage("&6金&c红"));
    }

    @Test
    void sectionSignConvertsToMiniMessageTag() {
        // Bukkit § 颜色符也按 legacy 处理（线路名等既有上色文本）
        assertEquals("欢迎乘坐<gold>环线（顺时针方向）",
                PlaceholderParser.legacyToMiniMessage("欢迎乘坐§6环线（顺时针方向）"));
    }

    @Test
    void sectionSignRendersInComponent() {
        // 复现报错场景：§6 不再抛 MiniMessage 解析异常
        Component c = PlaceholderParser.toComponent("欢迎乘坐§6环线（顺时针方向）");
        assertEquals("欢迎乘坐环线（顺时针方向）", plain(c));
    }

    @Test
    void legacyHexConvertsToMiniMessageHex() {
        assertEquals("<#AA00FF>紫", PlaceholderParser.legacyToMiniMessage("&#AA00FF紫"));
    }

    @Test
    void existingMiniMessageTagsLeftIntact() {
        // 没有 & 代码时原样返回
        assertEquals("<gold>金<bold>粗", PlaceholderParser.legacyToMiniMessage("<gold>金<bold>粗"));
    }

    @Test
    void mixedLegacyAndMiniMessage() {
        // 一个字符串里混用两种写法
        assertEquals("<gold>金<red>红", PlaceholderParser.legacyToMiniMessage("&6金<red>红"));
    }

    @Test
    void strayAmpersandKept() {
        assertEquals("A & B", PlaceholderParser.legacyToMiniMessage("A & B"));
        // & 后非法代码字符也保留
        assertEquals("Tom & Jerry", PlaceholderParser.legacyToMiniMessage("Tom & Jerry"));
    }

    @Test
    void incompleteHexFallsBackToStray() {
        // 不足 6 位十六进制不当作 hex
        assertEquals("&#AA0 short", PlaceholderParser.legacyToMiniMessage("&#AA0 short"));
    }

    @Test
    void toComponentRendersLegacyColor() {
        Component c = PlaceholderParser.toComponent("&c红色");
        assertEquals("红色", plain(c));
        assertEquals(NamedTextColor.RED, c.children().isEmpty() ? c.color() : c.children().get(0).color());
    }

    @Test
    void toComponentRendersHexColor() {
        Component c = PlaceholderParser.toComponent("&#AA00FF紫");
        assertEquals("紫", plain(c));
        TextColor color = c.children().isEmpty() ? c.color() : c.children().get(0).color();
        assertEquals(TextColor.fromHexString("#AA00FF"), color);
    }

    @Test
    void toComponentRendersMixed() {
        Component c = PlaceholderParser.toComponent("&6已到达 <red>终点");
        assertEquals("已到达 终点", plain(c));
    }

    @Test
    void toComponentNullSafe() {
        assertEquals("", plain(PlaceholderParser.toComponent(null)));
    }

    @Test
    void plainTextPassesThrough() {
        assertEquals("纯文本 100% off", plain(PlaceholderParser.toComponent("纯文本 100% off")));
    }

    @Test
    void legacyArrivalNoticeStyleWorks() {
        // routes.yml bossbar-arrival-notice 实际写法
        Component c = PlaceholderParser.toComponent("&6列车已到达 &cStation A站");
        assertEquals("列车已到达 Station A站", plain(c));
    }

    @Test
    void parseAppliesPlaceholderAndBothColorFormats() {
        // bossbar arrivalNotice 走 parse：占位符替换 + 双格式颜色
        var result = PlaceholderParser.parse(
                java.util.List.of("&6列车已到达 <red>{curr_station}站"),
                java.util.Map.of("curr_station", "Station A"));
        assertEquals(1, result.size());
        assertEquals("列车已到达 Station A站", plain(result.get(0)));
    }
}

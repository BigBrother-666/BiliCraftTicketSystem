package com.bigbrother.bilicraftticketsystem;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：BKCommonLib 节点（含新版物品「数据组件」格式的嵌套 components 块）经
 * {@link CommonUtils#deepToPlainMap} 转换后，所有嵌套结构都必须是纯 {@link java.util.Map}。
 * <p>
 * 复现的线上报错：{@code java.lang.IllegalArgumentException: components must be a Map} —
 * Paper 1.21+ 反序列化新版物品时断言 components instanceof Map，而 BKCommonLib 的
 * getValues() 嵌套子树是 ConfigurationNode（未实现 Map），故需先深度转换。
 */
public class ItemDeserializeTest {

    /**
     * 未经转换时，BKCommonLib 的嵌套节点不是 java.util.Map —— 即线上崩溃的根因。
     */
    @Test
    void rawNestedNodeIsNotAMap() {
        ConfigurationNode node = new ConfigurationNode();
        node.set("components.minecraft:custom_name", "返回主界面");
        Object rawComponents = node.getValues().get("components");
        assertNotNull(rawComponents);
        assertFalse(rawComponents instanceof Map,
                "前置条件：BKCommonLib 嵌套节点本不是 java.util.Map（正是 Paper 报错的原因）");
    }

    /**
     * 转换后，新版物品格式的 components 块必须是纯 Map，且内容完整保留。
     */
    @Test
    void deepConvertsNewFormatComponents() {
        ConfigurationNode node = new ConfigurationNode();
        node.set("id", "minecraft:player_head");
        node.set("count", 1);
        node.set("schema_version", 1);
        node.set("components.minecraft:custom_name", "返回主界面");
        node.set("components.minecraft:profile.name", "HeadDatabase");

        Map<String, Object> plain = CommonUtils.deepToPlainMap(node.getValues());

        assertEquals("minecraft:player_head", plain.get("id"));
        Object components = plain.get("components");
        assertInstanceOf(Map.class, components, "components 必须是纯 java.util.Map");

        Map<?, ?> compMap = (Map<?, ?>) components;
        assertEquals("返回主界面", compMap.get("minecraft:custom_name"));
        assertInstanceOf(Map.class, compMap.get("minecraft:profile"),
                "深层嵌套（profile）也必须是纯 Map");
    }

    /**
     * 旧版格式（v/type/meta）同样能被无损深度转换。
     */
    @Test
    void deepConvertsLegacyFormat() {
        ConfigurationNode node = new ConfigurationNode();
        node.set("v", 3700);
        node.set("type", "PLAYER_HEAD");
        node.set("meta.==", "ItemMeta");
        node.set("meta.meta-type", "SKULL");

        Map<String, Object> plain = CommonUtils.deepToPlainMap(node.getValues());

        assertEquals("PLAYER_HEAD", plain.get("type"));
        assertInstanceOf(Map.class, plain.get("meta"));
        assertEquals("ItemMeta", ((Map<?, ?>) plain.get("meta")).get("=="));
    }

    /**
     * 转换结果中不得残留任何 BKCommonLib 节点类型（递归彻底）。
     */
    @Test
    void noNodeTypesRemain() {
        ConfigurationNode node = new ConfigurationNode();
        node.set("components.minecraft:custom_name", "x");
        node.set("components.minecraft:lore.deep.deeper", "y");

        Map<String, Object> plain = CommonUtils.deepToPlainMap(node.getValues());
        assertNoNodes(plain);
    }

    private static void assertNoNodes(Object value) {
        assertFalse(value instanceof ConfigurationNode, "不应残留 ConfigurationNode");
        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                assertNoNodes(v);
            }
        } else if (value instanceof List<?> list) {
            for (Object v : list) {
                assertNoNodes(v);
            }
        }
    }
}

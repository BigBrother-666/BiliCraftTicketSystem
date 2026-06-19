package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.Optional;

/**
 * 列车导航属性：上一次推进指针（{@link BcRouteIndexProperty}）时所在的节点 id。
 * <p>
 * 用于<b>按节点去重推进</b>：同一个铁轨方块上可能挂多个控制牌（如一个铁轨对应多个 bcswitcher，
 * 或道岔牌与车站牌共块），它们会在列车经过时各自触发一次 GROUP_ENTER/LEAVE，导致
 * {@link BcRouteNavigator#advance} 被多次调用、指针越推越快。记录上次推进的节点 id 后，
 * {@link BcRouteNavigator#advance(com.bergerkiller.bukkit.tc.controller.MinecartGroup, String)}
 * 只在节点 id 与上次不同时才推进，保证「一个物理节点只推进一格」。
 * <p>
 * 环线安全：两次经过同一节点之间，列车必然经过其它会推进的节点，会覆盖本值，故不会误判去重。
 * 存为 TrainCarts 的 train property，随存档持久化。
 */
public class BcLastAdvanceNodeProperty implements ITrainProperty<String> {
    /**
     * 单例（注册与读写须为同一实例）。
     */
    public static final BcLastAdvanceNodeProperty INSTANCE = new BcLastAdvanceNodeProperty();

    /**
     * 在 TrainCarts 配置中的键名。
     */
    private static final String KEY = "bcLastAdvanceNode";

    private static final String DEFAULT = "";

    @Override
    public String getDefault() {
        return DEFAULT;
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, KEY, String.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        Util.setConfigOptional(config, KEY, value);
    }
}

package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.Optional;

/**
 * 列车导航属性：当前已推进到的道岔下标，配合 {@link BcRouteProperty} 使用。
 * <p>
 * 指向 {@link BcRouteProperty} 序列中「当前该走的 lineId」。列车每经过一个 bcswitcher 推进一格。
 * 存为 TrainCarts 的 train property，随存档持久化。
 */
public class BcRouteIndexProperty implements ITrainProperty<Integer> {
    /**
     * 单例（注册与读写须为同一实例）。
     */
    public static final BcRouteIndexProperty INSTANCE = new BcRouteIndexProperty();

    /**
     * 在 TrainCarts 配置中的键名。
     */
    private static final String KEY = "bcRouteIndex";

    private static final Integer DEFAULT = 0;

    @Override
    public Integer getDefault() {
        return DEFAULT;
    }

    @Override
    public Optional<Integer> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, KEY, int.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
        Util.setConfigOptional(config, KEY, value);
    }
}

package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 列车导航属性：按经过顺序排列的「每道岔应选的 lineId」序列（有序、不去重）。
 * <p>
 * 由 {@link com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath#switcherLineIds()} 导出，
 * 列车每经过一个 bcswitcher 推进一格（配合 {@link BcRouteIndexProperty}），bcswitcher 据当前
 * lineId 选向。存为 TrainCarts 的 train property，TC 自动随存档持久化，服务器重启 / 插件重载后恢复。
 * <p>
 * 不用 TC 自带的 tag 集合：tag 是无序 Set，无法表达「先走 L1，再走 contact，再回到 L1」这种
 * 有序且可重复的导航序列。
 */
public class BcRouteProperty implements ITrainProperty<List<String>> {
    /**
     * 单例（注册与读写须为同一实例）。
     */
    public static final BcRouteProperty INSTANCE = new BcRouteProperty();

    /**
     * 在 TrainCarts 配置中的键名。
     */
    private static final String KEY = "bcRoute";

    @Override
    public List<String> getDefault() {
        return Collections.emptyList();
    }

    @Override
    public Optional<List<String>> readFromConfig(ConfigurationNode config) {
        if (config.contains(KEY)) {
            return Optional.of(Collections.unmodifiableList(
                    new ArrayList<>(config.getList(KEY, String.class))));
        }
        return Optional.empty();
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<List<String>> value) {
        if (value.isPresent()) {
            config.set(KEY, value.get());
        } else {
            config.remove(KEY);
        }
    }
}

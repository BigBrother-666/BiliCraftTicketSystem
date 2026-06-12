package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.Optional;

/**
 * 列车导航属性：列车的起点车站名（在哪个车站发车 / 上车）。
 * <p>
 * 新模型下列车不再用 platform-tag tag 标识起点站台，也不用铁轨节点 id（发车牌与站台牌位于
 * 不同铁轨方块，节点 id 无法对齐）。bcspawn 控制牌发车时把第三行解析出的车站名写入本属性，供：
 * <ul>
 *   <li>车票 verify：比对列车起点站名 + 列车所属 lineId 与车票路径的起点站名 + 营运线 lineId；</li>
 *   <li>交通卡「任意站台上车」：以列车起点站名为起点 findByStation 到终点站。</li>
 * </ul>
 * 存为 TrainCarts train property，随存档持久化。
 */
public class BcStartNodeProperty implements ITrainProperty<String> {
    /**
     * 单例（注册与读写须为同一实例）。
     */
    public static final BcStartNodeProperty INSTANCE = new BcStartNodeProperty();

    /**
     * 在 TrainCarts 配置中的键名。
     */
    private static final String KEY = "bcStartStation";

    @Override
    public String getDefault() {
        return "";
    }

    @Override
    public Optional<String> readFromConfig(ConfigurationNode config) {
        if (config.contains(KEY)) {
            return Optional.ofNullable(config.get(KEY, String.class));
        }
        return Optional.empty();
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<String> value) {
        if (value.isPresent()) {
            config.set(KEY, value.get());
        } else {
            config.remove(KEY);
        }
    }

    /**
     * 读取列车起点车站名。
     *
     * @param group 列车
     * @return 起点车站名；未设置返回空串
     */
    public static String read(MinecartGroup group) {
        if (group == null) {
            return "";
        }
        return group.getProperties().get(INSTANCE);
    }

    /**
     * 写入列车起点车站名。
     *
     * @param group   列车
     * @param station 起点车站名
     */
    public static void write(MinecartGroup group, String station) {
        if (group == null) {
            return;
        }
        group.getProperties().set(INSTANCE, station);
    }
}

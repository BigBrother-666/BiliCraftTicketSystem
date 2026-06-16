package com.bigbrother.bilicraftticketsystem.route.geograph.nav;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;

import java.util.Optional;

/**
 * 列车所属营运线路 id 属性。
 * <p>
 * 旧模型把 lineId 作为 tag 加在列车上（{@code addTags(lineId)}），但 tag 可被玩家用 TrainCarts
 * 指令随意改动，不安全。改为存为 TrainCarts train property（随存档持久化、玩家无法用 tag 指令篡改），
 * 由 bcspawn 控制牌发车时写入，供：
 * <ul>
 *   <li>platform 控制牌：据此识别线路播报进出站提示、构建普通车 bossbar；</li>
 *   <li>bcswitcher 控制牌：普通车（无导航序列）据此选道岔分支；</li>
 *   <li>车票 / 交通卡 verify：比对列车所属营运线 lineId；</li>
 *   <li>运行时转线：普通车到达本线终点站后改写本属性，转入下一条线路。</li>
 * </ul>
 */
public class BcLineIdProperty implements ITrainProperty<String> {
    /**
     * 单例（注册与读写须为同一实例）。
     */
    public static final BcLineIdProperty INSTANCE = new BcLineIdProperty();

    /**
     * 在 TrainCarts 配置中的键名。
     */
    private static final String KEY = "bcLineId";

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
     * 读取列车所属营运线路 id。
     *
     * @param group 列车
     * @return 线路 id；未设置返回空串
     */
    public static String read(MinecartGroup group) {
        if (group == null) {
            return "";
        }
        return group.getProperties().get(INSTANCE);
    }

    /**
     * 写入列车所属营运线路 id。
     *
     * @param group  列车
     * @param lineId 线路 id
     */
    public static void write(MinecartGroup group, String lineId) {
        if (group == null) {
            return;
        }
        group.getProperties().set(INSTANCE, lineId == null ? "" : lineId);
    }
}

package com.bigbrother.bilicraftticketsystem.signactions.component;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;

/**
 * 运行时转线的公共逻辑：解析「转线目标」写法，以及转线后重建普通车 bossbar。
 * <p>
 * 转线目标的写法与 {@code railway_routes.yml} 中 {@code bossbar-stations} 末项的转线配置<b>完全一致</b>：
 * {@code <线路id>} 或 {@code <线路id>:<进入站名>}。冒号后为转线后的<b>进入站名</b>（即转线后的下一站，
 * 可跳过目标线路靠前的车站）；不写冒号表示不指定进入站。
 * <p>
 * 被两处使用：
 * <ul>
 *   <li>{@link com.bigbrother.bilicraftticketsystem.signactions.SignActionPlatform}：普通车离开本线
 *       终点站时按线路配置自动转线；</li>
 *   <li>{@link com.bigbrother.bilicraftticketsystem.signactions.SignActionSwitchLine}：switchline 控制牌
 *       收到红石信号时按牌面第三行手动转线。</li>
 * </ul>
 */
public final class LineTransfer {

    private LineTransfer() {
    }

    /**
     * 一个转线目标：目标线路 id + 转线后的进入站名。
     *
     * @param lineId       目标线路 id（非空）
     * @param entryStation 转线后在目标线路上的进入站名（下一站）；未指定时为空串，不为 null
     */
    public record Target(String lineId, String entryStation) {
    }

    /**
     * 解析转线目标写法 {@code <线路id>[:<进入站名>]}。
     * <p>
     * 与 {@code railway_routes.yml} 的转线配置同格式。<b>只校验写法、不校验线路是否存在</b>
     * （建牌时线路可能尚未配置），线路存在性由调用方按需检查。
     *
     * @param text 牌面文本（如 {@code pr-s2:S2-B}）
     * @return 解析出的转线目标；文本为空、或冒号前的线路 id 为空时返回 null
     */
    public static Target parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int sep = trimmed.indexOf(':');
        String lineId = sep >= 0 ? trimmed.substring(0, sep).trim() : trimmed;
        String entryStation = sep >= 0 ? trimmed.substring(sep + 1).trim() : "";
        if (lineId.isEmpty()) {
            return null;
        }
        return new Target(lineId, entryStation);
    }

    /**
     * 转线后重建普通车 bossbar：移除旧线 bossbar，按列车<b>当前</b>所属线路（须已写好新 lineId）
     * 懒创建并定位到进入站（{@link CommonRouteBossbar#approach}）。
     * <p>
     * 列车尚未物理到达进入站，故只刷新显示、不发到站提示。直达车的 bossbar 与整条导航路线绑定，
     * 不走本方法重建（调用方只在普通车转线时调用）。
     *
     * @param group        列车（其 {@link BcLineIdProperty} 应已更新为新线路）
     * @param entryStation 转线后在新线的进入站名（可为空串，表示定位到首站）
     * @param bbEnabled    是否允许创建 bossbar（platform 按本站 BOSSBAR 功能位传入）
     */
    public static void rebuildBossbar(MinecartGroup group, String entryStation, boolean bbEnabled) {
        if (group == null) {
            return;
        }
        LineInfo line = LineConfig.get(BcLineIdProperty.read(group));
        for (MinecartMember<?> member : group) {
            // 移除旧线 bossbar（换乘到别的线路必重建）
            if (BossbarManager.get(member) != null) {
                BossbarManager.remove(member);
            }
            if (bbEnabled && line != null) {
                CommonRouteBossbar common = new CommonRouteBossbar(line);
                if (common.getBossBar() != null) {
                    BossbarManager.put(member, common);
                    common.approach(entryStation);
                }
            }
        }
    }
}

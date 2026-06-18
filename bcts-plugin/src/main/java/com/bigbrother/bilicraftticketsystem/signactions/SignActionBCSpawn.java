package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.signactions.TrainCartsSignAction;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcStartNodeProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.lang.reflect.Field;
import java.util.logging.Level;

import static com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem.plugin;

/**
 * bcspawn 发车控制牌。
 * <p>
 * 控制牌格式：
 * <pre>
 *   [+train]
 *   bcspawn &lt;格式同 traincarts 的 spawn&gt;
 *   &lt;线路 id&gt; &lt;当前车站名&gt;
 *   &lt;格式同 traincarts 的 spawn&gt;
 * </pre>
 * 功能：
 * <ul>
 *   <li>发车（继承 traincarts 的 spawn 行为）。</li>
 *   <li>标记列车所属线路：把第三行的线路 id 作为 tag 加到列车上，供 bcswitcher
 *       在道岔处按线路选向、platform 按线路播放进出站提示。</li>
 *   <li>记录发车数据到数据库。</li>
 *   <li>一个站台可放置多个发车控制牌。</li>
 * </ul>
 * 第三行格式 {@code <线路id> <车站名>}：第一个空格前为线路 id，其余为车站名。
 */
public class SignActionBCSpawn extends TrainCartsSignAction {

    public SignActionBCSpawn() {
        super("bcspawn");
    }

    /**
     * 解析第三行，返回 [线路id, 车站名]。
     *
     * @param line3 控制牌第三行
     * @return 长度为 2 的数组 [lineId, station]，缺失部分为空串
     */
    private String[] parseLine3(String line3) {
        String trimmed = line3 == null ? "" : line3.trim();
        int idx = trimmed.indexOf(' ');
        if (idx < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim()};
    }

    @Override
    public void execute(SignActionEvent info) {
        String[] parsed = parseLine3(info.getLine(2));
        String lineId = parsed[0];
        String station = parsed[1];

        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }

        SpawnSign sign = info.getTrainCarts().getSpawnSignManager().create(info);
        if (sign.isActive()) {
            // TrainCarts 的 SpawnSign 把第三、四行拼接当作矿车类型解析（spawnFormat = line3 + line4）。
            // bcspawn 第三行是 <线路id> <车站名>，会被误当成矿车类型，所以只用第四行生成矿车。
            overrideSpawnFormatToLine4(sign, info.getLine(3));
            sign.spawn(info);
            sign.resetSpawnTime();
            MinecartGroup group = info.getGroup();
            if (group != null) {
                // 标记列车所属线路：写入 train property（不再用 tag，避免玩家用 TC 指令篡改）
                String lineName = "";
                if (!lineId.isEmpty()) {
                    BcLineIdProperty.write(group, lineId);
                    LineInfo lineInfo = LineConfig.get(lineId);
                    if (lineInfo != null) {
                        lineName = lineInfo.getLineName();
                    }
                }
                // 记录列车起点车站名（新模型据此做车票 verify / 交通卡任意站台上车）
                if (!station.isEmpty()) {
                    BcStartNodeProperty.write(group, station);
                }
                // 添加ticket
                group.getProperties().addTicket(MainConfig.expressTicketName);
                // 发车信息记录到数据库（线路 id + 线路名，便于直接查看）
                plugin.getTrainDatabaseManager().getBcspawnService().recordSpawn(station, lineId, lineName);
            }
        }
    }

    /** 缓存的 SpawnSign.spawnFormat 字段，null 表示尚未解析或解析失败。 */
    private static Field spawnFormatField;
    private static boolean spawnFormatFieldResolved;

    /**
     * 把 SpawnSign 的私有字段 {@code spawnFormat} 覆写为仅第四行内容，
     * 使其生成矿车时不把第三行的 {@code <线路id> <车站名>} 误当成矿车类型。
     * <p>
     * TrainCarts 的 {@code SpawnSign.updateState} 会把 {@code line3 + line4} 作为
     * spawnFormat，这对原版 spawn 控制牌成立，但 bcspawn 占用了第三行。这里通过反射
     * 修正。若 TrainCarts 内部实现变化导致字段缺失，记录警告并回退（不修正，行为同修复前）。
     *
     * @param sign  本次发车使用的 SpawnSign
     * @param line4 控制牌第四行（矿车格式）
     */
    private static void overrideSpawnFormatToLine4(SpawnSign sign, String line4) {
        Field field = resolveSpawnFormatField();
        if (field == null) {
            return;
        }
        try {
            field.set(sign, line4 == null ? "" : line4);
        } catch (IllegalAccessException e) {
            plugin.getLogger().log(Level.WARNING,
                    "无法覆写 SpawnSign.spawnFormat，bcspawn 可能把第三行误当矿车类型", e);
        }
    }

    private static synchronized Field resolveSpawnFormatField() {
        if (!spawnFormatFieldResolved) {
            spawnFormatFieldResolved = true;
            try {
                Field field = SpawnSign.class.getDeclaredField("spawnFormat");
                field.setAccessible(true);
                spawnFormatField = field;
            } catch (NoSuchFieldException | SecurityException e) {
                plugin.getLogger().log(Level.WARNING,
                        "找不到 SpawnSign.spawnFormat 字段（TrainCarts 版本可能已变更），"
                                + "bcspawn 第三行将仍被当作矿车类型解析", e);
            }
        }
        return spawnFormatField;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        // 检查权限和格式
        if (!event.getPlayer().hasPermission("bcts.buildsign.bcspawn")) {
            return false;
        }
        String[] parsed = parseLine3(event.getLine(2));
        if (parsed[0].isEmpty() || parsed[1].isEmpty()) {
            event.getPlayer().sendMessage(Component.text(
                    "bcspawn 控制牌第三行需要 <线路id> <当前车站名>", NamedTextColor.RED));
            return false;
        }
        event.getPlayer().sendMessage(Component.text(
                "建立 bcspawn 发车控制牌成功（线路：%s，车站：%s）".formatted(parsed[0], parsed[1]),
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public void destroy(SignActionEvent info) {
        // 牌子被破坏时从 SpawnSignManager 移除（原继承 SignActionSpawn 时由其 destroy 提供，
        // 现自行实现以保持一致）。
        info.getTrainCarts().getSpawnSignManager().remove(info);
    }
}

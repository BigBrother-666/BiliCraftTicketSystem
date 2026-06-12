package com.bigbrother.bilicraftticketsystem.signactions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.signactions.spawner.SpawnSign;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcStartNodeProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
public class SignActionBCSpawn extends SignActionSpawn {

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
            sign.spawn(info);
            sign.resetSpawnTime();
            MinecartGroup group = info.getGroup();
            if (group != null) {
                // 标记列车所属线路（bcswitcher 回退 / platform 据此识别）
                if (!lineId.isEmpty()) {
                    group.getProperties().addTags(lineId);
                    group.onPropertiesChanged();
                }
                // 记录列车起点车站名（新模型据此做车票 verify / 交通卡任意站台上车）
                if (!station.isEmpty()) {
                    BcStartNodeProperty.write(group, station);
                }
                // 发车信息记录到数据库
                plugin.getTrainDatabaseManager().getBcspawnService().recordSpawn(station, lineId);
            }
        }
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
    public boolean match(SignActionEvent info) {
        return info != null && info.getMode() != SignActionMode.NONE && info.isType("bcspawn");
    }
}

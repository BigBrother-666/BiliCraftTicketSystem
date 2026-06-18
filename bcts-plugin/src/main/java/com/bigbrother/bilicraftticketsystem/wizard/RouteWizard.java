package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 线路编辑向导（{@code /ticket editRoute <lineId>}）。
 * <p>
 * 按 routes.yml 字段顺序引导填写：所属铁路系统、线路名、标志色（必填），bossbar 车站列表（必填，
 * 以 {@code ->} 分隔），到站提示、bossbar 颜色、进/出站提示（选填，进出站提示以 {@code ,} 分隔）。
 * 新建模式必填项不可跳过；修改模式预填现有值、均可跳过。完成后写回 routes.yml 并自动重载，
 * 并提示路径变动需重新遍历。
 */
public class RouteWizard extends ConfigWizard {
    private final String lineId;

    /**
     * @param player   发起玩家
     * @param lineId   线路 id
     * @param editMode 是否修改模式
     */
    public RouteWizard(Player player, String lineId, boolean editMode) {
        super(player, editMode);
        this.lineId = lineId;
        if (editMode) {
            LineInfo info = LineConfig.get(lineId);
            if (info != null) {
                values.put("railway-system", info.getRailwaySystemId());
                values.put("line-name", info.getLineName());
                values.put("line-color", info.getLineColor());
                values.put("bossbar-stations", reconstructStations(info));
                values.put("bossbar-arrival-notice", info.getBossbarArrivalNotice());
                values.put("bossbar-color", info.getBossbarColor());
                values.put("notice-arrival", new ArrayList<>(info.getNoticeArrival()));
                values.put("notice-departure", new ArrayList<>(info.getNoticeDeparture()));
            }
        }
    }

    /**
     * 从 LineInfo 还原带 :RV 后缀的原始车站列表（修改模式展示 / 保留用）。
     */
    private List<String> reconstructStations(LineInfo info) {
        List<String> stations = info.getBossbarStations();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            result.add(info.isReverseStation(i) ? stations.get(i) + ":RV" : stations.get(i));
        }
        return result;
    }

    @Override
    protected Component title() {
        return Component.text((editMode ? "修改" : "新建") + "线路 " + lineId, NamedTextColor.GOLD);
    }

    @Override
    protected List<WizardStep> steps() {
        List<WizardStep> steps = new ArrayList<>();

        steps.add(new WizardStep("railway-system", railwaySystemPrompt(), true, this::parseRailwaySystem));

        steps.add(new WizardStep("line-name",
                Component.text("输入线路名称（如 环线（顺时针方向））", NamedTextColor.WHITE),
                true,
                input -> input.isBlank() ? WizardStep.Result.error("线路名称不能为空")
                        : WizardStep.Result.ok(input)));

        steps.add(new WizardStep("line-color",
                Component.text("输入线路标志色，格式 #RRGGBB（如 #AA0000）", NamedTextColor.WHITE),
                true,
                this::parseHexColor));

        steps.add(new WizardStep("bossbar-stations",
                Component.text("""
                                输入车站名列表，按行车顺序用 -> 分隔
                                如果一个车站需要折返，在站名后添加 :RV
                                如果这条线路出最后一站后转入了另一条线路，最后一个车站格式应为 转入线路id:转入线路下一站车站名
                                例子：StationA->StationB:RV->StationC->StationD->otherLineId:Station S""",
                        NamedTextColor.WHITE),
                true,
                this::parseStations));

        steps.add(new WizardStep("bossbar-arrival-notice",
                Component.text("输入到站 bossbar 提示（可用占位符 {curr_station} {next_station} {line_name} {line_color}）", NamedTextColor.WHITE),
                false,
                WizardStep.Result::ok));

        steps.add(new WizardStep("bossbar-color",
                Component.text("输入 bossbar 颜色（PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE）", NamedTextColor.WHITE),
                false,
                this::parseBossbarColor));

        steps.add(new WizardStep("notice-arrival",
                Component.text("输入进站提示，多条用英文逗号 , 分隔（支持 sound:... / announce:...）",
                        NamedTextColor.WHITE),
                false,
                this::parseCommaList));

        steps.add(new WizardStep("notice-departure",
                Component.text("输入出站提示，多条用英文逗号 , 分隔（支持 sound:... / announce:...）",
                        NamedTextColor.WHITE),
                false,
                this::parseCommaList));

        return steps;
    }

    private Component railwaySystemPrompt() {
        List<String> owned = RailwaySystemConfig.getSystemsOfMember(player.getUniqueId());
        return Component.text("输入该线路所属铁路系统 id（你所属的铁路系统："
                + String.join(", ", owned) + "）", NamedTextColor.WHITE);
    }

    /**
     * 校验所属铁路系统：必须是已存在的系统，且玩家须是该系统成员。
     */
    private WizardStep.Result parseRailwaySystem(String input) {
        String id = input.trim();
        if (id.isEmpty()) {
            return WizardStep.Result.error("铁路系统 id 不能为空");
        }
        if (!RailwaySystemConfig.contains(id)) {
            return WizardStep.Result.error("铁路系统 [" + id + "] 不存在，请先用 /ticket editSystem 创建");
        }
        RailwaySystemInfo info = RailwaySystemConfig.get(id);
        if (!info.isMember(player.getUniqueId())) {
            return WizardStep.Result.error("你不是铁路系统 [" + id + "] 的成员，无权把线路归入该系统");
        }
        return WizardStep.Result.ok(id);
    }

    private WizardStep.Result parseHexColor(String input) {
        String s = input.trim();
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            return WizardStep.Result.error("颜色格式应为 #RRGGBB，如 #AA0000");
        }
        return WizardStep.Result.ok(s);
    }

    private WizardStep.Result parseStations(String input) {
        List<String> stations = new ArrayList<>();
        for (String part : input.split("->")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                stations.add(s);
            }
        }
        if (stations.size() < 2) {
            return WizardStep.Result.error("车站列表至少需要 2 站，用 -> 分隔");
        }
        return WizardStep.Result.ok(stations);
    }

    private WizardStep.Result parseBossbarColor(String input) {
        String s = input.trim().toUpperCase();
        try {
            net.kyori.adventure.bossbar.BossBar.Color.valueOf(s);
            return WizardStep.Result.ok(s);
        } catch (IllegalArgumentException e) {
            return WizardStep.Result.error("无效的 bossbar 颜色：" + input
                    + "（可选 PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE）");
        }
    }

    private WizardStep.Result parseCommaList(String input) {
        List<String> list = new ArrayList<>();
        for (String part : input.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return WizardStep.Result.ok(list);
    }

    @Override
    protected String currentValueDisplay(String key) {
        Object v = values.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            if ("bossbar-stations".equals(key)) {
                return String.join(" -> ", listToStrings(list));
            }
            return String.join(", ", listToStrings(list));
        }
        return String.valueOf(v);
    }

    private List<String> listToStrings(List<?> list) {
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            result.add(String.valueOf(o));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onComplete(Map<String, Object> collected) {
        String railwaySystem = (String) collected.get("railway-system");
        String lineName = (String) collected.getOrDefault("line-name", lineId);
        String lineColor = (String) collected.getOrDefault("line-color", "#a9a9a9");
        List<String> stations = collected.get("bossbar-stations") instanceof List
                ? (List<String>) collected.get("bossbar-stations") : new ArrayList<>();
        String arrivalNotice = (String) collected.get("bossbar-arrival-notice");
        String bossbarColor = (String) collected.get("bossbar-color");
        List<String> noticeArrival = collected.get("notice-arrival") instanceof List
                ? (List<String>) collected.get("notice-arrival") : null;
        List<String> noticeDeparture = collected.get("notice-departure") instanceof List
                ? (List<String>) collected.get("notice-departure") : null;

        LineConfig.upsert(lineId, railwaySystem, lineName, lineColor, stations,
                arrivalNotice, bossbarColor, noticeArrival, noticeDeparture);

        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "已保存线路 [%s]，正在重载配置...".formatted(lineId), NamedTextColor.GREEN)));
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "提示：线路走向 / 车站若有变动，请用 /railgeo walkAll 重新遍历生成路由图。",
                NamedTextColor.YELLOW)));
        try {
            LineConfig.load(BiliCraftTicketSystem.plugin);
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载完成", NamedTextColor.GREEN)));
        } catch (Exception e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载时发生错误：" + e.getMessage(), NamedTextColor.RED)));
        }
    }
}

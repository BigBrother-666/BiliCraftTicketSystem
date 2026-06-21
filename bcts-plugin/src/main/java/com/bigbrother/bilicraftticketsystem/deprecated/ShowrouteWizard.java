package com.bigbrother.bilicraftticketsystem.deprecated;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.wizard.ConfigWizard;
import com.bigbrother.bilicraftticketsystem.wizard.WizardStep;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ShowRoute 控制牌线路的创建 / 修改向导（{@code /ticket addroute <routeID>}），
 * 替代原先 {@code PlayerListeners} 里基于聊天分步状态机的实现。
 * <p>
 * 按 railway_routes_old.yml 的字段顺序引导填写：到站显示内容、车站列表（以 {@code ->} 分隔，
 * 至少 2 站）、bossbar 五参数（颜色 已过格式 未过格式 已过个数 未过个数）。完成后写回
 * railway_routes_old.yml 并自动重载。
 * <p>
 * <b>本类位于 deprecated 包，随该套旧线路功能一并待移除。</b> 新建模式必填项不可跳过；
 * 修改模式预填现有值、均可跳过。配置仅在全部步骤完成后由 {@link #onComplete(Map)} 一次性写回，
 * 因此中途掉线 / 放弃不会留下半成品配置。
 */
@Deprecated(since = "2.0.0")
public class ShowrouteWizard extends ConfigWizard {
    private final String routeId;

    /**
     * @param player   发起玩家
     * @param routeId  线路 id
     * @param editMode 是否修改模式（true 时预填现有值）
     */
    public ShowrouteWizard(Player player, String routeId, boolean editMode) {
        super(player, editMode);
        this.routeId = routeId;
        if (editMode) {
            values.put("curr-station-title",
                    RailwayRoutesConfig.railwayRoutes.get("%s.curr-station-title".formatted(routeId), ""));
            values.put("route",
                    RailwayRoutesConfig.railwayRoutes.get("%s.route".formatted(routeId), ""));
            values.put("args",
                    RailwayRoutesConfig.railwayRoutes.get("%s.args".formatted(routeId), ""));
        }
    }

    @Override
    protected Component title() {
        return Component.text((editMode ? "修改" : "新建") + " ShowRoute 线路 " + routeId, NamedTextColor.GOLD);
    }

    @Override
    protected List<WizardStep> steps() {
        List<WizardStep> steps = new ArrayList<>();

        steps.add(new WizardStep("curr-station-title",
                Component.text("输入到站时的显示内容，可用占位符 {station} 表示当前车站名", NamedTextColor.WHITE),
                true,
                input -> input.isBlank() ? WizardStep.Result.error("到站显示内容不能为空")
                        : WizardStep.Result.ok(input)));

        steps.add(new WizardStep("route",
                Component.text("输入完整车站列表，车站名之间使用 -> 分隔（至少 2 站）", NamedTextColor.WHITE),
                true,
                this::parseRoute));

        steps.add(new WizardStep("args",
                Component.text("""
                                输入空格分隔的五个参数，依次为：Bossbar颜色 已经过的车站格式 未经过的车站格式 已经过的车站显示个数 未经过的车站显示个数
                                Bossbar颜色：PINK BLUE RED GREEN YELLOW PURPLE WHITE
                                已经过 / 未经过的车站格式：mc 格式化代码（& 后的字符，不用写 &）""",
                        NamedTextColor.WHITE),
                true,
                this::parseArgs));

        return steps;
    }

    private WizardStep.Result parseRoute(String input) {
        if (input.split("->").length <= 1) {
            return WizardStep.Result.error("至少要包含两个车站，用 -> 分隔");
        }
        return WizardStep.Result.ok(input);
    }

    private WizardStep.Result parseArgs(String input) {
        String[] args = input.trim().split(" ");
        if (args.length < 5) {
            return WizardStep.Result.error("缺少参数，需要 5 个空格分隔的参数");
        }
        // 参数1：bossbar 颜色
        try {
            BossBar.Color.valueOf(args[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WizardStep.Result.error("Bossbar 颜色错误（可选 PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE）");
        }
        // 参数2、3：格式串，不校验
        // 参数4、5：已经过 / 未经过的车站显示个数
        int shown;
        int hidden;
        try {
            shown = Integer.parseInt(args[3].trim());
            hidden = Integer.parseInt(args[4].trim());
        } catch (NumberFormatException e) {
            return WizardStep.Result.error("已经过和未经过的车站显示个数为非负整数，且不能同时为 0");
        }
        if (shown < 0 || hidden < 0 || (shown == 0 && hidden == 0)) {
            return WizardStep.Result.error("已经过和未经过的车站显示个数为非负整数，且不能同时为 0");
        }
        int stationCount = stationCount();
        if (shown + hidden > stationCount) {
            return WizardStep.Result.error(
                    "已经过和未经过的车站显示个数之和不能超过车站个数(%s)".formatted(stationCount));
        }
        return WizardStep.Result.ok(input);
    }

    /**
     * 取当前已收集 / 预填的车站列表长度（供 args 显示个数校验用）。
     */
    private int stationCount() {
        Object route = values.get("route");
        if (route == null) {
            return 0;
        }
        return String.valueOf(route).split("->").length;
    }

    @Override
    protected void onComplete(Map<String, Object> collected) {
        // 修改模式保留原 owner，新建模式归属当前玩家
        String owner = editMode
                ? RailwayRoutesConfig.railwayRoutes.get("%s.owner".formatted(routeId), player.getUniqueId().toString())
                : player.getUniqueId().toString();
        RailwayRoutesConfig.railwayRoutes.set("%s.owner".formatted(routeId), owner);
        RailwayRoutesConfig.railwayRoutes.set("%s.curr-station-title".formatted(routeId), collected.get("curr-station-title"));
        RailwayRoutesConfig.railwayRoutes.set("%s.route".formatted(routeId), collected.get("route"));
        RailwayRoutesConfig.railwayRoutes.set("%s.args".formatted(routeId), collected.get("args"));
        RailwayRoutesConfig.save();
        RailwayRoutesConfig.load(BiliCraftTicketSystem.plugin);

        player.sendMessage(MainConfig.prefix.append(Component.text(
                "线路 %s %s成功。".formatted(routeId, editMode ? "修改" : "添加"), NamedTextColor.GREEN)));
    }
}

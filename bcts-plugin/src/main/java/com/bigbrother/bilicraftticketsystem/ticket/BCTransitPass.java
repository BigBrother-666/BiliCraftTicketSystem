package com.bigbrother.bilicraftticketsystem.ticket;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bigbrother.bctsguardplugin.GuardListeners;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineConfig;
import com.bigbrother.bilicraftticketsystem.config.line.LineInfo;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.route.geograph.GeoRoutePath;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcLineIdProperty;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcRouteNavigator;
import com.bigbrother.bilicraftticketsystem.route.geograph.nav.BcStartNodeProperty;
import com.bigbrother.bilicraftticketsystem.signactions.component.BossbarManager;
import com.bigbrother.bilicraftticketsystem.signactions.component.ExpressRouteBossbar;
import com.bigbrother.bilicraftticketsystem.signactions.component.RouteBossbarBase;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.PlaceholderParser;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public abstract class BCTransitPass {
    public static final String KEY_TRANSIT_PASS_TYPE = "transitPassType";
    public static final String KEY_TRANSIT_PASS_PLUGIN = "plugin";
    public static final String KEY_TRANSIT_PASS_BACKGROUND_IMAGE_PATH = "backgroundImagePath";


    protected ItemStack itemStack;
    /**
     * 本次行程的路径（geojson 路由引擎产出）。
     */
    protected GeoRoutePath pathInfo;
    protected double maxSpeed;
    /**
     * 支付本次票价的玩家 UUID（车票=购买者，交通卡=刷卡者）。用于按段免除其所属铁路系统的票价。
     * 为 {@code null} 时不享受任何成员免票。
     */
    protected UUID payerUuid;

    /**
     * 验证坐车凭证是否可以对这趟列车使用
     *
     * @param usedPlayer 使用坐车凭证的玩家
     * @param group      将要应用坐车凭证的列车
     * @return 坐车凭证是否可以对这趟列车使用
     */
    public abstract boolean verify(Player usedPlayer, MinecartGroup group);

    /**
     * 使用车票，对车票/玩家的属性进行更改
     *
     * @param usedPlayer 使用的玩家
     */
    public abstract void useTransitPass(Player usedPlayer);

    /**
     * 获取坐车凭证对应的单次票，用于快速购买
     *
     * @param usedPlayer 单次票应用的玩家
     * @return 单次票
     */
    public abstract BCTicket getNewSingleTicket(Player usedPlayer);

    /**
     * 获取玩家实际需要支付的金额
     *
     * @return 实际金额
     */
    public abstract double getPrice();

    public double getRideHistoryFare() {
        return getPrice();
    }

    protected void initPdc() {
        ItemMeta itemMeta = this.itemStack.getItemMeta();
        if (itemMeta != null && !itemMeta.getPersistentDataContainer().has(GuardListeners.KEY_TRANSIT_PASS)) {
            itemStack.editMeta(meta -> meta.getPersistentDataContainer().set(GuardListeners.KEY_TRANSIT_PASS, PersistentDataType.BOOLEAN, true));
        }
    }

    // 返回 起始站 → 终到站 字符串
    public abstract String getTransitPassName();

    public double getSpeedKph() {
        return CommonUtils.mpt2Kph(maxSpeed);
    }

    /**
     * 给矿车设置属性，初始化bossbar
     */
    public void applyTo(Player usedPlayer, MinecartGroup group) {
        TrainProperties trainProperties = group.getProperties();
        trainProperties.clearTickets();

        // 设置speed
        trainProperties.setSpeedLimit(maxSpeed);
        // 写入导航节点步骤序列（bcswitcher 据当前步骤 lineId 选向，platform 仅推进进度）
        BcRouteNavigator.setRoute(group, pathInfo.routeSteps());
        trainProperties.getHolder().onPropertiesChanged();

        // 所有车厢显示直达车bossbar
        for (MinecartMember<?> minecartMember : group) {
            RouteBossbarBase bossbar = BossbarManager.get(minecartMember);
            if (!(bossbar instanceof ExpressRouteBossbar)) {
                ExpressRouteBossbar express = new ExpressRouteBossbar(
                        pathInfo.getStartStationName(), pathInfo.getEndStationName());
                BossbarManager.put(minecartMember, express);
                express.refreshProgress(0, pathInfo.routeSteps().size());
            }
        }
    }

    /**
     * 获取路线详情lore（只显示车站节点，箭头按该段所属 lineId 上色）。
     *
     * @param cntPerRow 每行最多显示的车站数量，<=0不限制
     */
    public List<Component> getPathInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<GeoRoutePath.StationStep> steps = pathInfo.stationSteps();
        Component join = Component.text("");
        int cnt = 0;
        for (int i = 0; i < steps.size(); i++) {
            GeoRoutePath.StationStep step = steps.get(i);
            String stationName = step.stationName();

            if (i == steps.size() - 1) {
                // 终到站
                join = join.append(Component.text(stationName, lineColor(step.departLineId()))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(join);
                break;
            }
            if (cnt == 0) {
                // 起始站
                join = join.append(Component.text(stationName, lineColor(step.departLineId()))
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false));
            } else if (cntPerRow <= 0 || cnt % cntPerRow != 0) {
                // 经过站
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, true));
            } else {
                lore.add(join);
                // 开始新的一行
                join = Component.text("");
                join = join.append(Component.text(stationName, NamedTextColor.GRAY)
                        .append(Component.text("→", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, true));
            }
            cnt += 1;
        }
        return lore;
    }

    /**
     * 获取途经线路lore（按经过顺序列出沿途 lineId 对应的线路名，相邻去重）。
     *
     * @param cntPerRow 每行最多显示的线路数量，<=0不限制
     */
    public List<Component> getRailwayInfoLore(int cntPerRow) {
        List<Component> lore = new ArrayList<>();
        List<String> lineIds = new ArrayList<>();

        for (String lineId : pathInfo.getLineIdSequence()) {
            LineInfo lineInfo = LineConfig.get(lineId);
            if (lineId == null || lineId.isEmpty() || lineInfo == null) {
                continue;
            }
            // 如果该lineId没有对应的铁路公司，不显示
            String railwaySystemId = lineInfo.getRailwaySystemId();
            if (railwaySystemId == null || railwaySystemId.isEmpty() || RailwaySystemConfig.get(railwaySystemId) == null) {
                continue;
            }
            // 相邻去重
            if (lineIds.isEmpty() || !lineIds.getLast().equals(lineId)) {
                lineIds.add(lineId);
            }
        }

        Component railwayLore = Component.text("");
        for (int i = 0; i < lineIds.size(); i++) {
            if (i != 0 && cntPerRow > 0 && i % cntPerRow == 0) {
                lore.add(railwayLore);
                railwayLore = Component.text("");
            }
            String lineName = lineName(lineIds.get(i));
            TextColor color = lineColor(lineIds.get(i));
            if (i == lineIds.size() - 1) {
                railwayLore = railwayLore.append(Component.text(lineName, color));
            } else {
                railwayLore = railwayLore.append(Component.text(lineName, color).append(Component.text("→", NamedTextColor.GOLD)));
            }
        }
        lore.add(railwayLore);

        return lore;
    }

    /**
     * 获取车票价格部分lore，购买后会移除
     *
     * @return lore
     */
    public List<Component> getPriceInfoLore() {
        List<Component> lore = new ArrayList<>();
        Map<String, Double> segmentDistances = rawSegmentDistances();

        Map<String, Object> placeholder = new HashMap<>();
        for (Map.Entry<String, Double> entry : segmentDistances.entrySet()) {
            String systemId = entry.getKey();
            if (systemId.equals(RailwaySystemConfig.CONTACT_ID)) {
                placeholder.put("railway_system", "联络线");
                placeholder.put("system_distance", "%.2f".formatted(entry.getValue()));
                placeholder.put("system_price", "%.2f".formatted(MainConfig.pricePerKm * entry.getValue()));
                placeholder.put("system_price_per_km", "%.2f".formatted(MainConfig.pricePerKm));
            } else {
                RailwaySystemInfo systemInfo = RailwaySystemConfig.get(systemId);
                placeholder.put("railway_system", systemInfo.getName());
                placeholder.put("system_distance", "%.2f".formatted(entry.getValue()));
                placeholder.put("system_price", "%.2f".formatted(entry.getValue() * systemInfo.getPricePerKm()));
                placeholder.put("system_price_per_km", "%.2f".formatted(systemInfo.getPricePerKm()));
            }
            List<Component> configLore = parseConfigLore(List.of(MainConfig.distanceInfoLore), placeholder);
            if (!configLore.isEmpty()) {
                lore.add(configLore.getFirst());
            }
        }
        return lore;
    }

    /**
     * 线路 id → 显示色（取 {@link LineConfig#getColor}，无效时回退金色）。
     */
    protected static TextColor lineColor(String lineId) {
        if (lineId == null) {
            return NamedTextColor.GOLD;
        }
        TextColor color = TextColor.fromHexString(LineConfig.getColor(lineId));
        return color == null ? NamedTextColor.GOLD : color;
    }

    /**
     * 线路 id → MiniMessage颜色字符串（例<#RRGGBB>，取 {@link LineConfig#getColor}，无效时回退金色）。
     */
    protected static String lineMiniMessageColor(String lineId) {
        if (lineId == null) {
            return "<gold>";
        }
        String color = LineConfig.getColor(lineId);
        return color == null ? "<gold>" : "<%s>".formatted(color);
    }

    /**
     * 线路 id → 线路名（取 {@link LineConfig} 的 lineName，缺省回退 lineId 本身）。
     */
    protected static String lineName(String lineId) {
        LineInfo info = LineConfig.get(lineId);
        return info == null || info.getLineName() == null ? lineId : info.getLineName();
    }

    /**
     * 组合「车站名 + 所属铁路系统」的展示串（MiniMessage）：
     * {@code <线路标志色>车站名<gray>(<铁路系统名>)}。颜色与系统均取自<b>同一条</b>线路（行程实际线路）。
     * <p>
     * 站名为空 / NOT_AVAILABLE 时仅返回 {@link CommonUtils#NOT_AVAILABLE_MM}（不拼成 N/A(N/A)）；
     * 该线路无所属系统时省略括号部分，只显示带色站名。
     *
     * @param stationName 车站名（MiniMessage 或纯文本）
     * @param lineId      行程在该端实际所属线路 id（决定颜色与系统）
     * @return 组合展示串
     */
    protected static String stationWithSystem(String stationName, String lineId) {
        if (stationName == null || stationName.isEmpty() || CommonUtils.NOT_AVAILABLE.equals(stationName)) {
            return CommonUtils.NOT_AVAILABLE_MM;
        }
        String systemId = LineConfig.getSystemId(lineId);
        RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
        if (system == null) {
            return "%s%s".formatted(lineMiniMessageColor(lineId), stationName);
        }
        return "%s%s<gold>(%s)".formatted(lineMiniMessageColor(lineId), stationName, system.getName());
    }

    public List<Component> parseConfigLore(List<String> originLore, @NotNull Map<String, Object> initPlaceholders) {
        if (pathInfo != null) {
            String startLineId = pathInfo.getStartLineId();
            String endLineId = pathInfo.getEndLineId();
            String startSystemId = LineConfig.getSystemId(startLineId);
            String endSystemId = LineConfig.getSystemId(endLineId);
            initPlaceholders.putIfAbsent("start_station_name", pathInfo.getStartStationName());
            initPlaceholders.putIfAbsent("start_railway_name", lineName(startLineId));
            if (startSystemId != null) {
                initPlaceholders.putIfAbsent("start_railway_system", RailwaySystemConfig.get(startSystemId).getName());
            }
            initPlaceholders.putIfAbsent("start_line_color", lineMiniMessageColor(startLineId));
            initPlaceholders.putIfAbsent("end_station_name", pathInfo.getEndStationName());
            if (endSystemId != null) {
                initPlaceholders.putIfAbsent("end_railway_system", RailwaySystemConfig.get(endSystemId).getName());
            }
            // 合成占位符：带色站名 + (系统名)，避免模板里硬编码括号在空值时显示 N/A(N/A)
            initPlaceholders.putIfAbsent("start_station_full", stationWithSystem(pathInfo.getStartStationName(), startLineId));
            initPlaceholders.putIfAbsent("end_station_full", stationWithSystem(pathInfo.getEndStationName(), endLineId));

            initPlaceholders.putIfAbsent("path_info_lore", getPathInfoLore(MainConfig.loreStationNameCntRow));
            initPlaceholders.putIfAbsent("railway_info_lore", getRailwayInfoLore(MainConfig.loreRailwayNameCntRow));

            initPlaceholders.putIfAbsent("distance", "%.2f".formatted(pathInfo.getDistance()));
            initPlaceholders.putIfAbsent("speed", "%.2f".formatted(this.getSpeedKph()));
            initPlaceholders.putIfAbsent("price", "%.2f".formatted(this.getPrice()));
        }
        return PlaceholderParser.parse(originLore, initPlaceholders);
    }

    public static boolean isBCTransitPass(ItemStack itemStack) {
        return BCTicket.isBctsTicket(itemStack) || BCCard.isBctsCard(itemStack);
    }

    public static boolean isNewPRTrain(MinecartGroup group) {
        return group.getProperties().getTickets().contains(MainConfig.expressTicketName);
    }

    /**
     * 列车上车起点车站名（新模型：bcspawn 发车时记录到列车属性，替代旧的「读 tag 解析站台」）。
     *
     * @param group 列车
     * @return 起点车站名；未记录返回空串
     */
    public static String getTrainStartStationName(MinecartGroup group) {
        return BcStartNodeProperty.read(group);
    }

    /**
     * 列车所属营运线路 id：读取列车的 {@link BcLineIdProperty}（bcspawn 发车时写入）。
     * <p>
     * 旧模型把线路 id 作为 tag 加在列车上，已改为 train property（玩家无法用 tag 指令篡改）。
     *
     * @param group 列车
     * @return 营运线路 id；找不到 / 非营运线返回空串
     */
    public static String getTrainLineId(MinecartGroup group) {
        if (group == null) {
            return "";
        }
        String lineId = BcLineIdProperty.read(group);
        LineInfo info = LineConfig.get(lineId);
        if (info != null) {
            return lineId;
        }
        return "";
    }

    // 计算票价
    protected double calculateFare(double distance) {
        if (pathInfo == null) {
            return Math.round(distance * MainConfig.pricePerKm * 100.0) / 100.0;
        }
        double total = 0.0;
        for (double fare : rawSegmentFares().values()) {
            total += fare;
        }
        return Math.round(total * 100.0) / 100.0;
    }

    /**
     * 计算本次行程「按段所属铁路系统」的原始段费（未叠加次数倍数 / 折扣 / 起步价）。
     * <p>
     * 每段按其所属系统的 price-per-km（系统未单独配置则用全局默认）× 段公里数累加；支付方为某系统成员时，
     * 该系统的段费记 0（仍出现在结果中，便于「0 值也记录」与按系统建账）。
     *
     * @return 系统 id -> 原始段费（含 0 值），保持经过顺序
     */
    protected Map<String, Double> rawSegmentFares() {
        Map<String, Double> result = new LinkedHashMap<>();
        if (pathInfo == null) {
            return result;
        }
        List<String> lineIds = pathInfo.getLineIdSequence();
        List<Double> segDistances = pathInfo.getDistanceSequence();
        // 缓存每个铁路系统玩家是否为成员，避免逐段重复查询
        Map<String, Boolean> memberCache = new HashMap<>();

        for (int i = 0; i < segDistances.size(); i++) {
            double segKm = segDistances.get(i);
            String lineId = i < lineIds.size() ? lineIds.get(i) : null;
            String systemId = LineConfig.getSystemId(lineId);
            RailwaySystemInfo system = RailwaySystemConfig.get(systemId);
            if (system == null) {
                // 特殊：线路有systemId，但是无所属系统信息，按照全局配置计费
                if (systemId != null) {
                    result.merge(systemId, MainConfig.pricePerKm * segKm, Double::sum);
                }
                continue;
            }
            // 该段所属系统的玩家为成员则免除这部分票价（段费记 0）
            boolean waived = payerUuid != null
                    && memberCache.computeIfAbsent(systemId, id -> system.isMember(payerUuid));
            double rate = system.getPricePerKm() != null ? system.getPricePerKm() : MainConfig.pricePerKm;
            double fare = waived ? 0.0 : segKm * rate;
            result.merge(systemId, fare, Double::sum);
        }
        return result;
    }

    /**
     * 把玩家<b>实际支付</b>的金额按各系统的原始段费比例分摊，得到「按系统建账」的明细（账目闭合：
     * 各系统金额之和 == {@code actualPaid}，四舍五入到分）。
     * <p>
     * 分摊规则：
     * <ul>
     *   <li>{@code baseFare} 部分（交通卡起步价）整体计入行程<b>起点段</b>所属系统；非交通卡传 0。</li>
     *   <li>剩余 {@code actualPaid - baseFare} 按各系统原始段费比例分摊；若原始段费全为 0
     *       （如成员全免 / 零距离），则把剩余金额也全部计入起点系统。</li>
     *   <li>四舍五入残差补到金额最大的系统，保证求和等于 actualPaid。</li>
     * </ul>
     * 起点系统取不到时退化为按比例分摊（baseFare 也并入比例分摊）。
     *
     * @param actualPaid 玩家本次实付金额
     * @param baseFare   归起点系统的固定部分（交通卡起步价，否则 0）
     * @return 系统 id -> 应记收入（含 0 值），保持经过顺序
     */
    protected Map<String, Double> allocateIncome(double actualPaid, double baseFare) {
        Map<String, Double> raw = rawSegmentFares();
        Map<String, Double> result = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return result;
        }
        for (String systemId : raw.keySet()) {
            result.put(systemId, 0.0);
        }

        String startSystemId = LineConfig.getSystemId(pathInfo == null ? null : pathInfo.getStartLineId());
        if (!result.containsKey(startSystemId)) {
            startSystemId = result.keySet().iterator().next();
        }

        double rawTotal = 0.0;
        for (double f : raw.values()) {
            rawTotal += f;
        }

        // 起步价整体归起点系统
        double base = Math.min(baseFare, actualPaid);
        result.merge(startSystemId, base, Double::sum);
        double remaining = actualPaid - base;

        if (rawTotal <= 0) {
            // 原始段费全为 0：剩余金额全部归起点系统
            result.merge(startSystemId, remaining, Double::sum);
        } else {
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                result.merge(entry.getKey(), remaining * entry.getValue() / rawTotal, Double::sum);
            }
        }

        // 四舍五入到分，残差补到金额最大的系统，保证账目闭合
        return roundClosing(result, actualPaid);
    }

    /**
     * 把分摊明细各项四舍五入到分，并把与目标总额的舍入残差补到当前金额最大的系统，使求和精确等于
     * {@code target}。
     *
     * @param allocation 分摊明细（会被读取，不修改入参）
     * @param target     目标总额
     * @return 四舍五入并闭合后的明细
     */
    private static Map<String, Double> roundClosing(Map<String, Double> allocation, double target) {
        Map<String, Double> rounded = new java.util.LinkedHashMap<>();
        double sum = 0.0;
        String maxKey = null;
        double maxVal = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : allocation.entrySet()) {
            double v = Math.round(entry.getValue() * 100.0) / 100.0;
            rounded.put(entry.getKey(), v);
            sum += v;
            if (entry.getValue() > maxVal) {
                maxVal = entry.getValue();
                maxKey = entry.getKey();
            }
        }
        double diff = Math.round((target - sum) * 100.0) / 100.0;
        if (maxKey != null && diff != 0.0) {
            rounded.merge(maxKey, diff, Double::sum);
        }
        return rounded;
    }

    /**
     * 统计本次行程经过<b>每个铁路系统</b>的总距离（km），与 {@link #rawSegmentFares()} 的系统口径一致
     * （含成员免票的系统——列车仍实际经过这些里程）。
     *
     * @return 系统 id -> 经过总距离（km），保持经过顺序
     */
    protected Map<String, Double> rawSegmentDistances() {
        Map<String, Double> result = new LinkedHashMap<>();
        if (pathInfo == null) {
            return result;
        }
        List<String> lineIds = pathInfo.getLineIdSequence();
        List<Double> segDistances = pathInfo.getDistanceSequence();
        for (int i = 0; i < segDistances.size(); i++) {
            String lineId = i < lineIds.size() ? lineIds.get(i) : null;
            String systemId = LineConfig.getSystemId(lineId);
            if (systemId == null) {
                continue;
            }
            result.merge(systemId, segDistances.get(i), Double::sum);
        }
        return result;
    }

    /**
     * 把按系统分摊明细序列化为 JSON 字符串 {@code {"systemId":{"price":p,"distance":d},...}}
     * （金额与距离均保留两位小数，含 0 值）。距离取自 {@link #rawSegmentDistances()}，
     * 缺失的系统距离记 0。用于写入 transit_pass_usage_info.price 字段。
     *
     * @param perSystemPrice    系统 id -> 分摊金额
     * @param perSystemDistance 系统 id -> 经过总距离（km）
     * @return JSON 字符串；入参为空时返回 {@code "{}"}
     */
    protected static String toPriceJson(Map<String, Double> perSystemPrice, Map<String, Double> perSystemDistance) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : perSystemPrice.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            String key = entry.getKey().replace("\\", "\\\\").replace("\"", "\\\"");
            double distance = perSystemDistance.getOrDefault(entry.getKey(), 0.0);
            sb.append("\"").append(key).append("\":{\"price\":")
                    .append("%.2f".formatted(entry.getValue()))
                    .append(",\"distance\":")
                    .append("%.2f".formatted(distance))
                    .append("}");
        }
        return sb.append("}").toString();
    }
}

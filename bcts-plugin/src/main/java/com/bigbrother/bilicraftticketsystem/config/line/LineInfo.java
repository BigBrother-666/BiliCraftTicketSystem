package com.bigbrother.bilicraftticketsystem.config.line;

import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * 一条线路的配置信息（对应 routes.yml 中的一个线路 id）。
 * <p>
 * 每条线路含完整 bossbar / 进出站提示信息。所有字段在缺省时给出安全默认值，方便扩展
 * （例如以后加入铁路公司 company 字段，只需在此追加并在 {@link LineConfig} 解析）。
 */
@Getter
@ToString
public class LineInfo {
    /**
     * 线路 id（routes.yml 中的键，如 "pr-cw"）。
     */
    private final String id;
    /**
     * 所属铁路系统 id（routes.yml 的 {@code railway-system}），必填。
     */
    private final String railwaySystemId;
    /**
     * 线路名称，如 "环线（顺时针方向）"。
     */
    private final String lineName;
    /**
     * 线路标志色（十六进制，如 "#AA0000"）。
     */
    private final String lineColor;
    /**
     * bossbar 车站列表，首尾车站名相同则为环线。
     * <p>
     * 列表中存放的是<b>干净站名</b>（已剥离 {@code :RV} 等后缀）。折返信息见
     * {@link #reverseStationIndices}。
     */
    private final List<String> bossbarStations;
    /**
     * 需要折返出站的车站在 {@link #bossbarStations} 中的下标集合。
     * <p>
     * 来源：routes.yml 中车站名写成 {@code 站名:RV}（尽头式车站，进站后反向驶出）。
     * 用下标而非站名标记，是因为环线会重复站名，按位置才能精确区分。
     */
    private final java.util.Set<Integer> reverseStationIndices;
    /**
     * 到站 bossbar 提示，支持占位符 {curr_station}。
     */
    private final String bossbarArrivalNotice;
    /**
     * bossbar 颜色名（PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE）。
     */
    private final String bossbarColor;
    /**
     * 进站提示，支持 tc 的 announce / sound 等指令；可能为空。
     */
    private final List<String> noticeArrival;
    /**
     * 出站提示，支持 tc 的 announce / sound 等指令；可能为空。
     */
    private final List<String> noticeDeparture;
    /**
     * 转线目标线路 id：routes.yml 中 {@code bossbar-stations} 最后一项若填的是
     * {@code <线路id>} 或 {@code <线路id>:<进入站名>}（而非普通站名），表示普通车到达本线终点站后
     * 转入该线路继续运行。无转线时为 null。
     * <p>
     * 该项不计入 {@link #bossbarStations}（不当站名、不参与遍历期望站序）。
     */
    private final String nextLineId;
    /**
     * 转线后在目标线路上的<b>进入站名</b>（即转线后的下一站，可能跳过目标线路靠前的车站）。
     * <p>
     * 来源：{@code bossbar-stations} 最后一项写成 {@code <线路id>:<进入站名>} 时冒号后的部分。
     * 仅写 {@code <线路id>}（无冒号）时为空串。用于转线后 bossbar 的初始定位与出站提示
     * {@code {next_station}}。
     */
    private final String nextLineEntryStation;

    /**
     * @param id                    线路 id
     * @param railwaySystemId       所属铁路系统 id
     * @param lineName              线路名称
     * @param lineColor             线路标志色
     * @param bossbarStations       干净站名列表（已剥离 :RV 等后缀）
     * @param reverseStationIndices 需折返的车站下标集合
     * @param bossbarArrivalNotice  到站 bossbar 提示
     * @param bossbarColor          bossbar 颜色名
     * @param noticeArrival         进站提示列表
     * @param noticeDeparture       出站提示列表
     * @param nextLineId            转线目标线路 id（无则 null）
     * @param nextLineEntryStation  转线后的进入站名（无则 null / 空串）
     */
    public LineInfo(String id,
                    String railwaySystemId,
                    String lineName,
                    String lineColor,
                    List<String> bossbarStations,
                    java.util.Set<Integer> reverseStationIndices,
                    String bossbarArrivalNotice,
                    String bossbarColor,
                    List<String> noticeArrival,
                    List<String> noticeDeparture,
                    String nextLineId,
                    String nextLineEntryStation) {
        this.id = id;
        this.railwaySystemId = railwaySystemId;
        this.lineName = lineName;
        this.lineColor = lineColor;
        this.bossbarStations = bossbarStations == null ? Collections.emptyList() : bossbarStations;
        this.reverseStationIndices = reverseStationIndices == null ? Collections.emptySet() : reverseStationIndices;
        this.bossbarArrivalNotice = bossbarArrivalNotice;
        this.bossbarColor = bossbarColor;
        this.noticeArrival = noticeArrival == null ? Collections.emptyList() : noticeArrival;
        this.noticeDeparture = noticeDeparture == null ? Collections.emptyList() : noticeDeparture;
        this.nextLineId = nextLineId;
        this.nextLineEntryStation = nextLineEntryStation;
    }

    /**
     * 判断 bossbar 车站列表中指定下标的车站是否为折返站（尽头式，进站后反向驶出）。
     *
     * @param index 车站在 bossbarStations 中的下标
     * @return true 表示该站需折返出站
     */
    public boolean isReverseStation(int index) {
        return reverseStationIndices.contains(index);
    }

    /**
     * 按<b>站名</b>判断该线路上是否存在折返站（尽头式，进站后反向驶出）。
     * <p>
     * 遍历期只能从 platform 控制牌拿到站名（没有下标），故提供按名查询。环线即便站名重复，
     * 折返与否只取决于该站本身的 {@code :RV} 标记，按名判断与按下标一致。
     *
     * @param stationName 车站名（干净站名，不含 {@code :RV} 后缀）
     * @return true 表示该站为折返站
     */
    public boolean isReverseStationByName(String stationName) {
        if (stationName == null) {
            return false;
        }
        for (int index : reverseStationIndices) {
            if (index < bossbarStations.size() && stationName.equals(bossbarStations.get(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断该线路是否为环线（bossbar 车站列表首尾相同）。
     *
     * @return true 表示环线
     */
    public boolean isRing() {
        return bossbarStations.size() > 1
                && bossbarStations.getFirst().equals(bossbarStations.getLast());
    }
}

package com.bigbrother.bilicraftticketsystem.config.line;

import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * 一条线路的配置信息（对应 routes.yml 中的一个线路 id）。
 * <p>
 * 普通线路含完整 bossbar / 进出站提示信息；特殊 id（contact 联络线、default 到发线）
 * 通常只含 line-name / line-color。所有字段在缺省时给出安全默认值，方便扩展
 * （例如以后加入铁路公司 company 字段，只需在此追加并在 {@link LineConfig} 解析）。
 */
@Getter
@ToString
public class LineInfo {
    /**
     * 联络线特殊 id。
     */
    public static final String CONTACT_ID = "contact";
    /**
     * 到发线特殊 id。
     */
    public static final String DEFAULT_ID = "default";

    /**
     * 线路 id（routes.yml 中的键，如 "pr-cw"）。
     */
    private final String id;
    /**
     * 所属铁路系统 id（routes.yml 的 {@code railway-system}），普通线路必填。
     * 特殊线路（contact / default）该值无实际意义，可能为 null。
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
     * bossbar 车站列表，首尾车站名相同则为环线；特殊 id 可能为空。
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
     * @param id                   线路 id
     * @param lineName             线路名称
     * @param lineColor            线路标志色
     * @param bossbarStations      bossbar 车站列表
     * @param bossbarArrivalNotice 到站 bossbar 提示
     * @param bossbarColor         bossbar 颜色名
     * @param noticeArrival        进站提示列表
     * @param noticeDeparture      出站提示列表
     */
    public LineInfo(String id,
                    String lineName,
                    String lineColor,
                    List<String> bossbarStations,
                    String bossbarArrivalNotice,
                    String bossbarColor,
                    List<String> noticeArrival,
                    List<String> noticeDeparture) {
        this(id, null,
                lineName, lineColor, bossbarStations, Collections.emptySet(),
                bossbarArrivalNotice, bossbarColor, noticeArrival, noticeDeparture);
    }

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
                    List<String> noticeDeparture) {
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
     * 判断该线路是否为环线（bossbar 车站列表首尾相同）。
     *
     * @return true 表示环线
     */
    public boolean isRing() {
        return bossbarStations.size() > 1
                && bossbarStations.get(0).equals(bossbarStations.get(bossbarStations.size() - 1));
    }

    /**
     * 判断该线路 id 是否为特殊 id（联络线或到发线）。
     *
     * @return true 表示特殊 id
     */
    public boolean isSpecial() {
        return CONTACT_ID.equalsIgnoreCase(id) || DEFAULT_ID.equalsIgnoreCase(id);
    }

    /**
     * 判断给定线路 id 是否为特殊 id（联络线或到发线），不依赖 {@link LineInfo} 实例。
     *
     * @param lineId 线路 id
     * @return true 表示特殊 id
     */
    public static boolean isSpecialId(String lineId) {
        return CONTACT_ID.equalsIgnoreCase(lineId) || DEFAULT_ID.equalsIgnoreCase(lineId);
    }
}

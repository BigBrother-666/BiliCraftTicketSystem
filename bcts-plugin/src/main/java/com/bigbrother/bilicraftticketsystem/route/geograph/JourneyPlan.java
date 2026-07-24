package com.bigbrother.bilicraftticketsystem.route.geograph;

import lombok.Getter;

import java.util.List;

/**
 * 一次「换乘行程方案」：由若干段独立直达路径（{@link GeoRoutePath}）串联而成，段与段之间需在换乘站
 * <b>下车、步行换乘、再上车</b>（每段各对应一张独立的直达车票）。
 * <p>
 * 本类是只读聚合，<b>不引入新的图或新的车票模型</b>：它只是把现有寻路引擎产出的多条 {@link GeoRoutePath}
 * 打包起来，供上层显示与批量购买。票价由每段各自的车票逻辑计算（见 UI 层封装），本类只提供距离聚合。
 * <p>
 * 当前仅用于「一次换乘 / 两段」，但结构上不限制段数。
 */
@Getter
public class JourneyPlan {
    /**
     * 各段直达路径，按乘车顺序排列（首段起点=行程起点，末段终点=行程终点）。
     */
    private final List<GeoRoutePath> legs;
    /**
     * 各换乘站名，按顺序排列（size = legs.size()-1）。第 i 个换乘站 == legs[i] 的终点 == legs[i+1] 的起点。
     */
    private final List<String> transferStations;
    /**
     * 全程总距离（各段距离之和），单位 km。
     */
    private final double totalDistance;

    /**
     * @param legs             各段直达路径（至少两段，且相邻段首尾站名一致）
     * @param transferStations 各换乘站名（size = legs.size()-1）
     */
    public JourneyPlan(List<GeoRoutePath> legs, List<String> transferStations) {
        this.legs = legs;
        this.transferStations = transferStations;
        double total = 0.0;
        for (GeoRoutePath leg : legs) {
            total += leg.getDistance();
        }
        this.totalDistance = total;
    }

    /**
     * 行程起点站名（首段起点）。
     *
     * @return 起点站名
     */
    public String getStartStationName() {
        return legs.getFirst().getStartStationName();
    }

    /**
     * 行程终点站名（末段终点）。
     *
     * @return 终点站名
     */
    public String getEndStationName() {
        return legs.getLast().getEndStationName();
    }

    /**
     * 段数（== 需购买的车票张数）。
     *
     * @return 段数
     */
    public int legCount() {
        return legs.size();
    }
}

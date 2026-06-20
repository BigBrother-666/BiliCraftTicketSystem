package com.bigbrother.bilicraftticketsystem.config.system;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一个铁路系统的配置信息（对应 railway_system.yml 中的一个系统 id）。
 * <p>
 * 一个铁路系统下可包含多条线路（线路在 railway_routes.yml 中通过 {@code railway-system} 字段声明所属系统）。
 * 成员（members）用玩家 UUID 标识，用于校验玩家是否有权编辑该系统下的线路 / 系统本身。
 * <p>
 * 除名称 / 成员 / 每公里价格外，还记录系统<b>创建者</b>与<b>收入 / 已取出</b>金额：创建者在游戏内创建时
 * 生成、不可修改；收入随购票 / 刷卡实时累加，创建者上线时把「收入 - 已取出」发放给本人并把已取出补齐。
 */
@Getter
@ToString
public class RailwaySystemInfo {
    /**
     * 系统 id（railway_system.yml 中的键，如 "paralon-railway"）。
     */
    private final String id;
    /**`
     * 系统显示名称，如 "帕拉伦国有铁路"。
     */
    private final String name;
    /**
     * 成员玩家 UUID 集合。
     */
    private final Set<UUID> members;
    /**
     * 本系统的每公里价格；为 {@code null} 表示未单独配置，计费时回退到 config.yml 的全局 price-per-km。
     */
    private final Double pricePerKm;
    /**
     * 系统创建者 UUID（游戏内创建时写入，之后不可修改）；历史 / 手写配置可能为 {@code null}。
     */
    private final UUID creator;
    /**
     * 累计收入（购票 / 刷卡实付，按段分摊到本系统的部分之和）。实时累加，玩家不可在向导中修改。
     */
    @Setter
    private double income;
    /**
     * 已发放给创建者的金额。创建者上线时补发 {@code income - withdrawn} 并把本字段对齐到 income。
     */
    @Setter
    private double withdrawn;

    /**
     * @param id         系统 id
     * @param name       系统显示名称
     * @param members    成员 UUID 集合
     * @param pricePerKm 每公里价格（null 表示沿用全局默认）
     * @param creator    创建者 UUID（可空）
     * @param income     累计收入
     * @param withdrawn  已取出金额
     */
    public RailwaySystemInfo(String id, String name, Set<UUID> members, Double pricePerKm,
                             UUID creator, double income, double withdrawn) {
        this.id = id;
        this.name = name;
        this.members = members == null ? new LinkedHashSet<>() : new LinkedHashSet<>(members);
        this.pricePerKm = pricePerKm;
        this.creator = creator;
        this.income = income;
        this.withdrawn = withdrawn;
    }

    /**
     * 判断给定玩家是否为该系统创建者。
     *
     * @param playerUuid 玩家 UUID
     * @return true 表示是创建者
     */
    public boolean isCreator(UUID playerUuid) {
        return creator != null && creator.equals(playerUuid);
    }

    /**
     * 判断给定玩家是否为该系统成员。
     *
     * @param playerUuid 玩家 UUID
     * @return true 表示是成员
     */
    public boolean isMember(UUID playerUuid) {
        return playerUuid != null && members.contains(playerUuid);
    }

    /**
     * 取成员 UUID 的只读视图。
     *
     * @return 不可变的成员集合
     */
    public Set<UUID> getMembersView() {
        return Collections.unmodifiableSet(members);
    }
}

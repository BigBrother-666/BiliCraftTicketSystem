package com.bigbrother.bilicraftticketsystem.config.system;

import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一个铁路系统的配置信息（对应 railway_system.yml 中的一个系统 id）。
 * <p>
 * 一个铁路系统下可包含多条线路（线路在 routes.yml 中通过 {@code railway-system} 字段声明所属系统）。
 * 成员（members）用玩家 UUID 标识，用于校验玩家是否有权编辑该系统下的线路 / 系统本身。
 * <p>
 * 当前为最小配置（name + members），字段结构便于以后扩展（如系统标志色、图标等）。
 */
@Getter
@ToString
public class RailwaySystemInfo {
    /**
     * 系统 id（railway_system.yml 中的键，如 "paralon-railway"）。
     */
    private final String id;
    /**
     * 系统显示名称，如 "帕拉伦国有铁路"。
     */
    private final String name;
    /**
     * 成员玩家 UUID 集合。
     */
    private final Set<UUID> members;

    /**
     * @param id      系统 id
     * @param name    系统显示名称
     * @param members 成员 UUID 集合
     */
    public RailwaySystemInfo(String id, String name, Set<UUID> members) {
        this.id = id;
        this.name = name;
        this.members = members == null ? new LinkedHashSet<>() : new LinkedHashSet<>(members);
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

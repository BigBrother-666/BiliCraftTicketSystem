package com.bigbrother.bilicraftticketsystem.config.system;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 铁路系统配置（railway_system.yml）的加载器与写回工具。
 * <p>
 * 每个顶层键是一个系统 id，解析为 {@link RailwaySystemInfo}。提供供编辑向导使用的
 * {@link #upsert} 写回工具（保留 yaml 注释）。插件启动 / reload 时调用 {@link #load}。
 */
public class RailwaySystemConfig {
    /**
     * 系统 id -> 系统信息，使用 LinkedHashMap 保持配置文件中的顺序。
     */
    @Getter
    private static Map<String, RailwaySystemInfo> systems = new LinkedHashMap<>();

    private static FileConfiguration config;

    /**
     * 加载 railway_system.yml，解析所有铁路系统配置。插件启动与 reload 时调用。
     *
     * @param plugin 插件实例
     */
    public static void load(BiliCraftTicketSystem plugin) {
        config = new FileConfiguration(plugin, EnumConfig.RAILWAY_SYSTEM_CONFIG.getFileName());
        config.load();

        Map<String, RailwaySystemInfo> parsed = new LinkedHashMap<>();
        for (ConfigurationNode node : config.getNodes()) {
            RailwaySystemInfo info = parseNode(node);
            parsed.put(info.getId(), info);
        }
        systems = parsed;
    }

    /**
     * 把一个配置节点解析为 {@link RailwaySystemInfo}。
     *
     * @param node 系统配置节点（节点名即系统 id）
     * @return 解析出的系统信息
     */
    private static RailwaySystemInfo parseNode(ConfigurationNode node) {
        String id = node.getName();
        String name = node.get("name", id);
        List<String> rawMembers = node.getList("members", String.class, null);
        java.util.Set<UUID> members = new java.util.LinkedHashSet<>();
        if (rawMembers != null) {
            for (String raw : rawMembers) {
                UUID uuid = parseUuid(raw);
                if (uuid != null) {
                    members.add(uuid);
                } else {
                    BiliCraftTicketSystem.plugin.getLogger().warning(
                            "铁路系统 [%s] 的成员 \"%s\" 不是合法 UUID，已忽略".formatted(id, raw));
                }
            }
        }
        return new RailwaySystemInfo(id, name, members);
    }

    /**
     * 解析 UUID 字符串，非法返回 null。
     *
     * @param raw UUID 字符串
     * @return UUID，非法返回 null
     */
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 根据系统 id 获取系统信息。
     *
     * @param systemId 系统 id
     * @return 系统信息，不存在时返回 null
     */
    public static RailwaySystemInfo get(String systemId) {
        return systemId == null ? null : systems.get(systemId);
    }

    /**
     * 判断给定系统 id 是否存在。
     *
     * @param systemId 系统 id
     * @return true 表示存在
     */
    public static boolean contains(String systemId) {
        return systemId != null && systems.containsKey(systemId);
    }

    /**
     * 获取所有系统 id，按配置文件顺序。
     *
     * @return 系统 id 有序列表
     */
    public static List<String> getAllIds() {
        return new ArrayList<>(systems.keySet());
    }

    /**
     * 获取某玩家所属的全部系统 id（按配置文件顺序）。
     *
     * @param playerUuid 玩家 UUID
     * @return 该玩家作为成员的系统 id 列表
     */
    public static List<String> getSystemsOfMember(UUID playerUuid) {
        List<String> result = new ArrayList<>();
        for (RailwaySystemInfo info : systems.values()) {
            if (info.isMember(playerUuid)) {
                result.add(info.getId());
            }
        }
        return result;
    }

    /**
     * 新建或更新一个铁路系统，并写回 railway_system.yml（保留注释）。
     * <p>
     * 仅写文件，不刷新内存缓存——调用方应在写回后触发配置重载
     * （{@link BiliCraftTicketSystem#loadConfig}）使其生效。
     *
     * @param systemId 系统 id
     * @param name     系统显示名称
     * @param members  成员 UUID 集合
     */
    public static void upsert(String systemId, String name, java.util.Set<UUID> members) {
        ConfigurationNode node = config.getNode(systemId);
        node.set("name", name);
        List<String> memberStrings = new ArrayList<>();
        if (members != null) {
            for (UUID uuid : members) {
                memberStrings.add(uuid.toString());
            }
        }
        node.set("members", memberStrings);
        config.save();
    }
}

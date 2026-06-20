package com.bigbrother.bilicraftticketsystem.config.system;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.EnumConfig;
import lombok.Getter;

import java.util.*;

/**
 * 铁路系统配置（railway_system.yml）的加载器与写回工具。
 * <p>
 * 每个顶层键是一个系统 id，解析为 {@link RailwaySystemInfo}。提供供编辑向导使用的
 * {@link #upsert} 写回工具（保留 yaml 注释）。插件启动 / reload 时调用 {@link #load}。
 */
public class RailwaySystemConfig {
    public static final String CONTACT_ID = "contact";

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
        // 每公里价格选填：未配置则为 null，计费时回退到全局 price-per-km
        Double pricePerKm = node.contains("price-per-km") ? node.get("price-per-km", Double.class, null) : null;
        UUID creator = parseUuid(node.get("creator", String.class, null));
        double income = node.get("income", 0.0);
        double withdrawn = node.get("withdrawn", 0.0);
        List<String> rawMembers = node.getList("members", String.class, null);
        Set<UUID> members = new LinkedHashSet<>();
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
        return new RailwaySystemInfo(id, name, members, pricePerKm, creator, income, withdrawn);
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
     * 获取某玩家创建的全部系统 id（按配置文件顺序）。
     *
     * @param playerUuid 玩家 UUID
     * @return 该玩家作为创建者的系统 id 列表
     */
    public static List<String> getSystemsCreatedBy(UUID playerUuid) {
        List<String> result = new ArrayList<>();
        for (RailwaySystemInfo info : systems.values()) {
            if (info.isCreator(playerUuid)) {
                result.add(info.getId());
            }
        }
        return result;
    }

    /**
     * 按系统分摊累加收入并实时写回 railway_system.yml（防止资金流失）。同步更新内存缓存中的 income。
     * <p>
     * 每次购票 / 刷卡后调用，{@code perSystem} 为本次行程各铁路系统应得金额（可含 0 值）。
     * 方法同步，串行化文件写入，避免并发购票时写坏 yaml。系统不存在的条目忽略。
     *
     * @param perSystem 系统 id -> 本次新增收入
     */
    public static synchronized void addIncome(Map<String, Double> perSystem) {
        if (perSystem == null || perSystem.isEmpty() || config == null) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<String, Double> entry : perSystem.entrySet()) {
            String systemId = entry.getKey();
            if (systemId == null || !config.contains(systemId)) {
                continue;
            }
            ConfigurationNode node = config.getNode(systemId);
            double updated = node.get("income", 0.0) + entry.getValue();
            node.set("income", updated);
            // 同步内存缓存，使创建者上线发放读到最新值（load 之外的唯一写点）
            RailwaySystemInfo info = systems.get(systemId);
            if (info != null) {
                info.setIncome(updated);
            }
            changed = true;
        }
        if (changed) {
            config.save();
        }
    }

    /**
     * 结算某系统对创建者「未发放」的收入：返回 {@code income - withdrawn} 并把 withdrawn 对齐到 income，
     * 实时写回文件与内存缓存。返回 0（含负数归零）表示无可发放金额。
     *
     * @param systemId 系统 id
     * @return 本次应发放给创建者的金额（>= 0）
     */
    public static synchronized double collectIncome(String systemId) {
        if (systemId == null || config == null || !config.contains(systemId)) {
            return 0.0;
        }
        ConfigurationNode node = config.getNode(systemId);
        double income = node.get("income", 0.0);
        double withdrawn = node.get("withdrawn", 0.0);
        double payout = income - withdrawn;
        if (payout <= 0) {
            return 0.0;
        }
        node.set("withdrawn", income);
        RailwaySystemInfo info = systems.get(systemId);
        if (info != null) {
            info.setWithdrawn(income);
        }
        config.save();
        return payout;
    }

    /**
     * 新建或更新一个铁路系统，并写回 railway_system.yml（保留注释）。
     * <p>
     * 仅写文件，不刷新内存缓存——调用方应在写回后触发配置重载
     * （{@link BiliCraftTicketSystem#loadConfig}）使其生效。
     *
     * @param systemId   系统 id
     * @param name       系统显示名称
     * @param members    成员 UUID 集合
     * @param pricePerKm 每公里价格；null 表示不单独配置（移除该项，沿用全局默认）
     * @param creator    创建者 UUID；仅新建时写入。传 null 表示保留文件中已有的 creator（修改流程不改创建者）
     */
    public static void upsert(String systemId, String name, Set<UUID> members, Double pricePerKm, UUID creator) {
        ConfigurationNode node = config.getNode(systemId);
        node.set("name", name);
        if (pricePerKm == null) {
            node.remove("price-per-km");
        } else {
            node.set("price-per-km", pricePerKm);
        }
        // 创建者只在新建（文件中尚无 creator）时写入；修改流程 creator 传 null，保留原值不动
        if (creator != null && !node.contains("creator")) {
            node.set("creator", creator.toString());
        }
        // income / withdrawn 由购票 / 发放流程维护，向导写回时不触碰已有值；首次创建初始化为 0
        if (!node.contains("income")) {
            node.set("income", 0.0);
        }
        if (!node.contains("withdrawn")) {
            node.set("withdrawn", 0.0);
        }
        List<String> memberStrings = new ArrayList<>();
        if (members != null) {
            for (UUID uuid : members) {
                memberStrings.add(uuid.toString());
            }
        }
        node.set("members", memberStrings);
        config.save();
    }

    /**
     * 删除一个铁路系统，并写回 railway_system.yml（保留其余注释）。
     * <p>
     * 仅删除系统本身，不级联删除其下线路——调用方（如 delSystem 指令）应先删除所属线路。
     * 仅写文件，不刷新内存缓存——调用方应在写回后触发配置重载
     * （{@link BiliCraftTicketSystem#loadConfig} 或 {@link #load}）使其生效。
     *
     * @param systemId 系统 id
     * @return true 表示该系统存在并已删除；false 表示系统不存在（未改动文件）
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean delete(String systemId) {
        if (systemId == null || !config.contains(systemId)) {
            return false;
        }
        config.remove(systemId);
        config.save();
        return true;
    }
}

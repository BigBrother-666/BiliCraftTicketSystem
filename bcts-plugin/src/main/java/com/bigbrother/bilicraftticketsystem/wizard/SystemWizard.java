package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 铁路系统编辑向导（{@code /ticket editSystem <systemID>}）。
 * <p>
 * 步骤（最小配置，便于以后扩展）：
 * <ol>
 *   <li>{@code name}：系统显示名称（必填）。</li>
 *   <li>{@code members}：成员，输入玩家名（仅在线可用）或 UUID，逗号分隔（选填）。</li>
 * </ol>
 * 新建模式下创建者自动加入成员。完成后写回 railway_system.yml 并自动重载配置。
 */
public class SystemWizard extends ConfigWizard {
    private final String systemId;

    /**
     * @param player   发起玩家
     * @param systemId 系统 id
     * @param editMode 是否修改模式
     */
    public SystemWizard(Player player, String systemId, boolean editMode) {
        super(player, editMode);
        this.systemId = systemId;
        if (editMode) {
            RailwaySystemInfo info = RailwaySystemConfig.get(systemId);
            if (info != null) {
                values.put("name", info.getName());
                values.put("members", new LinkedHashSet<>(info.getMembersView()));
            }
        }
    }

    @Override
    protected Component title() {
        return Component.text((editMode ? "修改" : "新建") + "铁路系统 " + systemId, NamedTextColor.GOLD);
    }

    @Override
    protected List<WizardStep> steps() {
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new WizardStep("name",
                Component.text("输入铁路系统名称", NamedTextColor.WHITE),
                true,
                input -> input.isBlank()
                        ? WizardStep.Result.error("名称不能为空")
                        : WizardStep.Result.ok(input)));
        steps.add(new WizardStep("members",
                Component.text("输入成员（在线的玩家名 或 36位UUID，多个用英文逗号 , 分隔；）",
                        NamedTextColor.WHITE),
                false,
                this::parseMembers));
        return steps;
    }

    @Override
    protected String currentValueDisplay(String key) {
        if ("members".equals(key)) {
            Object v = values.get(key);
            if (v instanceof Set<?> set) {
                List<String> names = new ArrayList<>();
                for (Object o : set) {
                    names.add(uuidToDisplay((UUID) o));
                }
                return names.isEmpty() ? "（无）" : String.join(", ", names);
            }
            return "（无）";
        }
        return super.currentValueDisplay(key);
    }

    /**
     * 解析成员输入：逗号分隔，每项为玩家名（仅在线）或 UUID；任一项非法则整条重输。
     */
    private WizardStep.Result parseMembers(String input) {
        Set<UUID> result = new LinkedHashSet<>();
        for (String token : input.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            UUID uuid = resolveUuid(t);
            if (uuid == null) {
                return WizardStep.Result.error("无法识别成员 \"" + t + "\"：不是合法 UUID，也不是在线玩家名");
            }
            result.add(uuid);
        }
        return WizardStep.Result.ok(result);
    }

    /**
     * 把一个 token 解析为 UUID：先按 UUID 解析，失败再按在线玩家名解析。
     *
     * @param token 输入项
     * @return UUID，无法解析返回 null
     */
    private UUID resolveUuid(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，按在线玩家名
        }
        Player online = Bukkit.getPlayerExact(token);
        return online == null ? null : online.getUniqueId();
    }

    private String uuidToDisplay(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name + "(" + uuid + ")";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onComplete(Map<String, Object> collected) {
        String name = (String) collected.getOrDefault("name", systemId);
        Set<UUID> members = collected.get("members") instanceof Set<?>
                ? new LinkedHashSet<>((Set<UUID>) collected.get("members"))
                : new LinkedHashSet<>();
        // 新建模式：创建者自动入列
        if (!editMode) {
            members.add(player.getUniqueId());
        }

        RailwaySystemConfig.upsert(systemId, name, members);
        player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                "已保存铁路系统 [%s]，正在重载配置...".formatted(systemId), NamedTextColor.GREEN)));
        try {
            RailwaySystemConfig.load(BiliCraftTicketSystem.plugin);
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载完成", NamedTextColor.GREEN)));
        } catch (Exception e) {
            player.sendMessage(BiliCraftTicketSystem.PREFIX.append(Component.text(
                    "配置重载时发生错误：" + e.getMessage(), NamedTextColor.RED)));
        }
    }
}

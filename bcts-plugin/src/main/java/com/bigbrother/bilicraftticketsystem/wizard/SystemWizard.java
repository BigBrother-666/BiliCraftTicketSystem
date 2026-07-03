package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 铁路系统编辑向导（{@code /ticketconfig editSystem <systemID>}）。
 * <p>
 * 步骤（最小配置，便于以后扩展）：
 * <ol>
 *   <li>{@code name}：系统显示名称（必填）。</li>
 *   <li>{@code members}：成员——<b>仅创建者可见此步骤</b>。输入玩家名（仅在线可用）或 UUID
 *       （逗号分隔）可<b>追加</b>成员；提示下方列出现有成员，每个成员带 [删除] 按钮，创建者本人不可删。</li>
 *   <li>{@code price-per-km}：每公里价格（选填）；必须落在 config.yml 的 price-per-km-range 内；
 *       跳过则沿用 config.yml 的全局 price-per-km。</li>
 * </ol>
 * 新建模式下创建者自动加入成员。完成后写回 railway_system.yml 并自动重载配置。
 */
public class SystemWizard extends ConfigWizard {
    /**
     * logo 图片下载步骤的超时时间（毫秒）。包含 HEAD 取大小 + GET 下载，
     * 略大于 {@link ImageUtils} 内部连接/读取超时（5s+5s）之和，留出处理余量。
     */
    private static final long DOWNLOAD_TIMEOUT_MILLIS = 15000L;

    private static final String MEMBERS_KEY = "members";

    private final String systemId;
    /**
     * 发起玩家是否为该系统创建者：新建模式下即本人；修改模式下按现有配置的 creator 判断。
     * 仅创建者可增删成员，故 members 步骤只对创建者展示。
     */
    private final boolean isCreator;

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
                values.put(MEMBERS_KEY, new LinkedHashSet<>(info.getMembersView()));
                if (info.getPricePerKm() != null) {
                    values.put("price-per-km", info.getPricePerKm());
                }
                this.isCreator = info.isCreator(player.getUniqueId());
            } else {
                this.isCreator = false;
            }
        } else {
            // 新建模式：发起玩家即创建者
            this.isCreator = true;
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
        // 成员增删仅创建者可用：非创建者不展示此步骤（仍可改名称 / 价格 / logo）
        if (isCreator) {
            steps.add(new WizardStep(MEMBERS_KEY,
                    Component.text("输入要新增的成员（在线的玩家名 或 36位UUID，多个用英文逗号 , 分隔）；" +
                            "已有成员可点下方 [删除] 移除。留空或点 [跳过] 保持不变",
                            NamedTextColor.WHITE),
                    false,
                    this::parseMembers));
        }
        steps.add(new WizardStep("price-per-km",
                Component.text("输入本系统每公里价格（数字，范围 %.2f ~ %.2f）；跳过则使用全局默认价格（%.2f/km）"
                        .formatted(MainConfig.pricePerKmMin, MainConfig.pricePerKmMax, MainConfig.pricePerKm), NamedTextColor.WHITE),
                false,
                this::parsePricePerKm));
        steps.add(new WizardStep("web-logo-path",
                Component.text("输入本系统的logo图片直链，该图片会在网页端显示，图片分辨率会统一设置为%s*%s"
                        .formatted(MapConfig.getWebLogoDim(), MapConfig.getWebLogoDim()), NamedTextColor.WHITE),
                false,
                this::parseWebImageUrl,
                DOWNLOAD_TIMEOUT_MILLIS));
        steps.add(new WizardStep("mc-logo-path",
                Component.text("输入本系统的logo图片直链，该图片作为系统图标在车票系统内显示，图片分辨率会统一设置为%d*%d，不填则使用网页端logo"
                        .formatted(MapConfig.getMcLogoDim(), MapConfig.getMcLogoDim()), NamedTextColor.WHITE),
                false,
                this::parseMcImageUrl,
                DOWNLOAD_TIMEOUT_MILLIS));
        return steps;
    }

    private WizardStep.Result parseWebImageUrl(String imageUrl) {
        return downloadImage(imageUrl, true);
    }

    private WizardStep.Result parseMcImageUrl(String imageUrl) {
        return downloadImage(imageUrl, false);
    }

    private WizardStep.Result downloadImage(String imageUrl, boolean isWeb) {
        try {
            // 获取图片大小
            long contentLength = ImageUtils.getImageSize(imageUrl);
            if (contentLength == -1) {
                return WizardStep.Result.error("无法获取图片大小！");
            }

            // 检查图片大小（<= 5MB）
            if (contentLength > 3000 * 1024) {
                return WizardStep.Result.error("图片大小不能超过3MB，当前大小：" + (contentLength / 1024 / 1024) + " MB");
            }

            // 接收文件
            byte[] imageBytes = ImageUtils.getImageBytes(imageUrl);

            // 保存图片
            // 保存web logo
            if (isWeb) {
                byte[] webImageBytes;
                try {
                    webImageBytes = ImageUtils.convertTonxn(imageBytes, MapConfig.getWebLogoDim());
                    if (webImageBytes == null) {
                        return WizardStep.Result.error("图片尺寸转化失败");
                    }
                } catch (IOException e) {
                    return WizardStep.Result.error("图片格式不支持或图片损坏！错误信息：" + e.getMessage());
                }

                File imageWeb = ImageUtils.getSystemImageFileWeb(systemId);
                if (!imageWeb.getParentFile().exists()) {
                    imageWeb.getParentFile().mkdirs();
                }
                Files.write(imageWeb.toPath(), webImageBytes);
            }

            if (!isWeb || !ImageUtils.getSystemImageFileMc(systemId).exists()) {
                // 保存游戏内logo
                byte[] mcImageBytes;
                try {
                    mcImageBytes = ImageUtils.convertTonxn(imageBytes, MapConfig.getMcLogoDim());
                    if (mcImageBytes == null) {
                        return WizardStep.Result.error("图片尺寸转化失败");
                    }
                } catch (IOException e) {
                    return WizardStep.Result.error("图片格式不支持或图片损坏！错误信息：" + e.getMessage());
                }
                File imageMc = ImageUtils.getSystemImageFileMc(systemId);
                if (!imageMc.getParentFile().exists()) {
                    imageMc.getParentFile().mkdirs();
                }
                Files.write(imageMc.toPath(), mcImageBytes);
            }

            // 下载 + 处理成功，提示玩家（player.sendMessage 线程安全，可在异步线程调用）
            player.sendMessage(MainConfig.prefix.append(CommonUtils.mmStr2Component(
                    MainConfig.message.get("wizard-logo-download-success", "<green>logo 图片处理完成"))));
            return WizardStep.Result.ok(null);
        } catch (Exception e) {
            return WizardStep.Result.error("上传或处理图片时发生错误：" + e.getMessage());
        }
    }

    /**
     * 解析每公里价格：必须为数字，且落在 config.yml 配置的 price-per-km-range 区间内（含边界）。
     */
    private WizardStep.Result parsePricePerKm(String input) {
        try {
            double value = Double.parseDouble(input.trim());
            if (value < MainConfig.pricePerKmMin || value > MainConfig.pricePerKmMax) {
                return WizardStep.Result.error("每公里价格必须在 %.2f ~ %.2f 之间"
                        .formatted(MainConfig.pricePerKmMin, MainConfig.pricePerKmMax));
            }
            return WizardStep.Result.ok(value);
        } catch (NumberFormatException e) {
            return WizardStep.Result.error("\"" + input + "\" 不是合法数字");
        }
    }

    @Override
    protected String currentValueDisplay(String key) {
        if (MEMBERS_KEY.equals(key)) {
            Object v = values.get(key);
            if (v instanceof Set<?> set) {
                List<String> names = new ArrayList<>();
                for (Object o : set) {
                    names.add(uuidToDisplay((UUID) o));
                }
                return names.isEmpty() ? "（无）" : String.join(", ", names);
            }
            return "（无）";
        } else if (key.equals("web-logo-path")) {
            // 图片不显示路径
            return ImageUtils.getSystemImageFileWeb(systemId).exists() ? "已上传" : "未上传";
        } else if (key.equals("mc-logo-path")) {
            return ImageUtils.getSystemImageFileMc(systemId).exists() ? "已上传" : "未上传";
        }
        return super.currentValueDisplay(key);
    }

    /**
     * 成员步骤提示发送后，额外列出当前成员并在每个成员后附 [删除] 按钮（创建者本人不可删）。
     * 其余步骤无附加内容。
     */
    @Override
    protected void onStepShown(String key) {
        if (!MEMBERS_KEY.equals(key)) {
            return;
        }
        Set<UUID> members = currentMembers();
        if (members.isEmpty()) {
            player.sendMessage(Component.text("当前成员：（无）", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("当前成员：", NamedTextColor.GRAY));
        for (UUID uuid : members) {
            Component line = Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(uuidToDisplay(uuid), NamedTextColor.WHITE));
            // 创建者本人不可删除，避免系统失去创建者成员身份
            if (!uuid.equals(player.getUniqueId())) {
                line = line.append(Component.text("  [删除]", NamedTextColor.RED)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.callback(a -> removeMember(uuid))));
            } else {
                line = line.append(Component.text("  (创建者)", NamedTextColor.DARK_GRAY));
            }
            player.sendMessage(line);
        }
    }

    /**
     * 取当前已收集的成员集合（可变引用，若不存在则新建并放回 values）。
     *
     * @return 成员 UUID 集合
     */
    @SuppressWarnings("unchecked")
    private Set<UUID> currentMembers() {
        Object v = values.get(MEMBERS_KEY);
        if (v instanceof Set<?>) {
            return (Set<UUID>) v;
        }
        Set<UUID> fresh = new LinkedHashSet<>();
        values.put(MEMBERS_KEY, fresh);
        return fresh;
    }

    /**
     * [删除] 按钮回调：从当前成员集合移除指定 UUID，并重新展示成员步骤提示。
     * 仅当玩家仍停留在成员步骤时生效。
     *
     * @param uuid 要移除的成员 UUID
     */
    private void removeMember(UUID uuid) {
        if (!WizardManager.isActive(player.getUniqueId()) || WizardManager.get(player.getUniqueId()) != this) {
            return;
        }
        if (!MEMBERS_KEY.equals(currentStepKey())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("已离开成员编辑步骤，删除无效。", NamedTextColor.RED)));
            return;
        }
        if (uuid.equals(player.getUniqueId())) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("不能删除创建者本人。", NamedTextColor.RED)));
            return;
        }
        if (currentMembers().remove(uuid)) {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("已移除成员 " + uuidToDisplay(uuid), NamedTextColor.YELLOW)));
        } else {
            player.sendMessage(MainConfig.prefix.append(
                    Component.text("该成员已不在列表中。", NamedTextColor.GRAY)));
        }
        // 重新展示当前成员（含更新后的删除按钮）
        onStepShown(MEMBERS_KEY);
    }

    /**
     * 解析成员输入：逗号分隔，每项为玩家名（仅在线）或 UUID；任一项非法则整条重输。
     * 解析出的成员<b>追加</b>到现有成员集合（配合下方 [删除] 按钮增删）。
     */
    private WizardStep.Result parseMembers(String input) {
        Set<UUID> parsed = new LinkedHashSet<>();
        for (String token : input.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            UUID uuid = resolveUuid(t);
            if (uuid == null) {
                return WizardStep.Result.error("无法识别成员 \"" + t + "\"：不是合法 UUID，也不是在线玩家名");
            }
            parsed.add(uuid);
        }
        // 追加到现有成员集合，而非替换（删除靠 [删除] 按钮）
        Set<UUID> merged = new LinkedHashSet<>(currentMembers());
        merged.addAll(parsed);
        return WizardStep.Result.ok(merged);
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
        Set<UUID> members = collected.get(MEMBERS_KEY) instanceof Set<?>
                ? new LinkedHashSet<>((Set<UUID>) collected.get(MEMBERS_KEY))
                : new LinkedHashSet<>();
        // 新建模式：创建者自动入列
        if (!editMode) {
            members.add(player.getUniqueId());
        }

        // 跳过 / 未填则为 null，计费时回退到全局 price-per-km
        Double pricePerKm = collected.get("price-per-km") instanceof Number n ? n.doubleValue() : null;

        // 新建模式写入创建者；修改模式传 null，upsert 保留原创建者不动
        UUID creator = editMode ? null : player.getUniqueId();
        RailwaySystemConfig.upsert(systemId, name, members, pricePerKm, creator);
        player.sendMessage(MainConfig.prefix.append(Component.text(
                "已保存铁路系统 [%s]，正在重载配置...".formatted(systemId), NamedTextColor.GREEN)));
        try {
            RailwaySystemConfig.load(BiliCraftTicketSystem.plugin);
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "配置重载完成", NamedTextColor.GREEN)));
        } catch (Exception e) {
            player.sendMessage(MainConfig.prefix.append(Component.text(
                    "配置重载时发生错误：" + e.getMessage(), NamedTextColor.RED)));
        }
    }
}

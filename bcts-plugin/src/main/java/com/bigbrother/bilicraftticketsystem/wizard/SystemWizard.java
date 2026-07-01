package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemInfo;
import com.bigbrother.bilicraftticketsystem.utils.CommonUtils;
import com.bigbrother.bilicraftticketsystem.utils.ImageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 *   <li>{@code members}：成员，输入玩家名（仅在线可用）或 UUID，逗号分隔（选填）。</li>
 *   <li>{@code price-per-km}：每公里价格（选填）；跳过则沿用 config.yml 的全局 price-per-km。</li>
 * </ol>
 * 新建模式下创建者自动加入成员。完成后写回 railway_system.yml 并自动重载配置。
 */
public class SystemWizard extends ConfigWizard {
    /**
     * logo 图片下载步骤的超时时间（毫秒）。包含 HEAD 取大小 + GET 下载，
     * 略大于 {@link ImageUtils} 内部连接/读取超时（5s+5s）之和，留出处理余量。
     */
    private static final long DOWNLOAD_TIMEOUT_MILLIS = 15000L;

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
                if (info.getPricePerKm() != null) {
                    values.put("price-per-km", info.getPricePerKm());
                }
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
        steps.add(new WizardStep("price-per-km",
                Component.text("输入本系统每公里价格（数字）；跳过则使用全局默认价格（%.2f/km）".formatted(MainConfig.pricePerKm), NamedTextColor.WHITE),
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
     * 解析每公里价格：必须为非负数字。
     */
    private WizardStep.Result parsePricePerKm(String input) {
        try {
            double value = Double.parseDouble(input.trim());
            if (value < 0) {
                return WizardStep.Result.error("每公里价格不能为负数");
            }
            return WizardStep.Result.ok(value);
        } catch (NumberFormatException e) {
            return WizardStep.Result.error("\"" + input + "\" 不是合法数字");
        }
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
        } else if (key.equals("web-logo-path")) {
            // 图片不显示路径
            return ImageUtils.getSystemImageFileWeb(systemId).exists() ? "已上传" : "未上传";
        } else if (key.equals("mc-logo-path")) {
            return ImageUtils.getSystemImageFileMc(systemId).exists() ? "已上传" : "未上传";
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

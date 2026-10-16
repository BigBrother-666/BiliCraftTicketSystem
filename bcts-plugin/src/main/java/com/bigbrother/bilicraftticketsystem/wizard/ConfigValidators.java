package com.bigbrother.bilicraftticketsystem.wizard;

import com.bigbrother.bilicraftticketsystem.config.MainConfig;
import com.bigbrother.bilicraftticketsystem.config.system.RailwaySystemConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 铁路系统 / 线路配置的共享校验规则，游戏内向导（{@link SystemWizard} / {@link RouteWizard}）与
 * 网页端配置请求（{@code web.inbound.ConfigRequestHandler}）共用，保证两端规则一致。
 * <p>
 * 均为纯函数（只读当前配置缓存），返回 {@link WizardStep.Result}：成功带解析后的值，失败带中文提示。
 */
public final class ConfigValidators {
    private ConfigValidators() {
    }

    /**
     * 线路车站列表在向导输入中的分隔符。
     */
    public static final String STATION_SEPARATOR = "->";
    /**
     * 进 / 出站提示在向导输入中的分隔符。
     */
    public static final String NOTICE_SEPARATOR = "||";

    // ===== 铁路系统 =====

    /**
     * 校验铁路系统名称：非空白。
     *
     * @param input 名称
     * @return 成功值为原样名称
     */
    public static WizardStep.Result systemName(String input) {
        return input == null || input.isBlank()
                ? WizardStep.Result.error("名称不能为空")
                : WizardStep.Result.ok(input);
    }

    /**
     * 解析每公里价格文本并校验范围。
     *
     * @param input 玩家输入
     * @return 成功值为 Double
     */
    public static WizardStep.Result parsePricePerKm(String input) {
        try {
            return pricePerKm(Double.parseDouble(input.trim()));
        } catch (NumberFormatException e) {
            return WizardStep.Result.error("\"" + input + "\" 不是合法数字");
        }
    }

    /**
     * 校验每公里价格落在 config.yml 的 price-per-km-range 区间内（含边界）。
     *
     * @param value 价格
     * @return 成功值为 Double
     */
    public static WizardStep.Result pricePerKm(double value) {
        if (Double.isNaN(value) || value < MainConfig.pricePerKmMin || value > MainConfig.pricePerKmMax) {
            return WizardStep.Result.error("每公里价格必须在 %.2f ~ %.2f 之间"
                    .formatted(MainConfig.pricePerKmMin, MainConfig.pricePerKmMax));
        }
        return WizardStep.Result.ok(value);
    }

    // ===== 线路 =====

    /**
     * 校验所属铁路系统存在（成员关系由调用方另行校验）。
     *
     * @param input 系统 id
     * @return 成功值为 trim 后的系统 id
     */
    public static WizardStep.Result existingSystem(String input) {
        String id = input == null ? "" : input.trim();
        if (id.isEmpty()) {
            return WizardStep.Result.error("铁路系统 id 不能为空");
        }
        if (!RailwaySystemConfig.contains(id)) {
            return WizardStep.Result.error("铁路系统 [" + id + "] 不存在，请先用 /ticketconfig editSystem 创建");
        }
        return WizardStep.Result.ok(id);
    }

    /**
     * 校验线路名称：非空白。
     */
    public static WizardStep.Result lineName(String input) {
        return input == null || input.isBlank()
                ? WizardStep.Result.error("线路名称不能为空")
                : WizardStep.Result.ok(input);
    }

    /**
     * 校验线路标志色 #RRGGBB。
     */
    public static WizardStep.Result hexColor(String input) {
        String s = input == null ? "" : input.trim();
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            return WizardStep.Result.error("颜色格式应为 #RRGGBB，如 #AA0000");
        }
        return WizardStep.Result.ok(s);
    }

    /**
     * 解析向导输入的车站列表（{@code ->} 分隔）。
     */
    public static WizardStep.Result parseStations(String input) {
        return stations(List.of(input.split(STATION_SEPARATOR)));
    }

    /**
     * 校验车站列表（railway_routes.yml {@code bossbar-stations} 的原样条目，可含 {@code :RV} 后缀 /
     * 末项转线目标）：去除首尾空白与空项后至少 2 站，且条目不能包含分隔符 {@code ->}。
     *
     * @param items 车站条目
     * @return 成功值为 {@code List<String>}
     */
    public static WizardStep.Result stations(List<String> items) {
        List<String> stations = new ArrayList<>();
        if (items != null) {
            for (String part : items) {
                if (part == null) {
                    continue;
                }
                String s = part.trim();
                if (s.contains(STATION_SEPARATOR)) {
                    return WizardStep.Result.error("车站名不能包含 " + STATION_SEPARATOR + "：" + s);
                }
                if (!s.isEmpty()) {
                    stations.add(s);
                }
            }
        }
        if (stations.size() < 2) {
            return WizardStep.Result.error("车站列表至少需要 2 站，用 -> 分隔");
        }
        return WizardStep.Result.ok(stations);
    }

    /**
     * 校验 bossbar 颜色名（忽略大小写）。
     *
     * @return 成功值为大写颜色名
     */
    public static WizardStep.Result bossbarColor(String input) {
        String s = input == null ? "" : input.trim().toUpperCase();
        try {
            net.kyori.adventure.bossbar.BossBar.Color.valueOf(s);
            return WizardStep.Result.ok(s);
        } catch (IllegalArgumentException e) {
            return WizardStep.Result.error("无效的 bossbar 颜色：" + input
                    + "（可选 PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE）");
        }
    }

    /**
     * 解析进 / 出站提示（{@code ||} 分隔），逐条校验格式。空串得到空列表。
     *
     * @return 成功值为 {@code List<String>}
     */
    public static WizardStep.Result noticeList(String input) {
        List<String> list = new ArrayList<>();
        if (input == null) {
            return WizardStep.Result.ok(list);
        }
        for (String part : input.split("\\|\\|")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            WizardStep.Result err = validateNotice(s);
            if (err != null) {
                return err;
            }
            list.add(s);
        }
        return WizardStep.Result.ok(list);
    }

    /**
     * 把提示列表还原为向导输入格式（{@code ||} 连接），与 {@link #noticeList} 互逆。
     *
     * @param notices 提示列表（可空）
     * @return 连接后的字符串，无提示返回空串
     */
    public static String joinNotices(List<String> notices) {
        return notices == null ? "" : String.join(NOTICE_SEPARATOR, notices);
    }

    /**
     * 校验单条提示格式，须为 {@code sound:<音效名>,<音调>,<音量>} 或 {@code announce:<文字>}。
     *
     * @param notice 单条提示（已去除首尾空白）
     * @return 校验失败时返回错误 Result，通过时返回 null
     */
    private static WizardStep.Result validateNotice(String notice) {
        int idx = notice.indexOf(':');
        if (idx < 0) {
            return WizardStep.Result.error("无效的提示：" + notice
                    + "（格式应为 sound:<音效名>,<音调>,<音量> 或 announce:<文字>）");
        }
        String type = notice.substring(0, idx).trim().toLowerCase();
        String body = notice.substring(idx + 1);
        switch (type) {
            case "announce" -> {
                if (body.isBlank()) {
                    return WizardStep.Result.error("announce 提示的文字不能为空：" + notice);
                }
            }
            case "sound" -> {
                String[] parts = body.split(",");
                if (parts.length == 0 || parts[0].trim().isEmpty()) {
                    return WizardStep.Result.error("sound 提示缺少音效名：" + notice
                            + "（格式应为 sound:<音效名>,<音调>,<音量>）");
                }
                if (parts.length > 1 && !isNumber(parts[1])) {
                    return WizardStep.Result.error("sound 的音调必须是数字：" + notice);
                }
                if (parts.length > 2 && !isNumber(parts[2])) {
                    return WizardStep.Result.error("sound 的音量必须是数字：" + notice);
                }
            }
            default -> {
                return WizardStep.Result.error("未知的提示类型 " + type + "：" + notice
                        + "（应为 sound 或 announce）");
            }
        }
        return null;
    }

    private static boolean isNumber(String s) {
        try {
            Float.parseFloat(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

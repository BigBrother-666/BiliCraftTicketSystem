package com.bigbrother.bilicraftticketsystem.signactions.component;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * platform 站台控制牌的可选功能。
 * <p>
 * 控制牌第四行列出**不使用**的功能（反选模型）：留空表示全部启用，写上缩写表示禁用对应功能。
 * 例如第四行为 {@code BB DN} 表示禁用 bossbar 和出站提示，仍启用空车销毁与进站提示。
 */
@Getter
public enum PlatformFeature {
    /**
     * 显示 bossbar（进度代表列车行程）。
     */
    BOSSBAR("BB"),
    /**
     * 空列车离开时自动销毁。
     */
    DESTROY("DY"),
    /**
     * 进站提示（消息/音效）。
     */
    ARRIVAL_NOTICE("AN"),
    /**
     * 出站提示（消息/音效）。
     */
    DEPARTURE_NOTICE("DN");

    /**
     * 控制牌上使用的缩写。
     */
    private final String code;

    PlatformFeature(String code) {
        this.code = code;
    }

    /**
     * 解析 platform 控制牌第四行，返回**启用**的功能集合（反选：未列出的即启用）。
     *
     * @param disabledLine 第四行内容（空格分隔的禁用功能缩写），可为 null
     * @return 启用的功能集合
     */
    public static Set<PlatformFeature> parseEnabled(String disabledLine) {
        Set<PlatformFeature> enabled = EnumSet.allOf(PlatformFeature.class);
        if (disabledLine == null || disabledLine.trim().isEmpty()) {
            return enabled;
        }
        for (String token : disabledLine.trim().split("\\s+")) {
            PlatformFeature f = fromCode(token);
            if (f != null) {
                enabled.remove(f);
            }
        }
        return enabled;
    }

    /**
     * 根据缩写查找功能。
     *
     * @param code 缩写（大小写不敏感）
     * @return 对应功能，无匹配返回 null
     */
    public static PlatformFeature fromCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(f -> f.code.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }
}
